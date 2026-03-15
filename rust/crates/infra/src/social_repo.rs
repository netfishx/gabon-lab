use sqlx::PgPool;

use gabon_shared::error::AppError;
use gabon_shared::traits::FollowRow;

/// Real PostgreSQL implementation of SocialRepo.
pub struct PgSocialRepo<'a> {
    pub pool: &'a PgPool,
}

impl gabon_shared::traits::SocialRepo for PgSocialRepo<'_> {
    async fn follow(&self, follower_id: i64, followed_id: i64) -> Result<bool, AppError> {
        let result = sqlx::query(
            r#"INSERT INTO user_follows (follower_id, followed_id)
               VALUES ($1, $2)
               ON CONFLICT DO NOTHING"#,
        )
        .bind(follower_id)
        .bind(followed_id)
        .execute(self.pool)
        .await?;

        Ok(result.rows_affected() > 0)
    }

    async fn unfollow(&self, follower_id: i64, followed_id: i64) -> Result<bool, AppError> {
        let result = sqlx::query(
            "DELETE FROM user_follows WHERE follower_id = $1 AND followed_id = $2",
        )
        .bind(follower_id)
        .bind(followed_id)
        .execute(self.pool)
        .await?;

        Ok(result.rows_affected() > 0)
    }

    async fn get_following(&self, customer_id: i64) -> Result<Vec<FollowRow>, AppError> {
        let rows = sqlx::query_as::<_, PgFollowRow>(
            r#"SELECT c.id, c.name, c.avatar_url, c.is_vip
               FROM user_follows f JOIN customers c ON f.followed_id = c.id
               WHERE f.follower_id = $1 AND c.deleted_at IS NULL
               ORDER BY f.created_at DESC"#,
        )
        .bind(customer_id)
        .fetch_all(self.pool)
        .await?;

        Ok(rows.into_iter().map(Into::into).collect())
    }

    async fn get_followers(&self, customer_id: i64) -> Result<Vec<FollowRow>, AppError> {
        let rows = sqlx::query_as::<_, PgFollowRow>(
            r#"SELECT c.id, c.name, c.avatar_url, c.is_vip
               FROM user_follows f JOIN customers c ON f.follower_id = c.id
               WHERE f.followed_id = $1 AND c.deleted_at IS NULL
               ORDER BY f.created_at DESC"#,
        )
        .bind(customer_id)
        .fetch_all(self.pool)
        .await?;

        Ok(rows.into_iter().map(Into::into).collect())
    }
}

#[derive(sqlx::FromRow)]
struct PgFollowRow {
    id: i64,
    name: Option<String>,
    avatar_url: Option<String>,
    is_vip: bool,
}

impl From<PgFollowRow> for FollowRow {
    fn from(r: PgFollowRow) -> Self {
        Self {
            id: r.id,
            name: r.name,
            avatar_url: r.avatar_url,
            is_vip: r.is_vip,
        }
    }
}
