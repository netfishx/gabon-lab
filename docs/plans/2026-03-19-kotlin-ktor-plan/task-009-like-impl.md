# Task 009: Like System Implementation

**type**: impl
**depends-on**: ["009-like-test"]

## Description

Implement the like/unlike system covering BDD Feature 4. This is the Green phase -- make all like tests pass.

### CRITICAL: CTE-Based Atomic SQL

Like and unlike MUST use raw SQL via `exec()` (or `TransactionManager.current().exec()`), NOT Exposed DSL. The CTE pattern ensures atomicity without application-level locks.

**Like SQL (CTE)**:
```sql
WITH inserted AS (
  INSERT INTO video_likes (video_id, customer_id)
  VALUES (?, ?)
  ON CONFLICT (video_id, customer_id) DO NOTHING
  RETURNING id
)
UPDATE videos SET like_count = like_count + 1
WHERE id = ? AND EXISTS (SELECT 1 FROM inserted)
```

If the user already liked the video, `ON CONFLICT DO NOTHING` produces no rows in `inserted`, so the UPDATE's `WHERE EXISTS` clause fails and like_count stays unchanged. This is idempotent by design.

**Unlike SQL (CTE)**:
```sql
WITH deleted AS (
  DELETE FROM video_likes
  WHERE video_id = ? AND customer_id = ?
  RETURNING id
)
UPDATE videos SET like_count = GREATEST(like_count - 1, 0)
WHERE id = ? AND EXISTS (SELECT 1 FROM deleted)
```

If the user hasn't liked the video, DELETE returns no rows, so the UPDATE doesn't fire. `GREATEST(..., 0)` prevents negative counts.

### VideoRepo Extensions

Add to the existing VideoRepo:
- `likeVideo(videoId, customerId)`: Execute the like CTE raw SQL within a transaction
- `unlikeVideo(videoId, customerId)`: Execute the unlike CTE raw SQL within a transaction

These methods must verify the video exists and is approved (status=4) BEFORE executing the CTE. Throw VIDEO_NOT_FOUND (404) or VIDEO_NOT_APPROVED (403) as appropriate.

### VideoRoutes Extensions

Add to existing video routes:
- `POST /api/v1/videos/{id}/like` -- requires auth. Check video existence and status. Execute like CTE.
- `DELETE /api/v1/videos/{id}/like` -- requires auth. Execute unlike CTE.

### Concurrency Guarantee

The CTE approach handles concurrent likes from different users correctly because:
1. Each INSERT targets a unique (video_id, customer_id) pair -- no conflict between different users
2. The UPDATE `like_count = like_count + 1` uses row-level locking in PostgreSQL (implicit in UPDATE)
3. 10 concurrent likes from 10 different users will each INSERT successfully and each increment like_count, resulting in exactly 10

The CTE also handles duplicate likes from the SAME user: `ON CONFLICT DO NOTHING` means no INSERT, so no UPDATE fires.

## Files

- `kotlin/src/main/kotlin/lab/gabon/repository/VideoRepo.kt` -- Extended with likeVideo/unlikeVideo raw SQL methods
- `kotlin/src/main/kotlin/lab/gabon/route/VideoRoutes.kt` -- Extended with like/unlike route handlers

## Verification

```bash
cd kotlin && ./gradlew test --tests '*Like*'
```

- All 8+ like tests PASS (Green phase)
- Like increments like_count by exactly 1
- Duplicate like is idempotent (like_count unchanged)
- Unlike decrements like_count by 1 (floor 0)
- Unlike not-liked is safe (like_count unchanged)
- Concurrent likes from 10 users: like_count is exactly 10
- Non-approved video returns 403
- Nonexistent video returns 404
- Unauthorized returns 401
