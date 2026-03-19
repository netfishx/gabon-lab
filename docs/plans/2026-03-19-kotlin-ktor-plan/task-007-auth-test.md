# Task 007: Customer Auth and Token Security Tests

**type**: test
**depends-on**: ["003", "004", "005"]

## BDD Scenarios

```gherkin
Feature: Customer Authentication
  Customers register, login, refresh tokens, logout, and change passwords.
  Usernames are case-insensitive (LOWER). Passwords use bcrypt(cost=10).
  JWT tokens use iss=gabon-service, aud=customer, HS256.

  # --- Register ---

  Scenario: Successful registration
    Given no customer with username "alice" exists
    When I POST /api/v1/auth/register with:
      | username | alice      |
      | password | secret123  |
    Then the response status is 201
    And the response body contains "access_token" and "refresh_token"
    And a customer record exists with username "alice"

  Scenario: Registration with duplicate username
    Given a customer with username "bob" already exists
    When I POST /api/v1/auth/register with:
      | username | bob        |
      | password | secret123  |
    Then the response status is 409
    And the error code is "AUTH_USERNAME_EXISTS"

  Scenario: Registration with case-insensitive duplicate
    Given a customer with username "Charlie" already exists
    When I POST /api/v1/auth/register with:
      | username | charlie    |
      | password | secret123  |
    Then the response status is 409
    And the error code is "AUTH_USERNAME_EXISTS"

  Scenario: Registration with too-short username
    When I POST /api/v1/auth/register with:
      | username | ab         |
      | password | secret123  |
    Then the response status is 422
    And the error message mentions minimum length 3

  Scenario: Registration with too-short password
    When I POST /api/v1/auth/register with:
      | username | validuser  |
      | password | 12345      |
    Then the response status is 422
    And the error message mentions minimum length 6

  # --- Login ---

  Scenario: Successful login
    Given a customer "alice" with password "secret123" exists
    When I POST /api/v1/auth/login with:
      | username | alice      |
      | password | secret123  |
    Then the response status is 200
    And the response body contains "access_token" and "refresh_token"
    And the customer's last_login_at is updated

  Scenario: Login with case-insensitive username
    Given a customer "Alice" with password "secret123" exists
    When I POST /api/v1/auth/login with:
      | username | alice      |
      | password | secret123  |
    Then the response status is 200
    And the response body contains "access_token" and "refresh_token"

  Scenario: Login with wrong password
    Given a customer "alice" with password "secret123" exists
    When I POST /api/v1/auth/login with:
      | username | alice      |
      | password | wrongpass  |
    Then the response status is 401
    And the error code is "AUTH_INVALID_CREDENTIALS"

  Scenario: Login with nonexistent username
    When I POST /api/v1/auth/login with:
      | username | nonexistent |
      | password | secret123   |
    Then the response status is 401
    And the error code is "AUTH_INVALID_CREDENTIALS"

  # --- Get Current User ---

  Scenario: Get current user info with valid token
    Given I am logged in as customer "alice"
    When I GET /api/v1/auth/me with my access token
    Then the response status is 200
    And the response contains my id, username, name, phone, is_vip, avatar_url

  Scenario: Get current user without token
    When I GET /api/v1/auth/me without authorization
    Then the response status is 401
    And the error code is "UNAUTHORIZED"

  # --- Change Password ---

  Scenario: Successful password change
    Given I am logged in as customer "alice" with password "secret123"
    When I PUT /api/v1/auth/password with:
      | old_password | secret123     |
      | new_password | newsecret456  |
    Then the response status is 200
    And I can login with password "newsecret456"
    And I cannot login with password "secret123"

  Scenario: Change password with wrong old password
    Given I am logged in as customer "alice" with password "secret123"
    When I PUT /api/v1/auth/password with:
      | old_password | wrongoldpass  |
      | new_password | newsecret456  |
    Then the response status is 400
    And the error code is "AUTH_PASSWORD_MISMATCH"

  # --- Logout ---

  Scenario: Successful logout
    Given I am logged in as customer "alice"
    When I POST /api/v1/auth/logout with my access token
    Then the response status is 200
    And the token's JTI is added to the Redis blacklist
    And the token family is deleted from Redis

  Scenario: Logout invalidates refresh token
    Given I am logged in as customer "alice" with tokens
    When I POST /api/v1/auth/logout with my access token
    And I POST /api/v1/auth/refresh with my refresh token
    Then the refresh response status is 401
    And the error code is "AUTH_TOKEN_INVALID"
    And the error message is "token family expired or revoked"
```

```gherkin
Feature: Token Refresh and Security
  Refresh tokens use a family-based rotation scheme with Redis CAS.
  Each family tracks the current valid JTI. Reuse of an old JTI
  triggers family revocation (replay detection).

  # --- Happy Path ---

  Scenario: Successful token refresh
    Given customer "alice" has a valid refresh token with family F1
    And the refresh token's JTI matches the current JTI in Redis family F1
    When I POST /api/v1/auth/refresh with:
      | refresh_token | <alice's refresh token> |
    Then the response status is 200
    And the response contains new "access_token" and "refresh_token"
    And the Redis family F1 now stores the new refresh token's JTI
    And the old refresh token is no longer valid for refresh

  # --- Expired Token ---

  Scenario: Refresh with expired token
    Given customer "alice" has an expired refresh token
    When I POST /api/v1/auth/refresh with the expired token
    Then the response status is 401
    And the error code is "AUTH_TOKEN_INVALID"

  # --- Access Token Used as Refresh ---

  Scenario: Refresh with access token instead of refresh token
    Given customer "alice" has a valid access token (token_type=access)
    When I POST /api/v1/auth/refresh with:
      | refresh_token | <alice's access token> |
    Then the response status is 401
    And the error code is "AUTH_TOKEN_INVALID"
    And the error message is "not a refresh token"

  # --- Concurrent Refresh (Replay Detection) ---

  Scenario: Concurrent refresh — only one succeeds
    Given customer "alice" has a valid refresh token T1
    When 10 concurrent requests POST /api/v1/auth/refresh with T1
    Then exactly 1 request returns status 200 with new tokens
    And the remaining 9 requests return status 401
    And the Redis family is deleted (revoked) after replay detection

  # --- Reuse After Rotation ---

  Scenario: Old refresh token reuse after successful rotation
    Given customer "alice" refreshed successfully, getting new token T2
    When I POST /api/v1/auth/refresh with the old token T1
    Then the response status is 401
    And the error code is "AUTH_TOKEN_INVALID"
    And the error message is "token reuse detected, family revoked"
    And the entire token family is deleted from Redis

  # --- Blacklisted Access Token ---

  Scenario: Using a blacklisted access token
    Given customer "alice" logged out, blacklisting her access token JTI
    When I GET /api/v1/auth/me with the blacklisted access token
    Then the response status is 401

  # --- Cross-Domain Token Rejection ---

  Scenario: Admin token used on customer endpoint
    Given I have a valid admin JWT (iss=gabon-admin, aud=admin)
    When I GET /api/v1/auth/me with the admin token
    Then the response status is 401
    Because the middleware validates iss=gabon-service and aud=customer
```

## Description

Write tests covering all BDD scenarios from Feature 1 (Customer Authentication) and Feature 2 (Token Refresh and Security). This is the Red phase of TDD -- all tests must compile but FAIL because no implementation exists yet.

Test structure:

- **AuthRoutesTest.kt**: Integration tests using Ktor `testApplication`. Configure the full application (DB, Redis, routing) with a test module. Each BDD scenario maps to one `@Test` function. Use real PostgreSQL (Testcontainers or shared test DB) and real Redis for route-level tests. Test register/login/me/password/logout endpoints via the Ktor test client. Verify response status codes, JSON body structure (code, message, data fields), and error codes.

- **AuthServiceTest.kt**: Unit tests for AuthService logic. Mock CustomerRepo and Redis operations. Test bcrypt password hashing/verification, JWT token generation (iss=gabon-service, aud=customer, HS256), token family creation/rotation/revocation, blacklist operations, case-insensitive username lookup (LOWER), concurrent refresh CAS behavior.

Key test concerns:
- Register: verify 201 with tokens, 409 for duplicate (case-insensitive), 422 for validation
- Login: verify 200 with tokens + last_login_at update, 401 for wrong password/nonexistent user, case-insensitive matching
- Me: verify 200 with full profile, 401 without token
- Password change: verify old password verification, new password works after change, 400 for wrong old password
- Logout: verify JTI blacklisted in Redis, token family deleted, refresh token invalidated after logout
- Refresh: verify token rotation with new JTI in family, replay detection (old JTI revokes family), concurrent CAS (only 1 of 10 succeeds), expired token rejection, access token rejected as refresh
- Cross-domain: admin JWT (iss=gabon-admin) rejected by customer middleware

## Files

- `kotlin/src/test/kotlin/lab/gabon/route/AuthRoutesTest.kt` -- Integration tests for all auth endpoints
- `kotlin/src/test/kotlin/lab/gabon/service/AuthServiceTest.kt` -- Unit tests for auth business logic

## Verification

```bash
cd kotlin && ./gradlew test --tests '*Auth*'
```

- All tests compile successfully
- All tests FAIL (Red phase) because AuthService, CustomerRepo, and auth routes do not exist yet
- Test count matches BDD scenario count (16 Feature 1 + 7 Feature 2 = 23 scenarios minimum)
