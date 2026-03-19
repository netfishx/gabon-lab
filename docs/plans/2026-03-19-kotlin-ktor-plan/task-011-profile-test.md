# Task 011: User Profile Tests

**type**: test
**depends-on**: ["003", "006", "007-auth-impl"]

## BDD Scenarios

```gherkin
Feature: User Profile
  Customers can view/edit their profile and upload avatars via S3
  presigned URLs. Public profiles show follow counts and follow_status.

  # --- My Profile ---

  Scenario: Get my profile
    Given I am logged in as customer "alice"
    When I GET /api/v1/users/me/profile
    Then the response status is 200
    And the response contains id, username, name, phone, email,
        avatar_url, signature, is_vip, diamond_balance, last_login_at, created_at

  Scenario: Update my profile
    Given I am logged in as customer "alice"
    When I PUT /api/v1/users/me/profile with:
      | name      | Alice Wang       |
      | phone     | 13800138000      |
      | email     | alice@example.com|
      | signature | Hello world      |
    Then the response status is 200
    And the response reflects the updated fields
    Note: Empty fields are not overwritten (COALESCE NULLIF pattern)

  # --- Avatar Upload ---

  Scenario: Get avatar presigned upload URL
    Given I am logged in as customer "alice"
    When I POST /api/v1/users/me/avatar/upload-url with:
      | fileName    | avatar.jpg |
      | contentType | image/jpeg |
    Then the response status is 200
    And the response contains "uploadUrl" and "avatarUrl"

  Scenario: Confirm avatar upload
    Given I am logged in as customer "alice"
    When I POST /api/v1/users/me/avatar/confirm with:
      | avatarUrl | https://s3.example.com/avatars/123/abc.jpg |
    Then the response status is 200
    And alice's avatar_url is updated

  # --- Public Profile ---

  Scenario: Get another user's public profile
    Given user "bob" (id=2) exists with 10 following and 5 followers
    When I GET /api/v1/users/2 without auth
    Then the response status is 200
    And the response contains id, username, name, avatar_url, signature,
        is_vip, following_count=10, follower_count=5, follow_status=0

  Scenario: Get nonexistent user profile
    When I GET /api/v1/users/999999
    Then the response status is 404
    And the error code is "NOT_FOUND"
```

## Description

Write tests covering all BDD scenarios from Feature 10 (User Profile). This is the Red phase -- all tests must compile but FAIL because no profile implementation exists yet.

Test structure:

- **ProfileRoutesTest.kt**: Integration tests using Ktor `testApplication`. Create test users via auth register. Set up follow relationships for public profile tests (to verify following_count, follower_count, follow_status).

Key test concerns:
- Get my profile: verify 200 with all expected fields (id, username, name, phone, email, avatar_url, signature, is_vip, diamond_balance, last_login_at, created_at). This returns more data than GET /me (auth endpoint) because it includes email, diamond_balance, signature.
- Update profile: verify partial update works (only provided fields change). Verify empty string does NOT overwrite existing value (COALESCE NULLIF pattern). Verify response reflects updated fields.
- Avatar presign: verify 200 with uploadUrl and avatarUrl. The URL should be a valid presigned S3 URL. avatarUrl is the permanent URL after upload.
- Avatar confirm: verify 200 and avatar_url updated in the database. Subsequent GET profile should reflect new avatar.
- Public profile: verify 200 with public fields only (no email, no diamond_balance). Verify following_count and follower_count are correct. Verify follow_status=0 for unauthenticated. If authenticated, verify correct follow_status relative to viewer.
- Nonexistent user: verify 404 with NOT_FOUND error code.

Test data setup: create users with known follow relationships using direct DB inserts or the social follow endpoint (if available from Task 010).

## Files

- `kotlin/src/test/kotlin/lab/gabon/route/ProfileRoutesTest.kt` -- Integration tests for all profile endpoints

## Verification

```bash
cd kotlin && ./gradlew test --tests '*Profile*'
```

- All tests compile successfully
- All tests FAIL (Red phase) because UserService and profile routes do not exist yet
- Test count matches BDD scenario count (6 scenarios minimum)
