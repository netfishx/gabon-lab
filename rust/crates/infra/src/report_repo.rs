use sqlx::PgPool;

use gabon_shared::error::AppError;
use gabon_shared::traits::{RevenueRow, VideoDailyRow, VideoSummaryRow};

pub struct PgReportRepo<'a> {
    pub pool: &'a PgPool,
}

impl gabon_shared::traits::ReportRepo for PgReportRepo<'_> {
    async fn revenue_report(&self, page: i64, page_size: i64) -> Result<(Vec<RevenueRow>, i64), AppError> {
        let offset = (page - 1) * page_size;
        let total: i64 = sqlx::query_scalar(
            "SELECT COUNT(DISTINCT claimed_at::date) FROM task_progress WHERE task_status = 3 AND claimed_at IS NOT NULL",
        )
        .fetch_one(self.pool)
        .await?;

        let rows = sqlx::query_as::<_, PgRevenueRow>(
            r#"SELECT claimed_at::date::text AS date,
                      SUM(reward_diamonds)::bigint AS total_diamonds,
                      COUNT(*)::bigint AS claim_count
               FROM task_progress
               WHERE task_status = 3 AND claimed_at IS NOT NULL
               GROUP BY claimed_at::date
               ORDER BY claimed_at::date DESC
               LIMIT $1 OFFSET $2"#,
        )
        .bind(page_size)
        .bind(offset)
        .fetch_all(self.pool)
        .await?;

        Ok((
            rows.into_iter()
                .map(|r| RevenueRow { date: r.date, total_diamonds: r.total_diamonds, claim_count: r.claim_count })
                .collect(),
            total,
        ))
    }

    async fn video_daily_report(&self, page: i64, page_size: i64) -> Result<(Vec<VideoDailyRow>, i64), AppError> {
        let offset = (page - 1) * page_size;
        let total: i64 = sqlx::query_scalar(
            "SELECT COUNT(DISTINCT created_at::date) FROM videos WHERE deleted_at IS NULL",
        )
        .fetch_one(self.pool)
        .await?;

        let rows = sqlx::query_as::<_, PgVideoDailyRow>(
            r#"SELECT created_at::date::text AS date,
                      COUNT(*)::bigint AS video_count,
                      SUM(total_clicks)::bigint AS total_clicks,
                      SUM(valid_clicks)::bigint AS valid_clicks
               FROM videos WHERE deleted_at IS NULL
               GROUP BY created_at::date
               ORDER BY created_at::date DESC
               LIMIT $1 OFFSET $2"#,
        )
        .bind(page_size)
        .bind(offset)
        .fetch_all(self.pool)
        .await?;

        Ok((
            rows.into_iter()
                .map(|r| VideoDailyRow { date: r.date, video_count: r.video_count, total_clicks: r.total_clicks, valid_clicks: r.valid_clicks })
                .collect(),
            total,
        ))
    }

    async fn video_summary(&self) -> Result<VideoSummaryRow, AppError> {
        let row = sqlx::query_as::<_, PgVideoSummaryRow>(
            r#"SELECT
                 COUNT(*)::bigint AS total_videos,
                 COUNT(*) FILTER (WHERE status = 4)::bigint AS approved_videos,
                 COUNT(*) FILTER (WHERE status = 3)::bigint AS pending_videos,
                 COALESCE(SUM(total_clicks), 0)::bigint AS total_clicks
               FROM videos WHERE deleted_at IS NULL"#,
        )
        .fetch_one(self.pool)
        .await?;

        Ok(VideoSummaryRow {
            total_videos: row.total_videos,
            approved_videos: row.approved_videos,
            pending_videos: row.pending_videos,
            total_clicks: row.total_clicks,
        })
    }
}

#[derive(sqlx::FromRow)]
struct PgRevenueRow { date: String, total_diamonds: i64, claim_count: i64 }

#[derive(sqlx::FromRow)]
struct PgVideoDailyRow { date: String, video_count: i64, total_clicks: i64, valid_clicks: i64 }

#[derive(sqlx::FromRow)]
struct PgVideoSummaryRow { total_videos: i64, approved_videos: i64, pending_videos: i64, total_clicks: i64 }
