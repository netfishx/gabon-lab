package lab.gabon.security;

import java.time.Duration;

public record JwtDomain(
    String issuer, String audience, String secret, Duration accessTtl, Duration refreshTtl) {

  public static final String CUSTOMER_ISSUER = "gabon-service";
  public static final String CUSTOMER_AUDIENCE = "customer";
  public static final String ADMIN_ISSUER = "gabon-admin";
  public static final String ADMIN_AUDIENCE = "admin";
}
