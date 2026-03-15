use sqlx::PgPool;

use gabon_shared::error::AppError;
use gabon_shared::traits::{MyVideoRow, VideoDetailRow, VideoListRow};

pub struct PgVideoRepo<'a> {
    pub pool: &'a PgPool,
}

impl gabon_shared::traits::VideoRepo for PgVideoRepo<'_> {
    async fn list_approved(
        &self,
        page: i64,
        page_size: i64,
        keyword: Option<&str>,
    ) -> Result<(Vec<VideoListRow>, i64), AppError> {
        let offset = (page - 1) * page_size;

        let (total, items) = if let Some(kw) = keyword {
            let pattern = format!("%{kw}%");
            let total: i64 = sqlx::query_scalar(
                "SELECT COUNT(*) FROM videos WHERE status = 4 AND deleted_at IS NULL AND title ILIKE $1",
            )
            .bind(&pattern)
            .fetch_one(self.pool)
            .await?;

            let items = sqlx::query_as::<_, VideoListRow>(
                r"SELECT id, customer_id, title, thumbnail_url, duration, like_count, total_clicks
                   FROM videos WHERE status = 4 AND deleted_at IS NULL AND title ILIKE $1
                   ORDER BY id DESC LIMIT $2 OFFSET $3",
            )
            .bind(&pattern)
            .bind(page_size)
            .bind(offset)
            .fetch_all(self.pool)
            .await?;

            (total, items)
        } else {
            let total: i64 = sqlx::query_scalar(
                "SELECT COUNT(*) FROM videos WHERE status = 4 AND deleted_at IS NULL",
            )
            .fetch_one(self.pool)
            .await?;

            let items = sqlx::query_as::<_, VideoListRow>(
                r"SELECT id, customer_id, title, thumbnail_url, duration, like_count, total_clicks
                   FROM videos WHERE status = 4 AND deleted_at IS NULL
                   ORDER BY id DESC LIMIT $1 OFFSET $2",
            )
            .bind(page_size)
            .bind(offset)
            .fetch_all(self.pool)
            .await?;

            (total, items)
        };

        Ok((items, total))
    }

    async fn list_featured(
        &self,
        page: i64,
        page_size: i64,
    ) -> Result<(Vec<VideoListRow>, i64), AppError> {
        let offset = (page - 1) * page_size;

        let total: i64 = sqlx::query_scalar(
            "SELECT COUNT(*) FROM videos WHERE status = 4 AND deleted_at IS NULL",
        )
        .fetch_one(self.pool)
        .await?;

        let items = sqlx::query_as::<_, VideoListRow>(
            r"SELECT id, customer_id, title, thumbnail_url, duration, like_count, total_clicks
               FROM videos WHERE status = 4 AND deleted_at IS NULL
               ORDER BY like_count DESC, id DESC LIMIT $1 OFFSET $2",
        )
        .bind(page_size)
        .bind(offset)
        .fetch_all(self.pool)
        .await?;

        Ok((items, total))
    }

    async fn list_my(&self, customer_id: i64) -> Result<Vec<MyVideoRow>, AppError> {
        let items = sqlx::query_as::<_, MyVideoRow>(
            r"SELECT id, title, thumbnail_url, file_url, duration, status,
                      like_count, total_clicks, valid_clicks
               FROM videos WHERE customer_id = $1 AND deleted_at IS NULL
               ORDER BY id DESC",
        )
        .bind(customer_id)
        .fetch_all(self.pool)
        .await?;

        Ok(items)
    }

    async fn like(&self, video_id: i64, customer_id: i64) -> Result<bool, AppError> {
        let result = sqlx::query(
            r"WITH inserted AS (
                   INSERT INTO video_likes (video_id, customer_id) VALUES ($1, $2)
                   ON CONFLICT DO NOTHING RETURNING video_id
               )
               UPDATE videos SET like_count = like_count + 1, updated_at = NOW()
               WHERE id = (SELECT video_id FROM inserted)",
        )
        .bind(video_id)
        .bind(customer_id)
        .execute(self.pool)
        .await?;
        Ok(result.rows_affected() > 0)
    }

    async fn unlike(&self, video_id: i64, customer_id: i64) -> Result<bool, AppError> {
        let result = sqlx::query(
            r"WITH deleted AS (
                   DELETE FROM video_likes WHERE video_id = $1 AND customer_id = $2
                   RETURNING video_id
               )
               UPDATE videos SET like_count = like_count - 1, updated_at = NOW()
               WHERE id = (SELECT video_id FROM deleted)",
        )
        .bind(video_id)
        .bind(customer_id)
        .execute(self.pool)
        .await?;
        Ok(result.rows_affected() > 0)
    }

    async fn record_play(
        &self,
        video_id: i64,
        customer_id: Option<i64>,
        play_type: i16,
    ) -> Result<i64, AppError> {
        let col = if play_type == 1 { "total_clicks" } else { "valid_clicks" };
        sqlx::query(&format!(
            "UPDATE videos SET {col} = {col} + 1, updated_at = NOW() WHERE id = $1 AND deleted_at IS NULL"
        ))
        .bind(video_id)
        .execute(self.pool)
        .await?;

        let id: i64 = sqlx::query_scalar(
            r"INSERT INTO video_play_records (video_id, customer_id, play_type, ip_address)
               VALUES ($1, $2, $3, NULL) RETURNING id",
        )
        .bind(video_id)
        .bind(customer_id)
        .bind(play_type)
        .fetch_one(self.pool)
        .await?;
        Ok(id)
    }

    async fn get_detail(&self, video_id: i64, viewer_id: Option<i64>) -> Result<Option<VideoDetailRow>, AppError> {
        let row = sqlx::query_as::<_, PgVideoDetailPartial>(
            r"SELECT v.id, v.title, v.description, v.file_url, v.thumbnail_url,
                      v.duration, v.like_count, v.total_clicks,
                      c.id AS author_id, c.name AS author_name,
                      c.avatar_url AS author_avatar_url, c.is_vip AS author_is_vip
               FROM videos v JOIN customers c ON v.customer_id = c.id
               WHERE v.id = $1 AND v.status = 4 AND v.deleted_at IS NULL",
        )
        .bind(video_id)
        .fetch_optional(self.pool)
        .await?;

        let Some(row) = row else { return Ok(None) };

        let (viewer_liked, viewer_followed) = if let Some(uid) = viewer_id {
            let liked: bool = sqlx::query_scalar(
                "SELECT EXISTS(SELECT 1 FROM video_likes WHERE video_id = $1 AND customer_id = $2)",
            )
            .bind(video_id)
            .bind(uid)
            .fetch_one(self.pool)
            .await?;
            let followed: bool = sqlx::query_scalar(
                "SELECT EXISTS(SELECT 1 FROM user_follows WHERE follower_id = $1 AND followed_id = $2)",
            )
            .bind(uid)
            .bind(row.author_id)
            .fetch_one(self.pool)
            .await?;
            (liked, followed)
        } else {
            (false, false)
        };

        Ok(Some(VideoDetailRow {
            id: row.id, title: row.title, description: row.description,
            file_url: row.file_url, thumbnail_url: row.thumbnail_url,
            duration: row.duration, like_count: row.like_count, total_clicks: row.total_clicks,
            author_id: row.author_id, author_name: row.author_name,
            author_avatar_url: row.author_avatar_url, author_is_vip: row.author_is_vip,
            viewer_liked, viewer_followed,
        }))
    }

    async fn delete_video(&self, video_id: i64, customer_id: i64) -> Result<bool, AppError> {
        let result = sqlx::query(
            r"UPDATE videos SET deleted_at = NOW(), updated_at = NOW()
               WHERE id = $1 AND customer_id = $2 AND status = 3 AND deleted_at IS NULL",
        )
        .bind(video_id)
        .bind(customer_id)
        .execute(self.pool)
        .await?;
        Ok(result.rows_affected() > 0)
    }

    async fn list_user_videos(&self, user_id: i64) -> Result<Vec<VideoListRow>, AppError> {
        let items = sqlx::query_as::<_, VideoListRow>(
            r"SELECT id, customer_id, title, thumbnail_url, duration, like_count, total_clicks
               FROM videos WHERE customer_id = $1 AND status = 4 AND deleted_at IS NULL
               ORDER BY id DESC",
        )
        .bind(user_id)
        .fetch_all(self.pool)
        .await?;
        Ok(items)
    }
}

/// Partial row for video detail query (without viewer interaction fields
/// that require separate queries).
#[derive(sqlx::FromRow)]
struct PgVideoDetailPartial {
    id: i64,
    title: Option<String>,
    description: Option<String>,
    file_url: String,
    thumbnail_url: Option<String>,
    duration: Option<i32>,
    like_count: i64,
    total_clicks: i64,
    author_id: i64,
    author_name: Option<String>,
    author_avatar_url: Option<String>,
    author_is_vip: bool,
}
