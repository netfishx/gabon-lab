package lab.gabon;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.Filter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

/**
 * Integration tests for 4 required concurrency scenarios. Uses Testcontainers for PostgreSQL and
 * Redis, runs the full Spring Boot context.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "spring.flyway.enabled=false"
    })
@Testcontainers
class ConcurrencyIT {

  @TestConfiguration
  static class TestConfig {

    /** Disable all rate limit filters — rate limiting would interfere with concurrency testing. */
    @Bean
    FilterRegistrationBean<Filter> authRateLimit() {
      return disabledFilter();
    }

    @Bean
    FilterRegistrationBean<Filter> publicRateLimit() {
      return disabledFilter();
    }

    @Bean
    FilterRegistrationBean<Filter> apiRateLimit() {
      return disabledFilter();
    }

    @Bean
    FilterRegistrationBean<Filter> adminRateLimit() {
      return disabledFilter();
    }

    private static FilterRegistrationBean<Filter> disabledFilter() {
      var reg = new FilterRegistrationBean<Filter>();
      reg.setFilter((request, response, chain) -> chain.doFilter(request, response));
      reg.setEnabled(false);
      return reg;
    }
  }

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> pg =
      new PostgreSQLContainer<>("postgres:18-alpine")
          .withCopyFileToContainer(
              org.testcontainers.utility.MountableFile.forClasspathResource(
                  "db/migration/V001__init.sql"),
              "/docker-entrypoint-initdb.d/init.sql");

  @Container
  @SuppressWarnings("resource")
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:8-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", pg::getJdbcUrl);
    registry.add("spring.datasource.username", pg::getUsername);
    registry.add("spring.datasource.password", pg::getPassword);
    registry.add(
        "spring.data.redis.url",
        () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379));
    // S3 stub mode (blank endpoint)
    registry.add("app.s3.endpoint", () -> "");
    registry.add("app.s3.region", () -> "test");
    registry.add("app.s3.access-key", () -> "test");
    registry.add("app.s3.secret-key", () -> "test");
    registry.add("app.s3.bucket-videos", () -> "test-videos");
    registry.add("app.s3.bucket-avatars", () -> "test-avatars");
  }

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;

  private HttpClient httpClient;
  private String baseUrl;
  private final JsonMapper json = JsonMapper.builder().build();

  private static final AtomicInteger SEQ = new AtomicInteger();

  @BeforeEach
  void setUp() {
    httpClient = HttpClient.newBuilder().build();
    baseUrl = "http://localhost:" + port;
  }

  // -- Helpers ----------------------------------------------------------------

  private String uniqueUsername() {
    return "user_" + SEQ.incrementAndGet() + "_" + System.nanoTime();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parseJson(String body) throws Exception {
    return json.readValue(body, Map.class);
  }

  private String toJson(Object obj) throws Exception {
    return json.writeValueAsString(obj);
  }

  private Map<String, Object> post(String path, Object body) throws Exception {
    var req =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
            .build();
    var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    return parseJson(resp.body());
  }

  private Map<String, Object> postAuth(String path, Object body, String token) throws Exception {
    var builder =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + token);
    if (body != null) {
      builder.POST(HttpRequest.BodyPublishers.ofString(toJson(body)));
    } else {
      builder.POST(HttpRequest.BodyPublishers.noBody());
    }
    var resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    return parseJson(resp.body());
  }

  private Map<String, Object> getAuth(String path, String token) throws Exception {
    var req =
        HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
    var resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
    return parseJson(resp.body());
  }

  @SuppressWarnings("unchecked")
  private String[] registerUser(String username) throws Exception {
    var resp =
        post("/api/v1/auth/register", Map.of("username", username, "password", "password123"));
    assertThat(((Number) resp.get("code")).intValue())
        .as("register %s: %s", username, resp)
        .isEqualTo(0);
    var data = (Map<String, Object>) resp.get("data");
    return new String[] {(String) data.get("accessToken"), (String) data.get("refreshToken")};
  }

  // -- Test 1: Concurrent refresh — only one succeeds -------------------------

  @Test
  void concurrentRefresh_onlyOneSucceeds() throws Exception {
    var tokens = registerUser(uniqueUsername());
    var refreshToken = tokens[1];

    int n = 10;
    var startGate = new CountDownLatch(1);
    var successes = new AtomicInteger();
    var failures = new AtomicInteger();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int i = 0; i < n; i++) {
        executor.submit(
            () -> {
              try {
                startGate.await();
                var resp = post("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken));
                int code = ((Number) resp.get("code")).intValue();
                if (code == 0) {
                  successes.incrementAndGet();
                } else {
                  failures.incrementAndGet();
                }
              } catch (Exception e) {
                failures.incrementAndGet();
              }
            });
      }
      startGate.countDown();
      executor.shutdown();
      executor.awaitTermination(30, TimeUnit.SECONDS);
    }

    assertThat(successes.get()).isEqualTo(1);
    assertThat(failures.get()).isEqualTo(n - 1);
  }

  // -- Test 2: Logout then refresh fails --------------------------------------

  @Test
  void logoutThenRefresh_fails() throws Exception {
    var tokens = registerUser(uniqueUsername());
    var accessToken = tokens[0];
    var refreshToken = tokens[1];

    // Logout
    var logoutResp =
        postAuth("/api/v1/auth/logout", Map.of("refreshToken", refreshToken), accessToken);
    assertThat(((Number) logoutResp.get("code")).intValue()).isEqualTo(0);

    // Refresh should fail
    var refreshResp = post("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken));
    int code = ((Number) refreshResp.get("code")).intValue();
    assertThat(code).isNotEqualTo(0);
  }

  // -- Test 3: Concurrent likes — like_count = N (different users) ------------

  @Test
  void concurrentLikes_countEqualsN() throws Exception {
    int n = 10;

    // Register N users and collect their access tokens
    var accessTokens = new ArrayList<String>(n);
    for (int i = 0; i < n; i++) {
      var tok = registerUser(uniqueUsername());
      accessTokens.add(tok[0]);
    }

    // Insert a video (owned by a separate user) directly via JDBC
    var ownerUsername = uniqueUsername();
    registerUser(ownerUsername);
    long ownerId =
        jdbc.queryForObject(
            "SELECT id FROM customers WHERE LOWER(username) = LOWER(?)", Long.class, ownerUsername);
    long videoId = insertTestVideo(ownerId);

    var startGate = new CountDownLatch(1);
    var successes = new AtomicInteger();
    var failures = new AtomicInteger();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (String token : accessTokens) {
        executor.submit(
            () -> {
              try {
                startGate.await();
                var resp = postAuth("/api/v1/videos/" + videoId + "/like", null, token);
                int code = ((Number) resp.get("code")).intValue();
                if (code == 0) {
                  successes.incrementAndGet();
                } else {
                  failures.incrementAndGet();
                }
              } catch (Exception e) {
                failures.incrementAndGet();
              }
            });
      }
      startGate.countDown();
      executor.shutdown();
      executor.awaitTermination(30, TimeUnit.SECONDS);
    }

    assertThat(successes.get()).isEqualTo(n);
    assertThat(failures.get()).isEqualTo(0);

    // Verify like_count in DB is exactly N
    long likeCount =
        jdbc.queryForObject("SELECT like_count FROM videos WHERE id = ?", Long.class, videoId);
    assertThat(likeCount).isEqualTo(n);
  }

  // -- Test 4: Concurrent task claim — only one succeeds ----------------------

  @SuppressWarnings("unchecked")
  @Test
  void concurrentTaskClaim_onlyOneSucceeds() throws Exception {
    var tokens = registerUser(uniqueUsername());
    var accessToken = tokens[0];

    // Get the customer id via /auth/me
    var meResp = getAuth("/api/v1/auth/me", accessToken);
    assertThat(((Number) meResp.get("code")).intValue()).isEqualTo(0);
    var meData = (Map<String, Object>) meResp.get("data");
    long customerId = ((Number) meData.get("id")).longValue();

    // Insert a task_definition
    long taskId = insertTaskDefinition();

    // Insert a task_progress with status=2 (completed) and reward_diamonds=50
    int rewardDiamonds = 50;
    long progressId = insertCompletedTaskProgress(customerId, taskId, rewardDiamonds);

    // Record initial diamond balance
    long initialBalance =
        jdbc.queryForObject(
            "SELECT diamond_balance FROM customers WHERE id = ?", Long.class, customerId);

    int n = 10;
    var startGate = new CountDownLatch(1);
    var successes = new AtomicInteger();
    var failures = new AtomicInteger();

    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int i = 0; i < n; i++) {
        executor.submit(
            () -> {
              try {
                startGate.await();
                var resp = postAuth("/api/v1/tasks/" + progressId + "/claim", null, accessToken);
                int code = ((Number) resp.get("code")).intValue();
                if (code == 0) {
                  successes.incrementAndGet();
                } else {
                  failures.incrementAndGet();
                }
              } catch (Exception e) {
                failures.incrementAndGet();
              }
            });
      }
      startGate.countDown();
      executor.shutdown();
      executor.awaitTermination(30, TimeUnit.SECONDS);
    }

    assertThat(successes.get()).isEqualTo(1);
    assertThat(failures.get()).isEqualTo(n - 1);

    // Verify diamond_balance increased by reward amount exactly once
    long finalBalance =
        jdbc.queryForObject(
            "SELECT diamond_balance FROM customers WHERE id = ?", Long.class, customerId);
    assertThat(finalBalance).isEqualTo(initialBalance + rewardDiamonds);

    // Verify task_progress status changed to 3 (claimed)
    short taskStatus =
        jdbc.queryForObject(
            "SELECT task_status FROM task_progress WHERE id = ?", Short.class, progressId);
    assertThat(taskStatus).isEqualTo((short) 3);
  }

  // -- DB helpers -------------------------------------------------------------

  private long insertTestVideo(long customerId) {
    return jdbc.queryForObject(
        """
        INSERT INTO videos (customer_id, title, file_name, file_size, file_url, mime_type,
            status, total_clicks, valid_clicks, like_count, created_at, updated_at)
        VALUES (?, 'Test Video', 'test.mp4', 1024, 'https://stub.local/test.mp4', 'video/mp4',
            4, 0, 0, 0, NOW(), NOW())
        RETURNING id
        """,
        Long.class,
        customerId);
  }

  private long insertTaskDefinition() {
    String taskCode = "test_task_" + SEQ.incrementAndGet();
    return jdbc.queryForObject(
        """
        INSERT INTO task_definitions (task_code, task_name, description, task_type, task_category,
            target_count, reward_diamonds, display_order, status, created_at, updated_at)
        VALUES (?, 'Test Task', 'A test task', 1, 1, 1, 50, 1, 1, NOW(), NOW())
        RETURNING id
        """,
        Long.class,
        taskCode);
  }

  private long insertCompletedTaskProgress(long customerId, long taskId, int rewardDiamonds) {
    return jdbc.queryForObject(
        """
        INSERT INTO task_progress (customer_id, task_id, current_count, target_count,
            period_key, task_status, reward_diamonds, completed_at, created_at, updated_at)
        VALUES (?, ?, 1, 1, '2026-03-19', 2, ?, NOW(), NOW(), NOW())
        RETURNING id
        """,
        Long.class,
        customerId,
        taskId,
        rewardDiamonds);
  }
}
