-- name: ListActiveTaskDefinitions :many
SELECT id, task_code, task_name, description, task_type, task_category,
       target_count, reward_diamonds, icon_url, display_order, vip_only
FROM task_definitions
WHERE status = 1
  AND (start_time IS NULL OR start_time <= NOW())
  AND (end_time IS NULL OR end_time > NOW())
ORDER BY display_order ASC, id ASC;
