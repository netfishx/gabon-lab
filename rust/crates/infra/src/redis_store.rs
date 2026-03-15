use deadpool_redis::{Connection, Pool, redis::AsyncCommands};

use gabon_shared::error::AppError;

pub struct RedisTokenStore<'a> {
    pub pool: &'a Pool,
}

impl RedisTokenStore<'_> {
    async fn conn(&self) -> Result<Connection, AppError> {
        self.pool
            .get()
            .await
            .map_err(|e| AppError::Internal(e.to_string()))
    }
}

impl gabon_shared::traits::TokenStore for RedisTokenStore<'_> {
    async fn store_refresh_token(&self, token: &str, user_id: i64, ttl_secs: u64) -> Result<(), AppError> {
        let mut conn = self.conn().await?;
        let key = format!("refresh:{token}");
        conn.set_ex::<_, _, ()>(&key, user_id, ttl_secs).await.map_err(|e| AppError::Internal(e.to_string()))?;
        Ok(())
    }

    async fn get_refresh_token_user(&self, token: &str) -> Result<Option<i64>, AppError> {
        let mut conn = self.conn().await?;
        let key = format!("refresh:{token}");
        let val: Option<i64> = conn.get(&key).await.map_err(|e| AppError::Internal(e.to_string()))?;
        Ok(val)
    }

    async fn delete_refresh_token(&self, token: &str) -> Result<(), AppError> {
        let mut conn = self.conn().await?;
        let key = format!("refresh:{token}");
        conn.del::<_, ()>(&key).await.map_err(|e| AppError::Internal(e.to_string()))?;
        Ok(())
    }

    async fn blacklist_access_token(&self, token: &str, ttl_secs: u64) -> Result<(), AppError> {
        let mut conn = self.conn().await?;
        let key = format!("blacklist:{token}");
        conn.set_ex::<_, _, ()>(&key, 1, ttl_secs).await.map_err(|e| AppError::Internal(e.to_string()))?;
        Ok(())
    }

    async fn is_blacklisted(&self, token: &str) -> Result<bool, AppError> {
        let mut conn = self.conn().await?;
        let key = format!("blacklist:{token}");
        let exists: bool = conn.exists(&key).await.map_err(|e| AppError::Internal(e.to_string()))?;
        Ok(exists)
    }
}
