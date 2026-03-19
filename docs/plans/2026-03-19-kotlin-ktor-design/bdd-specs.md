# BDD Specifications — Gabon Kotlin/Ktor Backend

Business behavior specifications for the short video platform API (Kotlin implementation).
Derived from Go (Echo + sqlc) and Rust (Axum + SQLx) reference implementations.

---

## Feature 1: Customer Authentication

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

---

## Feature 2: Token Refresh and Security

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

---

## Feature 3: Video Management

```gherkin
Feature: Video Management
  Video upload uses S3 presigned URLs. Lifecycle:
  presign -> client uploads to S3 -> confirm -> pending_review(3)
  -> admin approves(4) or rejects(5).

  # --- Presigned Upload URL ---

  Scenario: Generate presigned upload URL
    Given I am logged in as customer "alice"
    When I POST /api/v1/videos/upload-url with:
      | fileName    | my-video.mp4 |
      | contentType | video/mp4    |
    Then the response status is 200
    And the response contains "uploadUrl", "fileUrl", and "s3Key"
    And the s3Key follows pattern "<customerID>/<uuid>.mp4"

  Scenario: Generate presigned URL without auth
    When I POST /api/v1/videos/upload-url without authorization
    Then the response status is 401

  # --- Confirm Upload ---

  Scenario: Confirm video upload
    Given I am logged in as customer "alice"
    And I have obtained a presigned upload URL with s3Key "123/abc.mp4"
    When I POST /api/v1/videos/confirm-upload with:
      | s3Key    | 123/abc.mp4   |
      | fileName | my-video.mp4  |
      | fileSize | 10485760      |
      | mimeType | video/mp4     |
      | title    | My First Video|
    Then the response status is 201
    And a video record is created with status=3 (pending_review)

  # --- List Videos (Public) ---

  Scenario: List approved videos without auth
    Given there are 5 approved videos in the system
    When I GET /api/v1/videos?page=1&page_size=20
    Then the response status is 200
    And the response contains items array with total, page, pageSize
    And all returned videos have approved status

  Scenario: Search videos by keyword
    Given an approved video titled "Cute Cat Dancing"
    When I GET /api/v1/videos?keyword=cat
    Then the response includes the "Cute Cat Dancing" video

  # --- Featured Videos ---

  Scenario: List featured videos
    When I GET /api/v1/videos/featured?page=1&page_size=10
    Then the response status is 200
    And the response contains featured approved videos

  # --- Video Detail ---

  Scenario: Get approved video detail (public)
    Given an approved video with id 42
    When I GET /api/v1/videos/42 without auth
    Then the response status is 200
    And the response contains video metadata and is_liked=false

  Scenario: Get approved video detail (authenticated)
    Given I am logged in as customer "alice"
    And video 42 is approved and alice has liked it
    When I GET /api/v1/videos/42 with my access token
    Then the response contains is_liked=true

  Scenario: Get unapproved video by its owner
    Given I am logged in as customer "alice"
    And video 99 has status pending_review(3) and belongs to alice
    When I GET /api/v1/videos/99 with my access token
    Then the response status is 200
    Because the owner can see their own unapproved videos

  Scenario: Get unapproved video by non-owner
    Given I am logged in as customer "bob"
    And video 99 has status pending_review(3) and belongs to alice
    When I GET /api/v1/videos/99 with bob's access token
    Then the response status is 403
    And the error code is "VIDEO_NOT_APPROVED"

  Scenario: Get nonexistent video
    When I GET /api/v1/videos/999999
    Then the response status is 404
    And the error code is "VIDEO_NOT_FOUND"

  # --- My Videos ---

  Scenario: List my videos with status filter
    Given I am logged in as customer "alice" with 3 approved and 2 pending videos
    When I GET /api/v1/videos/me?status=3
    Then the response contains only the 2 pending videos

  # --- User Videos (Public) ---

  Scenario: List another user's approved videos
    Given user 42 has 5 approved videos and 2 pending videos
    When I GET /api/v1/users/42/videos
    Then the response contains only the 5 approved videos

  # --- Delete Video ---

  Scenario: Delete own video
    Given I am logged in as customer "alice" who owns video 42
    When I DELETE /api/v1/videos/42
    Then the response status is 200
    And the video is soft-deleted (deleted_at is set)

  # --- Play Recording ---

  Scenario: Record play click
    Given video 42 exists with total_clicks=10
    When I POST /api/v1/videos/42/play-click
    Then the response status is 200
    And video 42 total_clicks becomes 11
    And a play_record is created with play_type=1

  Scenario: Record valid play increments watch task progress
    Given I am logged in as customer "alice"
    And alice has an active daily watch task with target_count=3, current_count=1
    When I POST /api/v1/videos/42/play-valid with my access token
    Then the response status is 200
    And video 42 valid_clicks increments by 1
    And alice's watch task current_count becomes 2
```

---

## Feature 4: Like System

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

---

## Feature 5: Social System (Follow/Unfollow)

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

---

## Feature 6: Task System

```gherkin
Feature: Task System
  Tasks have types: daily(1), weekly(2), monthly(3).
  Period keys use Asia/Shanghai timezone:
    daily="2026-03-19", weekly="2026-W12", monthly="2026-03".
  Task progress statuses: in_progress(1), completed(2), claimed(3), expired(4).
  Claiming uses FOR UPDATE row lock + transactional diamond credit.

  # --- List Tasks ---

  Scenario: List all active tasks with auto-created progress
    Given I am logged in as customer "alice"
    And there are 3 active task definitions (1 daily, 1 weekly, 1 monthly)
    When I GET /api/v1/tasks
    Then the response status is 200
    And the response contains 3 task items
    And each item has task_id, task_code, task_name, task_type, target_count,
        reward_diamonds, progress_id, current_count, task_status
    And progress records are auto-created (upserted) for the current period

  Scenario: Filter tasks by type
    Given I am logged in as customer "alice"
    When I GET /api/v1/tasks?task_type=1
    Then only daily tasks are returned

  Scenario: Filter tasks by status
    Given I am logged in as customer "alice"
    And alice has 1 completed task and 2 in-progress tasks
    When I GET /api/v1/tasks?task_status=2
    Then only the 1 completed task is returned

  # --- Period Key Generation ---

  Scenario Outline: Period key for task types
    Given the current time in Asia/Shanghai is <datetime>
    When the system generates a period key for task type <type>
    Then the period key is "<expected_key>"

    Examples:
      | datetime            | type | expected_key |
      | 2026-03-19 08:00:00 | 1    | 2026-03-19   |
      | 2026-03-19 08:00:00 | 2    | 2026-W12     |
      | 2026-03-19 08:00:00 | 3    | 2026-03      |
      | 2026-01-01 00:30:00 | 2    | 2026-W01     |
      | 2025-12-31 23:59:59 | 1    | 2026-01-01   |

  Note: The last example assumes UTC 23:59:59 Dec 31 is already Jan 1 in Asia/Shanghai (+8)

  # --- Task Progress via Valid Play ---

  Scenario: Watching videos increments watch task progress
    Given I am logged in as customer "alice"
    And there is a daily watch task (category=1) with target_count=3
    And alice's current_count is 1, task_status=1 (in_progress)
    When I record a valid play on any video
    Then alice's watch task current_count becomes 2

  Scenario: Reaching target count auto-completes task
    Given alice's daily watch task has target_count=3, current_count=2
    When alice records 1 more valid play
    Then current_count becomes 3
    And task_status changes to 2 (completed)
    And completed_at is set

  # --- Claim Reward ---

  Scenario: Claim reward for completed task
    Given I am logged in as customer "alice"
    And alice has a completed task progress (id=10, status=2, reward=50)
    And alice's diamond_balance is 100
    When I POST /api/v1/tasks/10/claim
    Then the response status is 200
    And alice's diamond_balance becomes 150
    And task progress status changes to 3 (claimed)
    And claimed_at is set

  Scenario: Claim reward for in-progress task
    Given I am logged in as customer "alice"
    And alice has an in-progress task (status=1)
    When I POST /api/v1/tasks/{progressId}/claim
    Then the response status is 400
    And the error code is "TASK_NOT_CLAIMABLE"
    And the error message is "task is not completed"

  Scenario: Claim already-claimed reward
    Given I am logged in as customer "alice"
    And alice has a claimed task (status=3)
    When I POST /api/v1/tasks/{progressId}/claim
    Then the response status is 400
    And the error code is "TASK_NOT_CLAIMABLE"

  Scenario: Claim reward for another user's task
    Given I am logged in as customer "alice"
    And progress 10 belongs to customer "bob"
    When I POST /api/v1/tasks/10/claim
    Then the response status is 400
    And the error code is "TASK_NOT_CLAIMABLE"
    And the error message is "task progress not found"

  Scenario: Concurrent claim — only one succeeds
    Given customer "alice" has a completed task (progress_id=10, reward=100)
    And alice's diamond_balance is 0
    When 10 concurrent requests POST /api/v1/tasks/10/claim with alice's token
    Then exactly 1 request returns status 200
    And the remaining 9 return status 400 with "TASK_NOT_CLAIMABLE"
    And alice's diamond_balance is exactly 100 (not 1000)
```

---

## Feature 7: Daily Sign-In

```gherkin
Feature: Daily Sign-In
  One sign-in per customer per day. Uses (customer_id, period_key)
  unique constraint. Awards base diamonds (1) within a transaction.
  Period key is daily format in Asia/Shanghai timezone.

  Scenario: Successful daily sign-in
    Given I am logged in as customer "alice"
    And alice has not signed in today (no record for today's period_key)
    And alice's diamond_balance is 50
    When I POST /api/v1/activity/sign-in
    Then the response status is 200
    And the response body contains diamonds=1
    And alice's diamond_balance becomes 51
    And a sign_in_record exists for (alice, today's period_key)

  Scenario: Duplicate sign-in on same day
    Given I am logged in as customer "alice"
    And alice has already signed in today
    When I POST /api/v1/activity/sign-in
    Then the response status is 409
    And the error code is "ALREADY_SIGNED_IN"
    And alice's diamond_balance is unchanged

  Scenario: Sign-in on next day (period key changes)
    Given alice signed in on "2026-03-18" (yesterday)
    And today is "2026-03-19" in Asia/Shanghai
    When alice POSTs /api/v1/activity/sign-in
    Then the response status is 200
    Because the period_key "2026-03-19" is different from "2026-03-18"

  Scenario: Concurrent sign-in requests — only one succeeds
    Given customer "alice" has not signed in today
    When 5 concurrent requests POST /api/v1/activity/sign-in with alice's token
    Then exactly 1 request returns status 200
    And the remaining 4 return status 409 with "ALREADY_SIGNED_IN"
    And alice's diamond_balance increases by exactly 1

  Scenario: Sign-in without auth
    When I POST /api/v1/activity/sign-in without authorization
    Then the response status is 401
```

---

## Feature 8: Admin Video Review

```gherkin
Feature: Admin Video Review
  Admins review videos (approve=4, reject=5).
  Admin auth uses separate JWT domain: iss=gabon-admin, aud=admin.
  Routes are under /admin/v1/.

  # --- Admin Auth ---

  Scenario: Admin login
    Given an active admin "superadmin1" with password "admin123"
    When I POST /admin/v1/auth/login with:
      | username | superadmin1 |
      | password | admin123    |
    Then the response status is 200
    And the response contains admin "access_token" and "refresh_token"

  Scenario: Disabled admin cannot login
    Given admin "disabled_admin" has status=0 (disabled)
    When I POST /admin/v1/auth/login with valid credentials
    Then the response status is 403
    And the error code is "FORBIDDEN"
    And the error message is "account is disabled"

  # --- List Videos with Filters ---

  Scenario: List all videos (admin)
    Given I am logged in as admin
    When I GET /admin/v1/videos?page=1&page_size=20
    Then the response status is 200
    And the response contains videos of all statuses

  Scenario: Filter videos by status
    Given I am logged in as admin
    When I GET /admin/v1/videos?status=3
    Then only videos with status=3 (pending_review) are returned

  Scenario: Filter videos by author name
    Given I am logged in as admin
    When I GET /admin/v1/videos?author_name=alice
    Then only videos by authors matching "alice" are returned

  Scenario: Filter videos by date range
    Given I am logged in as admin
    When I GET /admin/v1/videos?start_date=2026-03-01&end_date=2026-03-19
    Then only videos created within the date range are returned
    Note: end_date is exclusive (internally adds 1 day)

  Scenario: Filter videos by VIP author
    Given I am logged in as admin
    When I GET /admin/v1/videos?is_vip=true
    Then only videos from VIP authors are returned

  # --- Review Video ---

  Scenario: Approve a pending video
    Given I am logged in as admin (id=1)
    And video 42 has status=3 (pending_review)
    When I POST /admin/v1/videos/42/review with:
      | status       | 4               |
      | review_notes | Content is good |
    Then the response status is 200
    And video 42 status changes to 4 (approved)
    And reviewed_by is set to admin id=1
    And reviewed_at is set

  Scenario: Reject a pending video
    Given I am logged in as admin
    And video 42 has status=3 (pending_review)
    When I POST /admin/v1/videos/42/review with:
      | status       | 5                       |
      | review_notes | Inappropriate content   |
    Then the response status is 200
    And video 42 status changes to 5 (rejected)

  Scenario: Review with invalid status value
    Given I am logged in as admin
    When I POST /admin/v1/videos/42/review with:
      | status | 3 |
    Then the response status is 422
    Because status must be 4 (approved) or 5 (rejected)

  # --- Admin Video Detail ---

  Scenario: Get video detail (admin)
    Given I am logged in as admin
    When I GET /admin/v1/videos/42
    Then the response status is 200
    And the response contains full detail including review_notes, reviewed_by,
        reviewed_at, file_name, file_size, all counts

  # --- Admin Delete Video ---

  Scenario: Admin delete video
    Given I am logged in as admin
    When I DELETE /admin/v1/videos/42
    Then the response status is 200
    And the video is soft-deleted

  # --- Admin CRUD (Superadmin Only) ---

  Scenario: Create admin (superadmin only)
    Given I am logged in as superadmin (role=1)
    When I POST /admin/v1/admin-users with:
      | username | newadmin   |
      | password | admin123   |
      | role     | 2          |
    Then the response status is 201

  Scenario: Create admin as regular admin (forbidden)
    Given I am logged in as admin (role=2)
    When I POST /admin/v1/admin-users
    Then the response status is 403
    And the error code is "FORBIDDEN"
    And the error message is "superadmin required"

  Scenario: Delete admin (cannot delete self)
    Given I am logged in as superadmin (id=1)
    When I DELETE /admin/v1/admin-users/1
    Then the response status is 400
    And the error message is "cannot delete yourself"

  Scenario: Change another admin's password (superadmin only)
    Given I am logged in as admin (role=2, id=2)
    When I PUT /admin/v1/admin-users/3/password
    Then the response status is 403
    And the error code is "FORBIDDEN"
    And the error message is "cannot change other admin's password"

  # --- Customer Management ---

  Scenario: List customers with filters
    Given I am logged in as admin
    When I GET /admin/v1/customers?name=alice&is_vip=true
    Then only matching customer records are returned

  Scenario: Reset customer password
    Given I am logged in as admin
    When I PUT /admin/v1/customers/42/password with:
      | new_password | resetpass123 |
    Then the response status is 200
    And customer 42 can login with "resetpass123"
```

---

## Feature 9: Rate Limiting

```gherkin
Feature: Rate Limiting
  Redis sliding window rate limiter. Different limits per route group.
  auth: 20 req/min by IP, pub: 120 req/min by IP,
  user: 200 req/min by customer_id, admin: 200 req/min by admin_id.

  Scenario: Normal request within limit
    Given the auth rate limit is 20 requests per minute
    And I have made 10 requests this minute
    When I POST /api/v1/auth/login
    Then the response status is 200 (or normal error)
    And the header X-RateLimit-Limit is "20"
    And the header X-RateLimit-Remaining is "9"

  Scenario: Exceed auth rate limit
    Given the auth rate limit is 20 requests per minute
    And I have made 21 requests from the same IP in the last minute
    When I POST /api/v1/auth/login
    Then the response status is 429
    And the error message is "too many requests, please try again later"
    And the header Retry-After is present

  Scenario: User rate limit keyed by customer_id
    Given the user rate limit is 200 requests per minute
    And customer "alice" (id=1) has made 201 requests this minute
    When alice makes another request to /api/v1/videos/me
    Then the response status is 429
    But customer "bob" can still make requests (different key)

  Scenario: Public rate limit keyed by IP
    Given the public rate limit is 120 requests per minute
    And IP 10.0.0.1 has made 121 requests this minute
    When another request comes from 10.0.0.1 to /api/v1/videos
    Then the response status is 429
    But IP 10.0.0.2 can still make requests

  Scenario: Rate limit window slides
    Given I exceeded the limit at minute 0
    And the window is 1 minute
    When 61 seconds have passed
    Then new requests are allowed again
```

---

## Feature 10: User Profile

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

---

## Feature 11: Admin Reports

```gherkin
Feature: Admin Reports
  Revenue reports, daily video statistics, and video summary.
  All admin-only endpoints under /admin/v1/.

  Scenario: Revenue report by date range
    Given I am logged in as admin
    When I GET /admin/v1/reports/revenue?start_date=2026-03-01&end_date=2026-03-19
    Then the response status is 200
    And each item contains date, claim_count, total_diamonds

  Scenario: Video daily report
    Given I am logged in as admin
    When I GET /admin/v1/reports/videos/daily?start_date=2026-03-01&end_date=2026-03-19
    Then the response status is 200
    And each item contains date, upload_count, total_clicks, total_valid_clicks, total_likes

  Scenario: Video summary report
    Given I am logged in as admin
    When I GET /admin/v1/reports/videos/summary?start_date=2026-03-01&end_date=2026-03-19
    Then the response status is 200
    And the response contains total_videos, approved_count, pending_count,
        rejected_count, total_clicks, total_valid_clicks, total_likes
```

---

## Appendix A: Unified Response Format

All API responses follow this envelope:

```json
// Success
{ "code": 0, "message": "ok", "data": { ... } }

// Paginated Success
{ "code": 0, "message": "ok", "data": { "items": [...], "total": 100, "page": 1, "pageSize": 20 } }

// Error
{ "code": 401, "message": "invalid username or password", "data": null }
```

## Appendix B: Error Code Reference

| Error Code               | HTTP Status | Description                        |
|--------------------------|-------------|------------------------------------|
| AUTH_INVALID_CREDENTIALS | 401         | Wrong username or password         |
| AUTH_TOKEN_EXPIRED       | 401         | JWT expired                        |
| AUTH_TOKEN_INVALID       | 401         | Invalid/tampered JWT               |
| AUTH_USERNAME_EXISTS     | 409         | Username already taken             |
| AUTH_PASSWORD_MISMATCH   | 400         | Old password incorrect             |
| VIDEO_NOT_FOUND          | 404         | Video does not exist               |
| VIDEO_NOT_APPROVED       | 403         | Non-owner accessing unapproved     |
| VIDEO_ALREADY_LIKED      | 409         | Already liked (if app-layer check) |
| USER_ALREADY_FOLLOWING   | 409         | Already following                  |
| USER_CANNOT_FOLLOW_SELF  | 400         | Self-follow attempted              |
| USER_NOT_FOLLOWING       | 400         | Unfollow when not following         |
| TASK_NOT_CLAIMABLE       | 400         | Task not in completed state        |
| ALREADY_SIGNED_IN        | 409         | Already signed in today            |
| RATE_LIMITED             | 429         | Rate limit exceeded                |
| UNAUTHORIZED             | 401         | Missing/invalid auth               |
| FORBIDDEN                | 403         | Insufficient permissions           |
| NOT_FOUND                | 404         | Resource not found                 |
| BAD_REQUEST              | 400         | Invalid request parameters         |

## Appendix C: Video Status Lifecycle

```
0 = failed
1 = pending_encode
2 = encoding
3 = pending_review  <-- created on confirm-upload
4 = approved        <-- admin review
5 = rejected        <-- admin review
```

## Appendix D: Task Status Lifecycle

```
1 = in_progress  <-- auto-created on list
2 = completed    <-- auto-set when current_count >= target_count
3 = claimed      <-- set on successful claim
4 = expired      <-- not used in current implementation
```

## Appendix E: Rate Limit Groups

| Group | Limit       | Key       | Applies To                     |
|-------|-------------|-----------|--------------------------------|
| auth  | 20/min      | IP        | register, login, refresh       |
| pub   | 120/min     | IP        | public video/user listing      |
| user  | 200/min     | customer_id | authenticated user actions   |
| admin | 200/min     | admin_id  | all admin endpoints            |
