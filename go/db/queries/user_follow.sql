-- name: FollowUser :exec
INSERT INTO user_follows (follower_id, followed_id)
VALUES (@follower_id, @followed_id)
ON CONFLICT (follower_id, followed_id) DO NOTHING;

-- name: UnfollowUser :exec
DELETE FROM user_follows
WHERE follower_id = @follower_id AND followed_id = @followed_id;

-- name: IsFollowing :one
SELECT EXISTS(
    SELECT 1 FROM user_follows
    WHERE follower_id = @follower_id AND followed_id = @followed_id
) AS is_following;

-- name: GetFollowingList :many
SELECT uf.followed_id AS user_id,
       c.username,
       c.name,
       c.avatar_url,
       CASE WHEN sqlc.narg('viewer_id')::bigint IS NOT NULL THEN
           EXISTS(SELECT 1 FROM user_follows WHERE follower_id = sqlc.narg('viewer_id') AND followed_id = uf.followed_id)
       ELSE false END AS viewer_is_following,
       CASE WHEN sqlc.narg('viewer_id')::bigint IS NOT NULL THEN
           EXISTS(SELECT 1 FROM user_follows WHERE follower_id = uf.followed_id AND followed_id = sqlc.narg('viewer_id'))
       ELSE false END AS viewer_is_followed_by
FROM user_follows uf
JOIN customers c ON c.id = uf.followed_id AND c.deleted_at IS NULL
WHERE uf.follower_id = @follower_id
ORDER BY uf.created_at DESC
LIMIT @limit_val OFFSET @offset_val;

-- name: CountFollowing :one
SELECT COUNT(*) FROM user_follows uf
JOIN customers c ON c.id = uf.followed_id AND c.deleted_at IS NULL
WHERE uf.follower_id = @follower_id;

-- name: GetFollowersList :many
SELECT uf.follower_id AS user_id,
       c.username,
       c.name,
       c.avatar_url,
       CASE WHEN sqlc.narg('viewer_id')::bigint IS NOT NULL THEN
           EXISTS(SELECT 1 FROM user_follows WHERE follower_id = sqlc.narg('viewer_id') AND followed_id = uf.follower_id)
       ELSE false END AS viewer_is_following,
       CASE WHEN sqlc.narg('viewer_id')::bigint IS NOT NULL THEN
           EXISTS(SELECT 1 FROM user_follows WHERE follower_id = uf.follower_id AND followed_id = sqlc.narg('viewer_id'))
       ELSE false END AS viewer_is_followed_by
FROM user_follows uf
JOIN customers c ON c.id = uf.follower_id AND c.deleted_at IS NULL
WHERE uf.followed_id = @followed_id
ORDER BY uf.created_at DESC
LIMIT @limit_val OFFSET @offset_val;

-- name: CountFollowers :one
SELECT COUNT(*) FROM user_follows uf
JOIN customers c ON c.id = uf.follower_id AND c.deleted_at IS NULL
WHERE uf.followed_id = @followed_id;
