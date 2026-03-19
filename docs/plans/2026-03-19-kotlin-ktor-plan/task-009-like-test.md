# Task 009: Like System Tests

**type**: test
**depends-on**: ["008-video-impl"]

## BDD Scenarios

```gherkin
Feature: Like System
  Like/unlike use CTE-based atomic SQL:
  - Like: INSERT ON CONFLICT DO NOTHING + conditional UPDATE like_count + 1
  - Unlike: DELETE RETURNING id + conditional UPDATE like_count - 1 (floor 0)
  Only approved videos can be liked.

  # --- Like ---

  Scenario: Like an approved video
    Given I am logged in as customer "alice"
    And video 42 is approved with like_count=5
    When I POST /api/v1/videos/42/like
    Then the response status is 200
    And video 42 like_count becomes 6
    And a video_likes record (video_id=42, customer_id=alice) exists

  Scenario: Like a non-approved video
    Given I am logged in as customer "alice"
    And video 99 has status pending_review(3)
    When I POST /api/v1/videos/99/like
    Then the response status is 403
    And the error code is "VIDEO_NOT_APPROVED"

  Scenario: Like a nonexistent video
    Given I am logged in as customer "alice"
    When I POST /api/v1/videos/999999/like
    Then the response status is 404
    And the error code is "VIDEO_NOT_FOUND"

  Scenario: Like an already-liked video (idempotent CTE)
    Given I am logged in as customer "alice"
    And alice has already liked video 42 (like_count=6)
    When I POST /api/v1/videos/42/like
    Then the CTE INSERT does ON CONFLICT DO NOTHING (no row returned)
    And the UPDATE does not execute (no EXISTS match)
    And like_count remains 6

  Scenario: Concurrent likes from 10 different users
    Given video 42 is approved with like_count=0
    And 10 different authenticated users concurrently POST /api/v1/videos/42/like
    Then all 10 requests return status 200
    And video 42 like_count is exactly 10

  # --- Unlike ---

  Scenario: Unlike a previously liked video
    Given I am logged in as customer "alice"
    And alice has liked video 42 (like_count=6)
    When I DELETE /api/v1/videos/42/like
    Then the response status is 200
    And video 42 like_count becomes 5
    And the video_likes record is deleted

  Scenario: Unlike a video not previously liked
    Given I am logged in as customer "alice"
    And alice has NOT liked video 42 (like_count=5)
    When I DELETE /api/v1/videos/42/like
    Then the CTE DELETE returns no rows
    And the UPDATE does not execute
    And like_count remains 5

  Scenario: Unlike without auth
    When I DELETE /api/v1/videos/42/like without authorization
    Then the response status is 401
```

## Description

Write tests covering all BDD scenarios from Feature 4 (Like System). This is the Red phase -- all tests must compile but FAIL because no like implementation exists yet.

Test structure:

- **LikeRoutesTest.kt**: Integration tests using Ktor `testApplication`. Set up test data: approved videos with known like_count, video_likes records for idempotency tests. Use auth helpers from 007 tests to create authenticated users.

Key test concerns:
- Like approved video: verify 200, like_count incremented by exactly 1, video_likes record created
- Like non-approved video: verify 403 with VIDEO_NOT_APPROVED error code
- Like nonexistent video: verify 404 with VIDEO_NOT_FOUND error code
- Idempotent like (CTE behavior): second like does NOT increment like_count -- verify exact count unchanged
- Concurrent likes (critical): launch 10 coroutines each with a different authenticated user, all liking the same video simultaneously. Verify final like_count is exactly 10 (not more, not less). This tests CTE atomicity.
- Unlike: verify 200, like_count decremented by 1, video_likes record removed
- Unlike not-liked video: verify like_count unchanged (CTE DELETE returns no rows, UPDATE does not fire)
- Unlike without auth: verify 401

The concurrent test is the most important test for this feature -- it validates that the CTE SQL approach handles race conditions correctly without application-level locks.

## Files

- `kotlin/src/test/kotlin/lab/gabon/route/LikeRoutesTest.kt` -- Integration tests for like/unlike endpoints

## Verification

```bash
cd kotlin && ./gradlew test --tests '*Like*'
```

- All tests compile successfully
- All tests FAIL (Red phase) because like/unlike routes and CTE SQL do not exist yet
- Test count matches BDD scenario count (8 scenarios minimum)
