-- name: HasSignedInToday :one
SELECT EXISTS(
    SELECT 1 FROM customer_sign_in_records
    WHERE customer_id = @customer_id AND period_key = @period_key
) AS signed;

-- name: RecordSignIn :one
INSERT INTO customer_sign_in_records (customer_id, period_key, reward_diamonds)
VALUES (@customer_id, @period_key, @reward_diamonds)
RETURNING *;
