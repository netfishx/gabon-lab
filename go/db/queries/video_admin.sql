-- name: AdminListVideos :many
SELECT v.id, v.customer_id, v.title, v.file_url, v.thumbnail_url,
       v.mime_type, v.status, v.total_clicks, v.valid_clicks, v.like_count,
       v.created_at, c.username AS author_name
FROM videos v
JOIN customers c ON c.id = v.customer_id
WHERE v.deleted_at IS NULL
  AND (sqlc.narg('author_name_filter')::text IS NULL OR LOWER(c.username) LIKE '%' || LOWER(sqlc.narg('author_name_filter')::text) || '%')
  AND (sqlc.narg('status_filter')::smallint IS NULL OR v.status = sqlc.narg('status_filter'))
  AND (sqlc.narg('is_vip_filter')::boolean IS NULL OR c.is_vip = sqlc.narg('is_vip_filter'))
  AND (sqlc.narg('start_date')::timestamptz IS NULL OR v.created_at >= sqlc.narg('start_date'))
  AND (sqlc.narg('end_date')::timestamptz IS NULL OR v.created_at < sqlc.narg('end_date'))
ORDER BY v.id DESC
OFFSET @offset_val LIMIT @limit_val;

-- name: AdminCountVideos :one
SELECT COUNT(*)
FROM videos v
JOIN customers c ON c.id = v.customer_id
WHERE v.deleted_at IS NULL
  AND (sqlc.narg('author_name_filter')::text IS NULL OR LOWER(c.username) LIKE '%' || LOWER(sqlc.narg('author_name_filter')::text) || '%')
  AND (sqlc.narg('status_filter')::smallint IS NULL OR v.status = sqlc.narg('status_filter'))
  AND (sqlc.narg('is_vip_filter')::boolean IS NULL OR c.is_vip = sqlc.narg('is_vip_filter'))
  AND (sqlc.narg('start_date')::timestamptz IS NULL OR v.created_at >= sqlc.narg('start_date'))
  AND (sqlc.narg('end_date')::timestamptz IS NULL OR v.created_at < sqlc.narg('end_date'));

-- name: AdminGetVideoDetail :one
SELECT v.*, c.username AS author_name, c.avatar_url AS author_avatar
FROM videos v
JOIN customers c ON c.id = v.customer_id
WHERE v.id = @id AND v.deleted_at IS NULL;

-- name: ReviewVideo :exec
UPDATE videos
SET status = @status,
    review_notes = @review_notes,
    reviewed_by = @reviewed_by,
    reviewed_at = NOW(),
    updated_at = NOW()
WHERE id = @id AND deleted_at IS NULL;

-- name: AdminDeleteVideo :exec
UPDATE videos
SET deleted_at = NOW(), updated_at = NOW()
WHERE id = @id AND deleted_at IS NULL;
