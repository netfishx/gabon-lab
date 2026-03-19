package lab.gabon.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import lab.gabon.security.JwtAuthFilter;
import lab.gabon.security.JwtAuthFilter.PublicRoute;
import lab.gabon.security.RateLimitFilter;
import lab.gabon.service.JwtService;
import lab.gabon.service.TokenStore;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**").allowedOrigins("*").allowedMethods("*").allowedHeaders("*");
  }

  // -- JWT Auth Filters -------------------------------------------------------

  @Bean
  public FilterRegistrationBean<JwtAuthFilter> customerJwtFilter(
      JwtService jwtService, TokenStore tokenStore, ObjectMapper objectMapper) {
    var filter =
        new JwtAuthFilter(
            jwtService::verifyCustomerAccess,
            tokenStore,
            objectMapper,
            List.of(
                // Auth
                new PublicRoute("*", "/auth/register"),
                new PublicRoute("*", "/auth/login"),
                new PublicRoute("*", "/auth/refresh"),
                // Video — public list
                new PublicRoute("GET", "/videos"),
                new PublicRoute("GET", "/videos/featured"),
                // Video — optional auth (detail, play tracking)
                new PublicRoute("GET", "/videos/*"),
                new PublicRoute("POST", "/videos/*/play-click"),
                new PublicRoute("POST", "/videos/*/play-valid"),
                // User videos — public
                new PublicRoute("GET", "/users/*/videos")),
            "/api/v1");
    var reg = new FilterRegistrationBean<>(filter);
    reg.addUrlPatterns("/api/v1/*");
    reg.setOrder(10);
    return reg;
  }

  @Bean
  public FilterRegistrationBean<JwtAuthFilter> adminJwtFilter(
      JwtService jwtService, TokenStore tokenStore, ObjectMapper objectMapper) {
    var filter =
        new JwtAuthFilter(
            jwtService::verifyAdminAccess,
            tokenStore,
            objectMapper,
            List.of(new PublicRoute("*", "/auth/login"), new PublicRoute("*", "/auth/refresh")),
            "/admin/v1");
    var reg = new FilterRegistrationBean<>(filter);
    reg.addUrlPatterns("/admin/v1/*");
    reg.setOrder(10);
    return reg;
  }

  // -- Rate Limit Filters -----------------------------------------------------

  @Bean
  public FilterRegistrationBean<RateLimitFilter> authRateLimit(
      StringRedisTemplate redis, ObjectMapper mapper) {
    var filter = new RateLimitFilter(redis, mapper, "auth", 20, Duration.ofMinutes(1), false);
    var reg = new FilterRegistrationBean<>(filter);
    reg.addUrlPatterns("/api/v1/auth/*", "/admin/v1/auth/*");
    reg.setOrder(5);
    return reg;
  }

  @Bean
  public FilterRegistrationBean<RateLimitFilter> publicRateLimit(
      StringRedisTemplate redis, ObjectMapper mapper) {
    var filter = new RateLimitFilter(redis, mapper, "pub", 120, Duration.ofMinutes(1), false);
    var reg = new FilterRegistrationBean<>(filter);
    reg.addUrlPatterns("/api/v1/*");
    reg.setOrder(6);
    return reg;
  }

  @Bean
  public FilterRegistrationBean<RateLimitFilter> apiRateLimit(
      StringRedisTemplate redis, ObjectMapper mapper) {
    var filter = new RateLimitFilter(redis, mapper, "user", 200, Duration.ofMinutes(1), true);
    var reg = new FilterRegistrationBean<>(filter);
    reg.addUrlPatterns("/api/v1/*");
    reg.setOrder(20);
    return reg;
  }

  @Bean
  public FilterRegistrationBean<RateLimitFilter> adminRateLimit(
      StringRedisTemplate redis, ObjectMapper mapper) {
    var filter = new RateLimitFilter(redis, mapper, "admin", 200, Duration.ofMinutes(1), true);
    var reg = new FilterRegistrationBean<>(filter);
    reg.addUrlPatterns("/admin/v1/*");
    reg.setOrder(20);
    return reg;
  }
}
