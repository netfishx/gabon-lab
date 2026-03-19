# Task 010: Social System Tests

**type**: test
**depends-on**: ["003", "007-auth-impl"]

## BDD Scenarios

```gherkin
Feature: Social System
  Follow uses ON CONFLICT DO NOTHING. Self-follow is forbidden via
  CHECK constraint and application validation. Mutual follow detection:
  follow_status 0=none, 1=one-way, 2=mutual.

  # --- Follow ---

  Scenario: Follow another user
    Given I am logged in as customer "alice" (id=1)
    And customer "bob" (id=2) exists
    When I POST /api/v1/users/2/follow
    Then the response status is 200
    And a user_follows record (follower_id=1, followed_id=2) exists

  Scenario: Follow a nonexistent user
    Given I am logged in as customer "alice"
    When I POST /api/v1/users/999999/follow
    Then the response status is 404
    And the error code is "NOT_FOUND"
    And the error message is "user not found"

  Scenario: Follow yourself
    Given I am logged in as customer "alice" (id=1)
    When I POST /api/v1/users/1/follow
    Then the response status is 400
    And the error code is "USER_CANNOT_FOLLOW_SELF"

  Scenario: Follow an already-followed user
    Given I am logged in as customer "alice"
    And alice already follows bob
    When I POST /api/v1/users/{bob_id}/follow
    Then the response status is 409
    And the error code is "USER_ALREADY_FOLLOWING"

  # --- Unfollow ---

  Scenario: Unfollow a user
    Given I am logged in as customer "alice" who follows "bob"
    When I DELETE /api/v1/users/{bob_id}/follow
    Then the response status is 200
    And the user_follows record is deleted

  Scenario: Unfollow yourself
    Given I am logged in as customer "alice" (id=1)
    When I DELETE /api/v1/users/1/follow
    Then the response status is 400
    And the error code is "USER_CANNOT_FOLLOW_SELF"

  Scenario: Unfollow a user not currently followed
    Given I am logged in as customer "alice"
    And alice does NOT follow bob
    When I DELETE /api/v1/users/{bob_id}/follow
    Then the response status is 400
    And the error code is "USER_NOT_FOLLOWING"

  # --- Mutual Follow Detection ---

  Scenario: One-way follow status
    Given alice follows bob, but bob does NOT follow alice
    When I GET /api/v1/users/{bob_id} with alice's token
    Then the response contains follow_status=1

  Scenario: Mutual follow status
    Given alice follows bob AND bob follows alice
    When I GET /api/v1/users/{bob_id} with alice's token
    Then the response contains follow_status=2

  Scenario: No follow status
    Given alice does NOT follow bob
    When I GET /api/v1/users/{bob_id} with alice's token
    Then the response contains follow_status=0

  Scenario: Follow status for unauthenticated viewer
    When I GET /api/v1/users/{bob_id} without auth
    Then the response contains follow_status=0

  Scenario: Viewing own profile shows follow_status=0
    Given I am logged in as customer "alice" (id=1)
    When I GET /api/v1/users/1 with alice's token
    Then the response contains follow_status=0

  # --- Following/Followers Lists ---

  Scenario: Get my following list
    Given I am logged in as customer "alice" who follows 3 users
    When I GET /api/v1/users/me/following?page=1&page_size=20
    Then the response contains 3 items with user_id, username, follow_status

  Scenario: Get my followers list
    Given I am logged in as customer "alice" with 2 followers
    When I GET /api/v1/users/me/followers?page=1&page_size=20
    Then the response contains 2 items

  Scenario: Following list shows mutual status
    Given alice follows bob, and bob follows alice (mutual)
    And alice follows charlie, but charlie does NOT follow alice (one-way)
    When I GET /api/v1/users/me/following with alice's token
    Then bob's entry shows follow_status=2
    And charlie's entry shows follow_status=1

  Scenario: Get another user's following list (public)
    Given user bob has 5 following entries
    When I GET /api/v1/users/{bob_id}/following
    Then the response status is 200
    And the response contains 5 items

  Scenario: Get another user's followers list (public)
    Given user bob has 3 followers
    When I GET /api/v1/users/{bob_id}/followers
    Then the response status is 200
    And the response contains 3 items
```

## Description

Write tests covering all BDD scenarios from Feature 5 (Social System). This is the Red phase -- all tests must compile but FAIL because no social implementation exists yet.

Test structure:

- **SocialRoutesTest.kt**: Integration tests using Ktor `testApplication`. Create multiple test users via the auth register endpoint (or direct DB inserts). Set up follow relationships for mutual detection tests.

Key test concerns:
- Follow: verify 200 and user_follows record created, 404 for nonexistent target, 400 for self-follow, 409 for already-following
- Unfollow: verify 200 and record deleted, 400 for self-unfollow, 400 for not-following
- Mutual detection: test all 3 states (0=none, 1=one-way, 2=mutual) via GET user profile endpoint. Verify unauthenticated always sees 0. Verify own profile always shows 0.
- Following/Followers lists: verify paginated response with correct item count, verify follow_status in each list item correctly reflects mutual state, verify public access to other users' lists

The mutual detection tests require careful setup of bidirectional follow relationships between test users.

## Files

- `kotlin/src/test/kotlin/lab/gabon/route/SocialRoutesTest.kt` -- Integration tests for follow/unfollow and social list endpoints

## Verification

```bash
cd kotlin && ./gradlew test --tests '*Social*'
```

- All tests compile successfully
- All tests FAIL (Red phase) because SocialService, SocialRepo, and social routes do not exist yet
- Test count matches BDD scenario count (17 scenarios minimum)
