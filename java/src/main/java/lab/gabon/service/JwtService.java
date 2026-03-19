package lab.gabon.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import java.util.Date;
import java.util.UUID;
import lab.gabon.common.AppError;
import lab.gabon.common.AppException;
import lab.gabon.config.AppConfig;
import lab.gabon.security.JwtDomain;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  public record TokenPair(
      String accessToken,
      String refreshToken,
      String familyId,
      String accessJti,
      String refreshJti) {}

  public record TokenClaims(
      long userId, String jti, String tokenType, String familyId, String kid, String role) {}

  private final AppConfig.JwtConfig config;
  private final Algorithm customerAlgorithm;
  private final Algorithm adminAlgorithm;
  private final JWTVerifier customerVerifier;
  private final JWTVerifier adminVerifier;

  public JwtService(AppConfig appConfig) {
    this.config = appConfig.jwt();
    this.customerAlgorithm = Algorithm.HMAC256(config.customerSecret());
    this.adminAlgorithm = Algorithm.HMAC256(config.adminSecret());

    this.customerVerifier =
        JWT.require(customerAlgorithm)
            .withIssuer(JwtDomain.CUSTOMER_ISSUER)
            .withAudience(JwtDomain.CUSTOMER_AUDIENCE)
            .build();

    this.adminVerifier =
        JWT.require(adminAlgorithm)
            .withIssuer(JwtDomain.ADMIN_ISSUER)
            .withAudience(JwtDomain.ADMIN_AUDIENCE)
            .build();
  }

  // -- Generate ---------------------------------------------------------------

  public TokenPair generateCustomerTokens(long customerId, String existingFamilyId) {
    var familyId = existingFamilyId != null ? existingFamilyId : UUID.randomUUID().toString();
    var accessJti = UUID.randomUUID().toString();
    var refreshJti = UUID.randomUUID().toString();
    var now = System.currentTimeMillis();

    var accessToken =
        JWT.create()
            .withIssuer(JwtDomain.CUSTOMER_ISSUER)
            .withAudience(JwtDomain.CUSTOMER_AUDIENCE)
            .withKeyId(config.currentKid())
            .withSubject(Long.toString(customerId))
            .withJWTId(accessJti)
            .withClaim("token_type", "access")
            .withClaim("family_id", familyId)
            .withIssuedAt(new Date(now))
            .withExpiresAt(new Date(now + config.customerAccessTtl().toMillis()))
            .sign(customerAlgorithm);

    var refreshToken =
        JWT.create()
            .withIssuer(JwtDomain.CUSTOMER_ISSUER)
            .withAudience(JwtDomain.CUSTOMER_AUDIENCE)
            .withKeyId(config.currentKid())
            .withSubject(Long.toString(customerId))
            .withJWTId(refreshJti)
            .withClaim("token_type", "refresh")
            .withClaim("family_id", familyId)
            .withIssuedAt(new Date(now))
            .withExpiresAt(new Date(now + config.customerRefreshTtl().toMillis()))
            .sign(customerAlgorithm);

    return new TokenPair(accessToken, refreshToken, familyId, accessJti, refreshJti);
  }

  public TokenPair generateAdminTokens(long adminId, String role, String existingFamilyId) {
    var familyId = existingFamilyId != null ? existingFamilyId : UUID.randomUUID().toString();
    var accessJti = UUID.randomUUID().toString();
    var refreshJti = UUID.randomUUID().toString();
    var now = System.currentTimeMillis();

    var accessToken =
        JWT.create()
            .withIssuer(JwtDomain.ADMIN_ISSUER)
            .withAudience(JwtDomain.ADMIN_AUDIENCE)
            .withKeyId(config.currentKid())
            .withSubject(Long.toString(adminId))
            .withJWTId(accessJti)
            .withClaim("token_type", "access")
            .withClaim("family_id", familyId)
            .withClaim("role", role)
            .withIssuedAt(new Date(now))
            .withExpiresAt(new Date(now + config.adminAccessTtl().toMillis()))
            .sign(adminAlgorithm);

    var refreshToken =
        JWT.create()
            .withIssuer(JwtDomain.ADMIN_ISSUER)
            .withAudience(JwtDomain.ADMIN_AUDIENCE)
            .withKeyId(config.currentKid())
            .withSubject(Long.toString(adminId))
            .withJWTId(refreshJti)
            .withClaim("token_type", "refresh")
            .withClaim("family_id", familyId)
            .withClaim("role", role)
            .withIssuedAt(new Date(now))
            .withExpiresAt(new Date(now + config.adminRefreshTtl().toMillis()))
            .sign(adminAlgorithm);

    return new TokenPair(accessToken, refreshToken, familyId, accessJti, refreshJti);
  }

  // -- Verify -----------------------------------------------------------------

  public TokenClaims verifyCustomerAccess(String token) {
    return verifyToken(token, customerVerifier, "access");
  }

  public TokenClaims verifyCustomerRefresh(String token) {
    return verifyToken(token, customerVerifier, "refresh");
  }

  public TokenClaims verifyAdminAccess(String token) {
    return verifyToken(token, adminVerifier, "access");
  }

  public TokenClaims verifyAdminRefresh(String token) {
    return verifyToken(token, adminVerifier, "refresh");
  }

  // -- Config accessors (for TokenStore TTL calculations) ---------------------

  public AppConfig.JwtConfig getConfig() {
    return config;
  }

  // -- Internal ---------------------------------------------------------------

  private TokenClaims verifyToken(String token, JWTVerifier verifier, String expectedTokenType) {
    DecodedJWT payload;
    try {
      payload = verifier.verify(token);
    } catch (TokenExpiredException e) {
      throw new AppException(new AppError.TokenExpired());
    } catch (JWTVerificationException e) {
      throw new AppException(new AppError.TokenInvalid());
    }

    var subject = payload.getSubject();
    if (subject == null) {
      throw new AppException(new AppError.TokenInvalid());
    }
    long userId;
    try {
      userId = Long.parseLong(subject);
    } catch (NumberFormatException e) {
      throw new AppException(new AppError.TokenInvalid());
    }

    var jti = payload.getId();
    if (jti == null) {
      throw new AppException(new AppError.TokenInvalid());
    }

    var tokenType = payload.getClaim("token_type").asString();
    if (tokenType == null || !tokenType.equals(expectedTokenType)) {
      throw new AppException(new AppError.TokenInvalid());
    }

    var familyId = payload.getClaim("family_id").asString();
    if (familyId == null) {
      throw new AppException(new AppError.TokenInvalid());
    }

    var kid = payload.getKeyId();
    if (kid == null) {
      throw new AppException(new AppError.TokenInvalid());
    }

    var role = payload.getClaim("role").asString();

    return new TokenClaims(userId, jti, tokenType, familyId, kid, role);
  }
}
