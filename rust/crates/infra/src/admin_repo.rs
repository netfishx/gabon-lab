use sqlx::PgPool;

use gabon_shared::error::AppError;
use gabon_shared::traits::{AdminCustomerRow, AdminRow, AdminVideoDetailRow, AdminVideoRow};

pub struct PgAdminRepo<'a> {
    pub pool: &'a PgPool,
}

impl gabon_shared::traits::AdminRepo for PgAdminRepo<'_> {
    async fn find_admin_by_username(&self, username: &str) -> Result<Option<AdminRow>, AppError> {
        let row = sqlx::query_as::<_, AdminRow>(
            "SELECT id, username, password_hash, role, full_name, status FROM admin_users WHERE LOWER(username) = LOWER($1) AND deleted_at IS NULL",
        )
        .bind(username)
        .fetch_optional(self.pool)
        .await?;
        Ok(row)
    }

    async fn find_admin_by_id(&self, id: i64) -> Result<Option<AdminRow>, AppError> {
        let row = sqlx::query_as::<_, AdminRow>(
            "SELECT id, username, password_hash, role, full_name, status FROM admin_users WHERE id = $1 AND deleted_at IS NULL",
        )
        .bind(id)
        .fetch_optional(self.pool)
        .await?;
        Ok(row)
    }

    async fn update_admin_last_login(&self, id: i64) -> Result<(), AppError> {
        sqlx::query("UPDATE admin_users SET last_login_at = NOW(), updated_at = NOW() WHERE id = $1")
            .bind(id)
            .execute(self.pool)
            .await?;
        Ok(())
    }

    async fn review_video(&self, video_id: i64, admin_id: i64, status: i16, notes: Option<&str>) -> Result<bool, AppError> {
        let result = sqlx::query(
            r"UPDATE videos
               SET status = $1, reviewed_by = $2, reviewed_at = NOW(),
                   review_notes = $3, updated_at = NOW()
               WHERE id = $4 AND status = 3 AND deleted_at IS NULL",
        )
        .bind(status)
        .bind(admin_id)
        .bind(notes)
        .bind(video_id)
        .execute(self.pool)
        .await?;
        Ok(result.rows_affected() > 0)
    }

    async fn list_videos(&self, page: i64, page_size: i64, status: Option<i16>) -> Result<(Vec<AdminVideoRow>, i64), AppError> {
        let offset = (page - 1) * page_size;
        let (total, items) = if let Some(s) = status {
            let total: i64 = sqlx::query_scalar(
                "SELECT COUNT(*) FROM videos WHERE status = $1 AND deleted_at IS NULL",
            )
            .bind(s)
            .fetch_one(self.pool)
            .await?;
            let items = sqlx::query_as::<_, AdminVideoRow>(
                r"SELECT id, title,
                          (SELECT name FROM customers WHERE id = videos.customer_id) AS uploader_name,
                          status, like_count, total_clicks
                   FROM videos WHERE status = $1 AND deleted_at IS NULL
                   ORDER BY id DESC LIMIT $2 OFFSET $3",
            )
            .bind(s)
            .bind(page_size)
            .bind(offset)
            .fetch_all(self.pool)
            .await?;
            (total, items)
        } else {
            let total: i64 = sqlx::query_scalar(
                "SELECT COUNT(*) FROM videos WHERE deleted_at IS NULL",
            )
            .fetch_one(self.pool)
            .await?;
            let items = sqlx::query_as::<_, AdminVideoRow>(
                r"SELECT id, title,
                          (SELECT name FROM customers WHERE id = videos.customer_id) AS uploader_name,
                          status, like_count, total_clicks
                   FROM videos WHERE deleted_at IS NULL
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

    async fn delete_video(&self, video_id: i64) -> Result<bool, AppError> {
        let result = sqlx::query(
            "UPDATE videos SET deleted_at = NOW(), updated_at = NOW() WHERE id = $1 AND deleted_at IS NULL",
        )
        .bind(video_id)
        .execute(self.pool)
        .await?;
        Ok(result.rows_affected() > 0)
    }

    async fn list_customers(&self, page: i64, page_size: i64, name: Option<&str>) -> Result<(Vec<AdminCustomerRow>, i64), AppError> {
        let offset = (page - 1) * page_size;
        let (total, items) = if let Some(n) = name {
            let pattern = format!("%{n}%");
            let total: i64 = sqlx::query_scalar(
                "SELECT COUNT(*) FROM customers WHERE deleted_at IS NULL AND name ILIKE $1",
            )
            .bind(&pattern)
            .fetch_one(self.pool)
            .await?;
            let items = sqlx::query_as::<_, AdminCustomerRow>(
                r"SELECT id, username, name, is_vip, diamond_balance
                   FROM customers WHERE deleted_at IS NULL AND name ILIKE $1
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
                "SELECT COUNT(*) FROM customers WHERE deleted_at IS NULL",
            )
            .fetch_one(self.pool)
            .await?;
            let items = sqlx::query_as::<_, AdminCustomerRow>(
                r"SELECT id, username, name, is_vip, diamond_balance
                   FROM customers WHERE deleted_at IS NULL
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

    async fn change_customer_password(&self, customer_id: i64, new_hash: &str) -> Result<bool, AppError> {
        let result = sqlx::query(
            "UPDATE customers SET password_hash = $1, updated_at = NOW() WHERE id = $2 AND deleted_at IS NULL",
        )
        .bind(new_hash)
        .bind(customer_id)
        .execute(self.pool)
        .await?;
        Ok(result.rows_affected() > 0)
    }

    async fn list_admins(&self, page: i64, page_size: i64) -> Result<(Vec<AdminRow>, i64), AppError> {
        let offset = (page - 1) * page_size;
        let total: i64 = sqlx::query_scalar(
            "SELECT COUNT(*) FROM admin_users WHERE deleted_at IS NULL",
        )
        .fetch_one(self.pool)
        .await?;
        let items = sqlx::query_as::<_, AdminRow>(
            r"SELECT id, username, password_hash, role, full_name, status
               FROM admin_users WHERE deleted_at IS NULL
               ORDER BY id DESC LIMIT $1 OFFSET $2",
        )
        .bind(page_size)
        .bind(offset)
        .fetch_all(self.pool)
        .await?;
        Ok((items, total))
    }

    async fn create_admin(&self, username: &str, password_hash: &str, role: i16, full_name: Option<&str>) -> Result<AdminRow, AppError> {
        let row = sqlx::query_as::<_, AdminRow>(
            r"INSERT INTO admin_users (username, password_hash, role, full_name)
               VALUES ($1, $2, $3, $4)
               RETURNING id, username, password_hash, role, full_name, status",
        )
        .bind(username)
        .bind(password_hash)
        .bind(role)
        .bind(full_name)
        .fetch_one(self.pool)
        .await?;
        Ok(row)
    }

    async fn delete_admin(&self, id: i64) -> Result<bool, AppError> {
        let result = sqlx::query(
            "UPDATE admin_users SET deleted_at = NOW(), updated_at = NOW() WHERE id = $1 AND deleted_at IS NULL",
        )
        .bind(id)
        .execute(self.pool)
        .await?;
        Ok(result.rows_affected() > 0)
    }

    async fn update_admin(&self, id: i64, role: i16, full_name: Option<&str>, status: i16) -> Result<bool, AppError> {
        let result = sqlx::query(
            r"UPDATE admin_users
               SET role = $1, full_name = $2, status = $3, updated_at = NOW()
               WHERE id = $4 AND deleted_at IS NULL",
        )
        .bind(role)
        .bind(full_name)
        .bind(status)
        .bind(id)
        .execute(self.pool)
        .await?;
        Ok(result.rows_affected() > 0)
    }

    async fn change_admin_password(&self, id: i64, new_hash: &str) -> Result<bool, AppError> {
        let result = sqlx::query(
            "UPDATE admin_users SET password_hash = $1, updated_at = NOW() WHERE id = $2 AND deleted_at IS NULL",
        )
        .bind(new_hash)
        .bind(id)
        .execute(self.pool)
        .await?;
        Ok(result.rows_affected() > 0)
    }

    async fn get_video_detail(&self, video_id: i64) -> Result<Option<AdminVideoDetailRow>, AppError> {
        let row = sqlx::query_as::<_, AdminVideoDetailRow>(
            r"SELECT v.id, v.title, v.description, v.file_url, v.thumbnail_url,
                      v.duration, v.status, v.like_count, v.total_clicks, v.valid_clicks,
                      c.id AS uploader_id, c.name AS uploader_name, v.review_notes
               FROM videos v JOIN customers c ON v.customer_id = c.id
               WHERE v.id = $1 AND v.deleted_at IS NULL",
        )
        .bind(video_id)
        .fetch_optional(self.pool)
        .await?;

        Ok(row)
    }
}
