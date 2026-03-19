
-- Report: ReportRevenue filters by task_status=3 + claimed_at range
CREATE INDEX IF NOT EXISTS idx_task_progress_claimed ON task_progress(claimed_at)
    WHERE task_status = 3;

-- Report: ReportVideosDaily/ReportVideoSummary filters by created_at range
CREATE INDEX IF NOT EXISTS idx_videos_created ON videos(created_at)
    WHERE deleted_at IS NULL;

-- FK index: CASCADE check on customer delete
CREATE INDEX IF NOT EXISTS idx_video_likes_customer ON video_likes(customer_id);

-- FK index: CASCADE check on customer delete (fastest-growing table)
CREATE INDEX IF NOT EXISTS idx_play_records_customer ON video_play_records(customer_id);

