package lab.gabon.security;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.function.Function;
import lab.gabon.common.ApiResponse;
import lab.gabon.common.AppError;
import lab.gabon.service.JwtService;
import lab.gabon.service.TokenStore;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT authentication filter that verifies Bearer tokens for a specific domain (customer or admin).
 *
 * <p>Skips public paths (e.g. register, login, refresh). On auth failure, writes a JSON error
 * response directly without propagating to the controller layer.
 */
public class JwtAuthFilter extends OncePerRequestFilter {

  private final Function<String, JwtService.TokenClaims> verifier;
  private final TokenStore tokenStore;
  private final ObjectMapper objectMapper;
  private final Set<String> publicPaths;

  public JwtAuthFilter(
      Function<String, JwtService.TokenClaims> verifier,
      TokenStore tokenStore,
      ObjectMapper objectMapper,
      Set<String> publicPaths) {
    this.verifier = verifier;
    this.tokenStore = tokenStore;
    this.objectMapper = objectMapper;
    this.publicPaths = publicPaths;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    var path = request.getRequestURI();

    if (isPublicPath(path)) {
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
    filterChain.doFilter(request, response);
  }

  private boolean isPublicPath(String path) {
    for (var suffix : publicPaths) {
      if (path.endsWith(suffix)) {
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
