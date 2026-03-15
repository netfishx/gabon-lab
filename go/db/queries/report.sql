-- name: ReportRevenue :many
SELECT DATE(tp.claimed_at AT TIME ZONE 'Asia/Shanghai') AS report_date,
       COUNT(*) AS claim_count,
       SUM(tp.reward_diamonds) AS total_diamonds
FROM task_progress tp
WHERE tp.task_status = 3
  AND tp.claimed_at >= @start_date
  AND tp.claimed_at < @end_date
GROUP BY report_date
ORDER BY report_date DESC
OFFSET @offset_val LIMIT @limit_val;

-- name: ReportRevenueTotal :one
SELECT COUNT(DISTINCT DATE(tp.claimed_at AT TIME ZONE 'Asia/Shanghai')) AS total_days
FROM task_progress tp
WHERE tp.task_status = 3
  AND tp.claimed_at >= @start_date
  AND tp.claimed_at < @end_date;

-- name: ReportVideosDaily :many
SELECT DATE(v.created_at AT TIME ZONE 'Asia/Shanghai') AS report_date,
       COUNT(*) AS upload_count,
       SUM(v.total_clicks) AS total_clicks,
       SUM(v.valid_clicks) AS total_valid_clicks,
       SUM(v.like_count) AS total_likes
FROM videos v
WHERE v.deleted_at IS NULL
  AND v.created_at >= @start_date
  AND v.created_at < @end_date
GROUP BY report_date
ORDER BY report_date DESC
OFFSET @offset_val LIMIT @limit_val;

-- name: ReportVideosDailyTotal :one
SELECT COUNT(DISTINCT DATE(v.created_at AT TIME ZONE 'Asia/Shanghai')) AS total_days
FROM videos v
WHERE v.deleted_at IS NULL
  AND v.created_at >= @start_date
  AND v.created_at < @end_date;

-- name: ReportVideoSummary :one
SELECT COUNT(*)::bigint AS total_videos,
       COALESCE(SUM(CASE WHEN status = 4 THEN 1 ELSE 0 END), 0)::bigint AS approved_count,
       COALESCE(SUM(CASE WHEN status = 3 THEN 1 ELSE 0 END), 0)::bigint AS pending_count,
       COALESCE(SUM(CASE WHEN status = 5 THEN 1 ELSE 0 END), 0)::bigint AS rejected_count,
       COALESCE(SUM(total_clicks), 0)::bigint AS total_clicks,
       COALESCE(SUM(valid_clicks), 0)::bigint AS total_valid_clicks,
       COALESCE(SUM(like_count), 0)::bigint AS total_likes
FROM videos
WHERE deleted_at IS NULL
  AND created_at >= @start_date
  AND created_at < @end_date;
