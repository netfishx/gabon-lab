# Task 011: User Profile Implementation

**type**: impl
**depends-on**: ["011-profile-test"]

## Description

Implement user profile management covering BDD Feature 10. This is the Green phase -- make all profile tests pass.

### UserService

- `getMyProfile(customerId)`: Fetch full customer record by ID. Return all private fields: id, username, name, phone, email, avatar_url, signature, is_vip, diamond_balance, last_login_at, created_at.
- `updateProfile(customerId, name?, phone?, email?, signature?)`: Update only non-null, non-empty fields. Use the COALESCE NULLIF pattern in SQL: `UPDATE customers SET name = COALESCE(NULLIF(?, ''), name), phone = COALESCE(NULLIF(?, ''), phone), ... WHERE id = ?`. This ensures empty strings do not overwrite existing values. Return updated customer.
- `presignAvatarUpload(customerId, fileName, contentType)`: Generate S3 key as `avatars/{customerId}/{uuid}.{ext}`. Create presigned PUT URL. Return uploadUrl and avatarUrl (the permanent access URL).
- `confirmAvatarUpload(customerId, avatarUrl)`: UPDATE customers SET avatar_url = ? WHERE id = ?. Return success.
- **NOTE**: `getPublicProfile` / `GET /api/v1/users/{userId}` is implemented in Task 010 (SocialRoutes), NOT here. This avoids the circular dependency between social (needs public profile endpoint for follow_status tests) and profile (needs SocialService for follow counts).

### CustomerRepo Extensions

Add to existing CustomerRepo (from Task 007):
- `updateProfile(id, name?, phone?, email?, signature?)`: Execute COALESCE NULLIF update
- `updateAvatarUrl(id, avatarUrl)`: Simple UPDATE of avatar_url

### User Routes (route/UserRoutes.kt)

Authenticated:
- `GET /api/v1/users/me/profile` -- get my full profile
- `PUT /api/v1/users/me/profile` -- update profile fields
- `POST /api/v1/users/me/avatar/upload-url` -- presign avatar upload
- `POST /api/v1/users/me/avatar/confirm` -- confirm avatar uploaded

Register user routes in `plugin/Routing.kt`. Note: `GET /api/v1/users/{userId}` (public profile) is in Task 010 SocialRoutes.

### COALESCE NULLIF Pattern

The profile update uses this SQL pattern to avoid overwriting existing values with empty strings:
```sql
UPDATE customers SET
  name = COALESCE(NULLIF($1, ''), name),
  phone = COALESCE(NULLIF($2, ''), phone),
  email = COALESCE(NULLIF($3, ''), email),
  signature = COALESCE(NULLIF($4, ''), signature)
WHERE id = $5
```

`NULLIF(value, '')` returns NULL if value is empty string. `COALESCE(NULL, name)` falls back to the existing column value. This is a single-query partial update without conditional logic in Kotlin.

### Dependency Note

This task has NO dependency on Task 010 (Social). The public profile endpoint (`GET /users/{userId}`) with follow_status is handled by Task 010. This task only handles `/users/me/*` private endpoints.

## Files

- `kotlin/src/main/kotlin/lab/gabon/service/UserService.kt` -- User profile business logic
- `kotlin/src/main/kotlin/lab/gabon/route/UserRoutes.kt` -- User profile HTTP route handlers
- `kotlin/src/main/kotlin/lab/gabon/repository/CustomerRepo.kt` -- Extended with updateProfile, updateAvatarUrl
- `kotlin/src/main/kotlin/lab/gabon/plugin/Routing.kt` -- Modified to register user routes

## Verification

```bash
cd kotlin && ./gradlew test --tests '*Profile*'
```

- All 6+ profile tests PASS (Green phase)
- Get my profile returns all private fields including diamond_balance
- Update profile: partial update works, empty fields do not overwrite
- Avatar presign returns valid uploadUrl and avatarUrl
- Avatar confirm updates avatar_url in DB
- Public profile returns follow counts and follow_status
- Nonexistent user returns 404
