use sqlx::PgPool;

use gabon_shared::error::AppError;
use gabon_shared::traits::{TaskProgressRow, TaskStatus};

pub struct PgActivityRepo<'a> {
    pub pool: &'a PgPool,
}

impl gabon_shared::traits::ActivityRepo for PgActivityRepo<'_> {
    async fn has_signed_in_today(&self, customer_id: i64, period_key: &str) -> Result<bool, AppError> {
        let exists: bool = sqlx::query_scalar(
            "SELECT EXISTS(SELECT 1 FROM customer_sign_in_records WHERE customer_id = $1 AND period_key = $2)",
        )
        .bind(customer_id)
        .bind(period_key)
        .fetch_one(self.pool)
        .await?;
        Ok(exists)
    }

    async fn record_sign_in(&self, customer_id: i64, period_key: &str, diamonds: i64) -> Result<i64, AppError> {
        let mut tx = self.pool.begin().await?;

        // Double-check within transaction to prevent TOCTOU race
        let exists: bool = sqlx::query_scalar(
            "SELECT EXISTS(SELECT 1 FROM customer_sign_in_records WHERE customer_id = $1 AND period_key = $2)",
        )
        .bind(customer_id)
        .bind(period_key)
        .fetch_one(&mut *tx)
        .await?;

        if exists {
            return Err(AppError::Conflict("今日已签到".into()));
        }

        sqlx::query(
            r"INSERT INTO customer_sign_in_records (customer_id, period_key, reward_diamonds)
               VALUES ($1, $2, $3)",
        )
        .bind(customer_id)
        .bind(period_key)
        .bind(diamonds)
        .execute(&mut *tx)
        .await?;

        sqlx::query(
            "UPDATE customers SET diamond_balance = diamond_balance + $1, updated_at = NOW() WHERE id = $2",
        )
        .bind(diamonds)
        .bind(customer_id)
        .execute(&mut *tx)
        .await?;

        tx.commit().await?;
        Ok(diamonds)
    }

    async fn list_tasks(&self, customer_id: i64) -> Result<Vec<TaskProgressRow>, AppError> {
        let rows = sqlx::query_as::<_, TaskProgressRow>(
            r"SELECT id, customer_id, task_id, current_count, target_count,
                      task_status, reward_diamonds
               FROM task_progress WHERE customer_id = $1
               ORDER BY id DESC",
        )
        .bind(customer_id)
        .fetch_all(self.pool)
        .await?;

        Ok(rows)
    }

    async fn get_task_progress(&self, progress_id: i64) -> Result<Option<TaskProgressRow>, AppError> {
        let row = sqlx::query_as::<_, TaskProgressRow>(
            r"SELECT id, customer_id, task_id, current_count, target_count,
                      task_status, reward_diamonds
               FROM task_progress WHERE id = $1",
        )
        .bind(progress_id)
        .fetch_optional(self.pool)
        .await?;

        Ok(row)
    }

    async fn claim_task(&self, progress_id: i64, customer_id: i64, diamonds: i64) -> Result<(), AppError> {
        let mut tx = self.pool.begin().await?;

        // Conditional UPDATE acts as row lock — only one concurrent request can match Completed
        let result = sqlx::query(
            r"UPDATE task_progress
               SET task_status = $3, claimed_at = NOW(), updated_at = NOW()
               WHERE id = $1 AND customer_id = $2 AND task_status = $4",
        )
        .bind(progress_id)
        .bind(customer_id)
        .bind(TaskStatus::Claimed as i16)
        .bind(TaskStatus::Completed as i16)
        .execute(&mut *tx)
        .await?;

        if result.rows_affected() == 0 {
            return Err(AppError::BadRequest("任务不可领取".into()));
        }

        sqlx::query(
            "UPDATE customers SET diamond_balance = diamond_balance + $1, updated_at = NOW() WHERE id = $2",
        )
        .bind(diamonds)
        .bind(customer_id)
        .execute(&mut *tx)
        .await?;

        tx.commit().await?;
        Ok(())
    }
}
