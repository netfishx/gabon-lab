# Task 008: Video Management Tests

**type**: test
**depends-on**: ["003", "006", "007-auth-impl"]

## BDD Scenarios

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

## Description

Write tests covering all BDD scenarios from Feature 3 (Video Management). This is the Red phase -- all tests must compile but FAIL because no video implementation exists yet.

Test structure:

- **VideoRoutesTest.kt**: Integration tests using Ktor `testApplication`. Set up test data via direct DB inserts (approved videos, pending videos, different owners). Use the auth helper from 007 tests to obtain valid tokens.

Key test concerns:
- Presigned URL: verify 200 with uploadUrl/fileUrl/s3Key fields, s3Key pattern matches `{customerId}/{uuid}.ext`, 401 without auth
- Confirm upload: verify 201, video record created with status=3 (pending_review), correct metadata persisted
- List videos: only approved (status=4) returned for public endpoint, pagination fields (items, total, page, pageSize)
- Search: keyword matches against title (case-insensitive ILIKE)
- Featured: returns approved videos marked as featured
- Detail: public access shows is_liked=false, authenticated user who liked sees is_liked=true, owner can see own unapproved video, non-owner gets 403 for unapproved, 404 for nonexistent
- My videos: returns all statuses for the owner, supports status filter
- User videos: only returns approved videos of target user
- Delete: soft delete sets deleted_at, only owner can delete
- Play click: increments total_clicks, creates play_record with play_type=1
- Valid play: increments valid_clicks, progresses watch task (requires task setup)

## Files

- `kotlin/src/test/kotlin/lab/gabon/route/VideoRoutesTest.kt` -- Integration tests for all video endpoints

## Verification

```bash
cd kotlin && ./gradlew test --tests '*Video*'
```

- All tests compile successfully
- All tests FAIL (Red phase) because VideoService, VideoRepo, and video routes do not exist yet
- Test count matches BDD scenario count (16 scenarios minimum)
