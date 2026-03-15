use sqlx::PgPool;

use gabon_shared::error::AppError;
use gabon_shared::traits::TaskProgressRow;

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
        sqlx::query(
            r#"INSERT INTO customer_sign_in_records (customer_id, sign_in_date, period_key, diamonds_awarded)
               VALUES ($1, NOW(), $2, $3)"#,
        )
        .bind(customer_id)
        .bind(period_key)
        .bind(diamonds)
        .execute(self.pool)
        .await?;

        sqlx::query(
            "UPDATE customers SET diamond_balance = diamond_balance + $1, updated_at = NOW() WHERE id = $2",
        )
        .bind(diamonds)
        .bind(customer_id)
        .execute(self.pool)
        .await?;

        Ok(diamonds)
    }

    async fn list_tasks(&self, customer_id: i64) -> Result<Vec<TaskProgressRow>, AppError> {
        let rows = sqlx::query_as::<_, PgTaskProgress>(
            r#"SELECT id, customer_id, task_id, current_count, target_count,
                      task_status, reward_diamonds
               FROM task_progress WHERE customer_id = $1
               ORDER BY id DESC"#,
        )
        .bind(customer_id)
        .fetch_all(self.pool)
        .await?;

        Ok(rows.into_iter().map(Into::into).collect())
    }

    async fn get_task_progress(&self, progress_id: i64) -> Result<Option<TaskProgressRow>, AppError> {
        let row = sqlx::query_as::<_, PgTaskProgress>(
            r#"SELECT id, customer_id, task_id, current_count, target_count,
                      task_status, reward_diamonds
               FROM task_progress WHERE id = $1"#,
        )
        .bind(progress_id)
        .fetch_optional(self.pool)
        .await?;

        Ok(row.map(Into::into))
    }

    async fn claim_task(&self, progress_id: i64, customer_id: i64, diamonds: i64) -> Result<(), AppError> {
        sqlx::query(
            r#"UPDATE task_progress
               SET task_status = 3, claimed_at = NOW(), updated_at = NOW()
               WHERE id = $1 AND customer_id = $2 AND task_status = 2"#,
        )
        .bind(progress_id)
        .bind(customer_id)
        .execute(self.pool)
        .await?;

        sqlx::query(
            "UPDATE customers SET diamond_balance = diamond_balance + $1, updated_at = NOW() WHERE id = $2",
        )
        .bind(diamonds)
        .bind(customer_id)
        .execute(self.pool)
        .await?;

        Ok(())
    }
}

#[derive(sqlx::FromRow)]
struct PgTaskProgress {
    id: i64,
    customer_id: i64,
    task_id: i64,
    current_count: i32,
    target_count: i32,
    task_status: i16,
    reward_diamonds: i32,
}

impl From<PgTaskProgress> for TaskProgressRow {
    fn from(r: PgTaskProgress) -> Self {
        Self {
            id: r.id,
            customer_id: r.customer_id,
            task_id: r.task_id,
            current_count: r.current_count,
            target_count: r.target_count,
            task_status: r.task_status,
            reward_diamonds: r.reward_diamonds,
        }
    }
}
