# Task 008: Video Management Implementation

**type**: impl
**depends-on**: ["008-video-test"]

## Description

Implement the full video management system covering BDD Feature 3. This is the Green phase -- make all video tests pass.

### VideoRepo

- `create(customerId, s3Key, fileName, fileSize, mimeType, title, status=3)`: INSERT INTO videos, return generated ID
- `findById(id)`: SELECT by primary key (WHERE deleted_at IS NULL), return Video entity or null
- `findByIdWithLikeStatus(videoId, customerId?)`: SELECT video LEFT JOIN video_likes to determine is_liked
- `listApproved(page, pageSize, keyword?)`: SELECT WHERE status=4 AND deleted_at IS NULL, optional ILIKE on title, ORDER BY created_at DESC, return paginated result
- `listFeatured(page, pageSize)`: SELECT WHERE status=4 AND is_featured=true AND deleted_at IS NULL
- `listByCustomer(customerId, page, pageSize, status?)`: SELECT WHERE customer_id=? AND deleted_at IS NULL, optional status filter
- `listApprovedByUser(userId, page, pageSize)`: SELECT WHERE customer_id=? AND status=4 AND deleted_at IS NULL
- `softDelete(id)`: UPDATE SET deleted_at = NOW() WHERE id = ?
- `incrementTotalClicks(id)`: UPDATE SET total_clicks = total_clicks + 1
- `incrementValidClicks(id)`: UPDATE SET valid_clicks = valid_clicks + 1

### PlayRecordRepo

- `create(videoId, customerId?, playType)`: INSERT INTO play_records

### VideoService

- `presignUpload(customerId, fileName, contentType)`: Generate S3 key as `{customerId}/{uuid}.{ext}`. Use S3 client (from Task 006) to create presigned PUT URL. Return uploadUrl, fileUrl, s3Key.
- `confirmUpload(customerId, s3Key, fileName, fileSize, mimeType, title)`: Create video record with status=3 (pending_review). Return created video.
- `listVideos(page, pageSize, keyword?)`: Delegate to repo listApproved.
- `listFeatured(page, pageSize)`: Delegate to repo.
- `getDetail(videoId, currentCustomerId?)`: Fetch video. If not approved and viewer is not the owner, throw 403 VIDEO_NOT_APPROVED. If not found, throw 404 VIDEO_NOT_FOUND. Include is_liked status.
- `listMyVideos(customerId, page, pageSize, status?)`: Delegate to repo listByCustomer.
- `listUserVideos(userId, page, pageSize)`: Delegate to repo listApprovedByUser.
- `deleteVideo(videoId, customerId)`: Verify ownership. Soft delete.
- `recordPlayClick(videoId)`: Increment total_clicks. Create play_record with play_type=1.
- `recordValidPlay(videoId, customerId?)`: Increment valid_clicks. Create play_record with play_type=2. If authenticated, update watch task progress (delegate to TaskService or inline).

### Video Routes (route/VideoRoutes.kt)

Public (no auth required):
- `GET /api/v1/videos` -- list approved videos with pagination and keyword search
- `GET /api/v1/videos/featured` -- list featured videos
- `GET /api/v1/videos/{id}` -- video detail (public, with optional auth for is_liked)
- `GET /api/v1/users/{userId}/videos` -- list user's approved videos
- `POST /api/v1/videos/{id}/play-click` -- record play click

Authenticated:
- `POST /api/v1/videos/upload-url` -- presign upload URL
- `POST /api/v1/videos/confirm-upload` -- confirm upload
- `GET /api/v1/videos/me` -- list my videos with status filter
- `DELETE /api/v1/videos/{id}` -- delete own video
- `POST /api/v1/videos/{id}/play-valid` -- record valid play

Register all video routes in `plugin/Routing.kt`.

## Files

- `kotlin/src/main/kotlin/lab/gabon/repository/VideoRepo.kt` -- Video data access layer
- `kotlin/src/main/kotlin/lab/gabon/repository/PlayRecordRepo.kt` -- Play record data access
- `kotlin/src/main/kotlin/lab/gabon/service/VideoService.kt` -- Video business logic
- `kotlin/src/main/kotlin/lab/gabon/route/VideoRoutes.kt` -- Video HTTP route handlers
- `kotlin/src/main/kotlin/lab/gabon/plugin/Routing.kt` -- Modified to register video routes

## Verification

```bash
cd kotlin && ./gradlew test --tests '*Video*'
```

- All 16+ video tests PASS (Green phase)
- Presigned URLs contain valid S3 key pattern
- Confirm upload creates video with status=3
- Public listing returns only approved videos
- Owner can see unapproved videos, non-owner gets 403
- Soft delete sets deleted_at
- Play click increments total_clicks and creates play_record
- Valid play increments valid_clicks and progresses watch task
