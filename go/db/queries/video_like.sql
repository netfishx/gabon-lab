-- name: LikeVideo :exec
WITH inserted AS (
    INSERT INTO video_likes (video_id, customer_id)
    VALUES (@video_id, @customer_id)
    ON CONFLICT (video_id, customer_id) DO NOTHING
    RETURNING video_likes.id
)
UPDATE videos SET like_count = like_count + 1, updated_at = NOW()
WHERE videos.id = @video_id AND EXISTS (SELECT 1 FROM inserted);

-- name: UnlikeVideo :exec
WITH deleted AS (
    DELETE FROM video_likes
    WHERE video_likes.video_id = @video_id AND video_likes.customer_id = @customer_id
    RETURNING video_likes.id
)
UPDATE videos SET like_count = GREATEST(like_count - 1, 0), updated_at = NOW()
WHERE videos.id = @video_id AND EXISTS (SELECT 1 FROM deleted);

-- name: IsVideoLiked :one
SELECT EXISTS(
    SELECT 1 FROM video_likes
    WHERE video_id = @video_id AND customer_id = @customer_id
) AS is_liked;
