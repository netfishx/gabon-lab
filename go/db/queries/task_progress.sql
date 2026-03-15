-- name: UpsertTaskProgress :one
INSERT INTO task_progress (customer_id, task_id, target_count, period_key, reward_diamonds)
VALUES (@customer_id, @task_id, @target_count, @period_key, @reward_diamonds)
ON CONFLICT (customer_id, task_id, period_key) DO NOTHING
RETURNING *;

-- name: GetTaskProgressByCustomerAndPeriod :many
SELECT tp.id, tp.customer_id, tp.task_id, tp.current_count, tp.target_count,
       tp.period_key, tp.task_status, tp.reward_diamonds,
       tp.completed_at, tp.claimed_at, tp.created_at
FROM task_progress tp
WHERE tp.customer_id = @customer_id
  AND tp.period_key = @period_key
ORDER BY tp.task_id ASC;

-- name: GetTaskProgressForUpdate :one
SELECT id, customer_id, task_id, current_count, target_count,
       task_status, reward_diamonds
FROM task_progress
WHERE id = @id AND customer_id = @customer_id
FOR UPDATE;

-- name: ClaimTaskProgress :exec
UPDATE task_progress
SET task_status = 3, claimed_at = NOW(), updated_at = NOW()
WHERE id = @id;

-- name: AddCustomerDiamonds :exec
UPDATE customers
SET diamond_balance = diamond_balance + @amount
WHERE id = @customer_id;

-- name: IncrTaskProgressCount :exec
UPDATE task_progress
SET current_count = current_count + 1,
    task_status = CASE
        WHEN current_count + 1 >= target_count THEN 2
        ELSE task_status
    END,
    completed_at = CASE
        WHEN current_count + 1 >= target_count AND completed_at IS NULL THEN NOW()
        ELSE completed_at
    END,
    updated_at = NOW()
WHERE customer_id = @customer_id
  AND task_id = @task_id
  AND period_key = @period_key
  AND task_status = 1;

-- name: GetWatchTaskIDs :many
SELECT id FROM task_definitions
WHERE status = 1
  AND task_category = 1
  AND (start_time IS NULL OR start_time <= NOW())
  AND (end_time IS NULL OR end_time > NOW());
