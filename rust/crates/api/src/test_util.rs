#![cfg(test)]

use std::collections::HashMap;
use std::sync::Mutex;

use gabon_shared::error::AppError;
use gabon_shared::traits::TokenStore;

pub struct MockTokenStore {
    pub refresh_tokens: Mutex<HashMap<String, i64>>,
    pub blacklist: Mutex<Vec<String>>,
}

impl MockTokenStore {
    pub fn new() -> Self {
        Self {
            refresh_tokens: Mutex::new(HashMap::new()),
            blacklist: Mutex::new(vec![]),
        }
    }
}

impl TokenStore for MockTokenStore {
    async fn store_refresh_token(&self, token: &str, user_id: i64, _ttl: u64) -> Result<(), AppError> {
        self.refresh_tokens.lock().unwrap().insert(token.to_string(), user_id);
        Ok(())
    }
    async fn get_refresh_token_user(&self, token: &str) -> Result<Option<i64>, AppError> {
        Ok(self.refresh_tokens.lock().unwrap().get(token).copied())
    }
    async fn delete_refresh_token(&self, token: &str) -> Result<(), AppError> {
        self.refresh_tokens.lock().unwrap().remove(token);
        Ok(())
    }
    async fn rotate_refresh_token(&self, old: &str, new: &str, ttl: u64) -> Result<Option<i64>, AppError> {
        let uid = self.get_refresh_token_user(old).await?;
        if let Some(id) = uid {
            self.delete_refresh_token(old).await?;
            self.store_refresh_token(new, id, ttl).await?;
        }
        Ok(uid)
    }
    async fn revoke_user_sessions(&self, _user_id: i64, _ttl: u64) -> Result<(), AppError> {
        Ok(())
    }
    async fn is_user_revoked(&self, _user_id: i64) -> Result<bool, AppError> {
        Ok(false)
    }
    async fn clear_user_revocation(&self, _user_id: i64) -> Result<(), AppError> {
        Ok(())
    }
    async fn blacklist_access_token(&self, token: &str, _ttl: u64) -> Result<(), AppError> {
        self.blacklist.lock().unwrap().push(token.to_string());
        Ok(())
    }
    async fn is_blacklisted(&self, token: &str) -> Result<bool, AppError> {
        Ok(self.blacklist.lock().unwrap().contains(&token.to_string()))
    }
}
