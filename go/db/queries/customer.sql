-- name: GetCustomerByUsername :one
SELECT * FROM customers
WHERE LOWER(username) = LOWER(@username) AND deleted_at IS NULL;

-- name: GetCustomerByID :one
SELECT * FROM customers
WHERE id = @id AND deleted_at IS NULL;

-- name: CreateCustomer :one
INSERT INTO customers (username, password_hash, name)
VALUES (@username, @password_hash, @username)
RETURNING *;

-- name: UpdateCustomerLastLogin :exec
UPDATE customers SET last_login_at = NOW() WHERE id = @id;

-- name: UpdateCustomerPassword :exec
UPDATE customers SET password_hash = @password_hash, updated_at = NOW()
WHERE id = @id AND deleted_at IS NULL;

-- name: GetCustomerProfile :one
SELECT id, username, name, phone, email, avatar_url, signature,
       is_vip, diamond_balance, last_login_at, created_at
FROM customers WHERE id = @id AND deleted_at IS NULL;

-- name: UpdateCustomerProfile :one
UPDATE customers SET
    name = COALESCE(NULLIF(@name, ''), name),
    phone = COALESCE(NULLIF(@phone, ''), phone),
    email = COALESCE(NULLIF(@email, ''), email),
    avatar_url = COALESCE(NULLIF(@avatar_url, ''), avatar_url),
    signature = COALESCE(NULLIF(@signature, ''), signature),
    updated_at = NOW()
WHERE id = @id AND deleted_at IS NULL
RETURNING *;

-- name: AddDiamonds :exec
UPDATE customers SET diamond_balance = diamond_balance + @amount, updated_at = NOW()
WHERE id = @id;
