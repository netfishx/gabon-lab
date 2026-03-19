# Task 007: Customer Auth and Token Security Implementation

**type**: impl
**depends-on**: ["007-auth-test"]

## Description

Implement the full customer authentication system covering BDD Features 1 and 2. This is the Green phase -- make all auth tests pass.

### CustomerRepo

- `create(username, passwordHash)`: INSERT INTO customers, return generated ID
- `findByUsername(username)`: SELECT WHERE LOWER(username) = LOWER(?), return Customer entity or null
- `findById(id)`: SELECT by primary key, return Customer entity or null
- `updatePassword(id, newPasswordHash)`: UPDATE password_hash WHERE id = ?
- `updateLastLogin(id)`: UPDATE last_login_at = NOW() WHERE id = ?

All queries use Exposed DSL against the customers table defined in Task 003.

### AuthService

- `register(username, password)`: Validate input (username >= 3 chars, password >= 6 chars). Check uniqueness via findByUsername. Hash password with bcrypt(cost=10). Create customer. Generate token pair. Return TokenResponse.
- `login(username, password)`: Find by username (case-insensitive). Verify bcrypt. Update last_login_at. Generate token pair. Return TokenResponse.
- `refresh(refreshToken)`: Decode JWT. Verify token_type=refresh, iss=gabon-service, aud=customer. Extract family_id and jti. Redis CAS: call `casFamily("token:family:{family_id}", oldJti, newJti)`. If Success, return new tokens. If Conflict (replay detected), delete family via `deleteFamily` — revoke entire family. If Missing, reject. Return new TokenResponse or throw.
- `logout(accessToken)`: Decode JWT. Add JTI to Redis blacklist with TTL matching token remaining lifetime. DEL the token family from Redis.
- `changePassword(customerId, oldPassword, newPassword)`: Fetch customer. Verify old password with bcrypt. Hash new password. Update via repo.
- `getMe(customerId)`: Fetch customer by ID. Return profile data (id, username, name, phone, is_vip, avatar_url).

### JWT Token Generation

- Access token: iss=gabon-service, aud=customer, sub=customerId, token_type=access, jti=UUID, kid=JWT_CURRENT_KID, exp=JWT_CUSTOMER_ACCESS_TTL
- Refresh token: iss=gabon-service, aud=customer, sub=customerId, token_type=refresh, jti=UUID, family_id=UUID, kid=JWT_CURRENT_KID, exp=JWT_CUSTOMER_REFRESH_TTL
- Sign with HS256 using JWT_CUSTOMER_SECRET (from Task 005 dual-domain JwtService). Admin tokens use JWT_ADMIN_SECRET with iss=gabon-admin, aud=admin.

### Auth Routes (route/AuthRoutes.kt)

- `POST /api/v1/auth/register` -- body: {username, password} -> 201 with token pair
- `POST /api/v1/auth/login` -- body: {username, password} -> 200 with token pair
- `POST /api/v1/auth/refresh` -- body: {refresh_token} -> 200 with new token pair
- `POST /api/v1/auth/logout` -- requires auth -> 200
- `GET /api/v1/auth/me` -- requires auth -> 200 with customer profile
- `PUT /api/v1/auth/password` -- requires auth, body: {old_password, new_password} -> 200

### Route Registration

Register auth routes in `plugin/Routing.kt` under the `/api/v1/auth` route group. Public routes (register, login, refresh) have no auth middleware. Protected routes (me, password, logout) use the JWT auth middleware from Task 005.

### Redis Key Schema

- Token family: `token:family:{familyId}` -> stores current valid JTI, TTL = refresh token lifetime (7d)
- Blacklist: `token:blacklist:{jti}` -> value "1", TTL = access token remaining lifetime

## Files

- `kotlin/src/main/kotlin/lab/gabon/repository/CustomerRepo.kt` -- Customer data access layer
- `kotlin/src/main/kotlin/lab/gabon/service/AuthService.kt` -- Authentication business logic
- `kotlin/src/main/kotlin/lab/gabon/route/AuthRoutes.kt` -- Auth HTTP route handlers
- `kotlin/src/main/kotlin/lab/gabon/plugin/Routing.kt` -- Modified to register auth routes

## Verification

```bash
cd kotlin && ./gradlew test --tests '*Auth*'
```

- All 23+ auth tests PASS (Green phase)
- Register returns 201 with valid JWT tokens
- Login is case-insensitive and updates last_login_at
- Refresh rotates tokens with CAS, replay detection revokes family
- Logout blacklists JTI and destroys family
- Cross-domain admin token rejected on customer endpoints
- Concurrent refresh: exactly 1 of 10 succeeds
