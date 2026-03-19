# Task 005: Dual-domain JWT service

**type**: setup
**depends-on**: [002]

## Description

Implement the JWT token generation, parsing, and Ktor authentication plugin configuration. The system has two independent authentication domains (customer and admin) with separate secrets, issuers, and audiences.

Key decisions:

- **JwtService** (`service/JwtService.kt`): implement a class that handles token generation and parsing for both domains.

  Generation:
  - `fun generateCustomerTokens(customerId: Long): TokenPair` — produce access + refresh tokens with claims: `sub` = customerId (string), `iss` = "gabon-service", `aud` = "customer", `jti` = random UUID, `token_type` = "access" or "refresh", `family_id` = random UUID (shared between the pair), `kid` = JWT_CURRENT_KID from config (e.g., "key-2026-03"). Sign with HS256 using JWT_CUSTOMER_SECRET. Access token TTL = JWT_CUSTOMER_ACCESS_TTL, refresh token TTL = JWT_CUSTOMER_REFRESH_TTL.
  - `fun generateAdminTokens(adminId: Long, role: String): TokenPair` — same structure but `iss` = "gabon-admin", `aud` = "admin", `kid` = JWT_CURRENT_KID (same config value, shared across domains for key rotation), extra claim `role` = role string. Sign with JWT_ADMIN_SECRET. TTLs from JWT_ADMIN_ACCESS_TTL / JWT_ADMIN_REFRESH_TTL.
  - `TokenPair` is a data class with `accessToken: String`, `refreshToken: String`, `familyId: String`, `accessJti: String`, `refreshJti: String`.

  Parsing:
  - `fun parseCustomerToken(token: String): TokenClaims` — verify signature with customer secret, validate issuer = "gabon-service", audience contains "customer". Extract all claims into a `TokenClaims` data class.
  - `fun parseAdminToken(token: String): TokenClaims` — same but with admin secret, issuer = "gabon-admin", audience = "admin".
  - Triple validation on every parse: algorithm (HS256), issuer, audience. Reject if any fails.
  - Map JWT library exceptions to AppError: expired -> TokenExpired, invalid signature/claims -> TokenInvalid.

  `TokenClaims` data class: `sub: Long, jti: String, tokenType: String, familyId: String, kid: String, role: String? (null for customer)`.

- **Authentication plugin** (`plugin/Authentication.kt`): configure Ktor's `install(Authentication)` with two JWT schemes:

  - `jwt("customer")`: use customer secret as HMAC verifier, validate issuer/audience, in the `validate` block: extract claims, verify `token_type == "access"`, check `isBlacklisted(jti)` via TokenStore — if blacklisted, return null (401). On success, store a `CustomerPrincipal(customerId, jti, familyId)` as the principal.

  - `jwt("admin")`: same pattern with admin secret/issuer/audience, store `AdminPrincipal(adminId, role, jti, familyId)`.

  Define `data class CustomerPrincipal(val customerId: Long, val jti: String, val familyId: String) : Principal` and `data class AdminPrincipal(val adminId: Long, val role: String, val jti: String, val familyId: String) : Principal`.

  Provide extension functions on `ApplicationCall` for ergonomic access: `val ApplicationCall.customerPrincipal: CustomerPrincipal` and `val ApplicationCall.adminPrincipal: AdminPrincipal` (throw Unauthorized if missing).

- **Use ktor-server-auth-jwt** library which integrates with com.auth0:java-jwt under the hood.

## Files

- `kotlin/src/main/kotlin/lab/gabon/service/JwtService.kt` — token generation (customer + admin), parsing with triple validation, TokenPair and TokenClaims data classes
- `kotlin/src/main/kotlin/lab/gabon/plugin/Authentication.kt` — Ktor Authentication install with jwt("customer") and jwt("admin") schemes, Principal data classes, call extension functions

## Verification

1. `./gradlew build` compiles without errors
2. Unit test: generate customer tokens for userId=1, parse the access token back, verify sub=1, iss="gabon-service", aud="customer", token_type="access", familyId matches
3. Unit test: generate admin tokens for adminId=1, role="super_admin", attempt to parse with customer parser — should throw TokenInvalid
4. Unit test: generate tokens, blacklist the access JTI in Redis, attempt to authenticate — should be rejected (401)
5. Integration test: hit an `authenticate("customer") { ... }` protected endpoint without token — returns 401. With valid token — returns 200
