-- name: GetAdminByUsername :one
SELECT * FROM admin_users
WHERE LOWER(username) = LOWER(@username) AND deleted_at IS NULL;

-- name: GetAdminByID :one
SELECT * FROM admin_users
WHERE id = @id AND deleted_at IS NULL;

-- name: CreateAdmin :one
INSERT INTO admin_users (username, password_hash, role, full_name, phone, avatar_url, status)
VALUES (@username, @password_hash, @role, @full_name, @phone, @avatar_url, @status)
RETURNING *;

-- name: UpdateAdminLastLogin :exec
UPDATE admin_users SET last_login_at = NOW() WHERE id = @id;

-- name: ListAdmins :many
SELECT id, username, role, full_name, phone, avatar_url, status,
       last_login_at, created_at
FROM admin_users
WHERE deleted_at IS NULL
  AND (sqlc.narg('filter_username')::text IS NULL OR LOWER(username) LIKE '%' || LOWER(sqlc.narg('filter_username')::text) || '%')
  AND (sqlc.narg('filter_role')::smallint IS NULL OR role = sqlc.narg('filter_role'))
  AND (sqlc.narg('filter_status')::smallint IS NULL OR status = sqlc.narg('filter_status'))
ORDER BY id ASC
OFFSET @offset_val LIMIT @limit_val;

-- name: CountAdmins :one
SELECT COUNT(*) FROM admin_users
WHERE deleted_at IS NULL
  AND (sqlc.narg('filter_username')::text IS NULL OR LOWER(username) LIKE '%' || LOWER(sqlc.narg('filter_username')::text) || '%')
  AND (sqlc.narg('filter_role')::smallint IS NULL OR role = sqlc.narg('filter_role'))
  AND (sqlc.narg('filter_status')::smallint IS NULL OR status = sqlc.narg('filter_status'));

-- name: UpdateAdmin :one
UPDATE admin_users
SET full_name = COALESCE(NULLIF(@full_name, ''), full_name),
    phone = COALESCE(NULLIF(@phone, ''), phone),
    avatar_url = COALESCE(NULLIF(@avatar_url, ''), avatar_url),
    role = CASE WHEN @set_role::boolean THEN @role ELSE role END,
    status = CASE WHEN @set_status::boolean THEN @status ELSE status END,
    updated_at = NOW()
WHERE id = @id AND deleted_at IS NULL
RETURNING *;

-- name: UpdateAdminPassword :exec
UPDATE admin_users
SET password_hash = @password_hash, updated_at = NOW()
WHERE id = @id AND deleted_at IS NULL;

-- name: SoftDeleteAdmin :exec
UPDATE admin_users
SET deleted_at = NOW(), updated_at = NOW()
WHERE id = @id AND deleted_at IS NULL;
