-- name: CreateVideo :one
INSERT INTO videos (customer_id, title, description, file_name, file_size, file_url,
                    thumbnail_url, mime_type, duration, width, height, status)
VALUES (@customer_id, @title, @description, @file_name, @file_size, @file_url,
        @thumbnail_url, @mime_type, @duration, @width, @height, @status)
RETURNING *;

-- name: GetVideoByID :one
SELECT * FROM videos
WHERE id = @id AND deleted_at IS NULL;

-- name: ListVideos :many
SELECT v.*, c.username AS author_name, c.avatar_url AS author_avatar
FROM videos v
JOIN customers c ON c.id = v.customer_id AND c.deleted_at IS NULL
WHERE v.status = 4 AND v.deleted_at IS NULL
  AND (sqlc.narg('keyword')::text IS NULL OR v.title ILIKE '%' || sqlc.narg('keyword')::text || '%')
ORDER BY v.created_at DESC
LIMIT @limit_val OFFSET @offset_val;

-- name: CountVideos :one
SELECT COUNT(*) FROM videos
WHERE status = 4 AND deleted_at IS NULL
  AND (sqlc.narg('keyword')::text IS NULL OR title ILIKE '%' || sqlc.narg('keyword')::text || '%');

-- name: ListFeaturedVideos :many
SELECT v.*, c.username AS author_name, c.avatar_url AS author_avatar
FROM videos v
JOIN customers c ON c.id = v.customer_id AND c.deleted_at IS NULL
WHERE v.status = 4 AND v.deleted_at IS NULL
  AND (sqlc.narg('keyword')::text IS NULL OR v.title ILIKE '%' || sqlc.narg('keyword')::text || '%')
ORDER BY v.like_count DESC, v.created_at DESC
LIMIT @limit_val OFFSET @offset_val;

-- name: ListVideosByCustomer :many
SELECT v.*, c.username AS author_name, c.avatar_url AS author_avatar
FROM videos v
JOIN customers c ON c.id = v.customer_id AND c.deleted_at IS NULL
WHERE v.customer_id = @customer_id AND v.status = 4 AND v.deleted_at IS NULL
ORDER BY v.created_at DESC
LIMIT @limit_val OFFSET @offset_val;

-- name: CountVideosByCustomer :one
SELECT COUNT(*) FROM videos
WHERE customer_id = @customer_id AND status = 4 AND deleted_at IS NULL;

-- name: ListMyVideos :many
SELECT * FROM videos
WHERE customer_id = @customer_id AND deleted_at IS NULL
  AND (sqlc.narg('status')::smallint IS NULL OR status = sqlc.narg('status'))
ORDER BY created_at DESC
LIMIT @limit_val OFFSET @offset_val;

-- name: CountMyVideos :one
SELECT COUNT(*) FROM videos
WHERE customer_id = @customer_id AND deleted_at IS NULL
  AND (sqlc.narg('status')::smallint IS NULL OR status = sqlc.narg('status'));

-- name: SoftDeleteVideo :exec
UPDATE videos SET deleted_at = NOW(), updated_at = NOW()
WHERE id = @id AND customer_id = @customer_id AND deleted_at IS NULL;

-- name: IncrVideoClicks :exec
UPDATE videos SET total_clicks = total_clicks + 1 WHERE id = @id;

-- name: IncrVideoValidClicks :exec
UPDATE videos SET valid_clicks = valid_clicks + 1 WHERE id = @id;
