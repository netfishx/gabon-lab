package lab.gabon.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import lab.gabon.common.ApiResponse;
import lab.gabon.common.AppError;
import lab.gabon.service.JwtService;
import lab.gabon.service.TokenStore;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * JWT authentication filter that verifies Bearer tokens for a specific domain (customer or admin).
 *
 * <p>Public routes are matched by HTTP method + Ant-style path pattern. On public routes, auth is
 * optional: a valid token sets the userId attribute, but missing/invalid tokens are silently
 * ignored.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

  private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

  /** A public route: method ("*" = any) + Ant-style path pattern (relative to pathPrefix). */
  public record PublicRoute(String method, String pattern) {}

  private final Function<String, JwtService.TokenClaims> verifier;
  private final TokenStore tokenStore;
  private final ObjectMapper objectMapper;
  private final List<PublicRoute> publicRoutes;
  private final String pathPrefix;

  public JwtAuthFilter(
      Function<String, JwtService.TokenClaims> verifier,
      TokenStore tokenStore,
      ObjectMapper objectMapper,
      List<PublicRoute> publicRoutes,
      String pathPrefix) {
    this.verifier = verifier;
    this.tokenStore = tokenStore;
    this.objectMapper = objectMapper;
    this.publicRoutes = publicRoutes;
    this.pathPrefix = pathPrefix;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var path = request.getRequestURI();
    var method = request.getMethod();

    if (isPublicRoute(method, path)) {
      tryExtractAuth(request);
      filterChain.doFilter(request, response);
      return;
    }

    var header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      writeError(response, new AppError.Unauthorized());
      return;
    }

    var token = header.substring(7);

    JwtService.TokenClaims claims;
    try {
      claims = verifier.apply(token);
    } catch (TokenExpiredException e) {
      writeError(response, new AppError.TokenExpired());
      return;
    } catch (JWTVerificationException e) {
      writeError(response, new AppError.TokenInvalid());
      return;
    } catch (Exception e) {
      // AppException wraps TokenExpired/TokenInvalid — unwrap to preserve error codes
      if (e instanceof lab.gabon.common.AppException appEx) {
        writeError(response, appEx.getError());
        return;
      }
      writeError(response, new AppError.TokenInvalid());
      return;
    }

    if (tokenStore.isBlacklisted(claims.jti())) {
      writeError(response, new AppError.TokenInvalid());
      return;
    }

    request.setAttribute("userId", claims.userId());
    if (claims.jti() != null) {
      request.setAttribute("jti", claims.jti());
    }
    if (claims.familyId() != null) {
      request.setAttribute("familyId", claims.familyId());
    }
    if (claims.role() != null) {
      request.setAttribute("role", claims.role());
    }
    filterChain.doFilter(request, response);
  }

  /**
   * Best-effort token extraction for public routes (optional auth). Sets userId attribute if a
   * valid token is present; silently ignores any auth failures.
   */
  private void tryExtractAuth(HttpServletRequest request) {
    var header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      return;
    }
    try {
      var claims = verifier.apply(header.substring(7));
      if (!tokenStore.isBlacklisted(claims.jti())) {
        request.setAttribute("userId", claims.userId());
      }
    } catch (Exception ignored) {
      // Optional auth — failures are expected and safe to ignore
    }
  }

  private boolean isPublicRoute(String method, String fullPath) {
    var relative =
        fullPath.startsWith(pathPrefix) ? fullPath.substring(pathPrefix.length()) : fullPath;
    for (var route : publicRoutes) {
      boolean methodMatch = "*".equals(route.method()) || route.method().equalsIgnoreCase(method);
      if (methodMatch && PATH_MATCHER.match(route.pattern(), relative)) {
        return true;
      }
    }
    return false;
  }

  private void writeError(HttpServletResponse response, AppError error) throws IOException {
    response.setStatus(error.status());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), ApiResponse.error(error));
  }
}
