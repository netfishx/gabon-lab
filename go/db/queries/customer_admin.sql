-- name: ListCustomers :many
SELECT id, username, name, phone, email, avatar_url, signature,
       is_vip, diamond_balance, last_login_at, created_at
FROM customers
WHERE deleted_at IS NULL
  AND (sqlc.narg('filter_name')::text IS NULL OR LOWER(name) LIKE '%' || LOWER(sqlc.narg('filter_name')::text) || '%')
  AND (sqlc.narg('filter_is_vip')::boolean IS NULL OR is_vip = sqlc.narg('filter_is_vip'))
ORDER BY id DESC
OFFSET @offset_val LIMIT @limit_val;

-- name: CountCustomers :one
SELECT COUNT(*) FROM customers
WHERE deleted_at IS NULL
  AND (sqlc.narg('filter_name')::text IS NULL OR LOWER(name) LIKE '%' || LOWER(sqlc.narg('filter_name')::text) || '%')
  AND (sqlc.narg('filter_is_vip')::boolean IS NULL OR is_vip = sqlc.narg('filter_is_vip'));

-- name: ResetCustomerPassword :exec
UPDATE customers
SET password_hash = @password_hash, updated_at = NOW()
WHERE id = @id AND deleted_at IS NULL;
