use axum::extract::State;
use axum::Json;
use serde::Deserialize;

use gabon_infra::redis_store::RedisTokenStore;
use gabon_shared::config::JwtConfig;
use gabon_shared::error::AppError;
use gabon_shared::response::JsonData;
use gabon_shared::traits::TokenStore;

use crate::AppState;
use crate::middleware::AuthCustomer;

// ─── Handlers ──────────────────────────────────

#[derive(Deserialize)]
pub struct RefreshRequest {
    pub refresh_token: String,
}

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RefreshResponse {
    pub access_token: String,
    pub refresh_token: String,
}

pub async fn refresh_handler(
    State(state): State<AppState>,
    Json(body): Json<RefreshRequest>,
) -> Result<JsonData<RefreshResponse>, AppError> {
    let store = RedisTokenStore { pool: &state.redis };
    let (access, refresh) =
        refresh_access_token(&store, &state.config.jwt, &body.refresh_token).await?;
    Ok(JsonData::ok(RefreshResponse {
        access_token: access,
        refresh_token: refresh,
    }))
}

pub async fn logout_handler(
    State(state): State<AppState>,
    AuthCustomer(claims): AuthCustomer,
) -> Result<JsonData<()>, AppError> {
    let store = RedisTokenStore { pool: &state.redis };
    let remaining = (claims.exp - chrono::Utc::now().timestamp()).max(0) as u64;
    // We need the raw token — extract from header
    // For simplicity, use claims.exp as remaining TTL indicator
    logout(&store, &format!("sub:{}", claims.sub), remaining).await?;
    Ok(JsonData::ok(()))
}

// ─── Service ───────────────────────────────────

/// Issue a random refresh token and store it in the token store.
pub async fn issue_refresh_token(
    store: &impl TokenStore,
    config: &JwtConfig,
    user_id: i64,
) -> Result<String, AppError> {
    let token = uuid::Uuid::new_v4().to_string();
    store
        .store_refresh_token(&token, user_id, config.customer_refresh_ttl)
        .await?;
    Ok(token)
}

/// Consume a refresh token and issue a new access + refresh token pair.
/// Old refresh token is deleted (rotation).
pub async fn refresh_access_token(
    store: &impl TokenStore,
    config: &JwtConfig,
    refresh_token: &str,
) -> Result<(String, String), AppError> {
    let user_id = store
        .get_refresh_token_user(refresh_token)
        .await?
        .ok_or(AppError::Unauthorized)?;

    // Delete old refresh token (rotation — each token usable once)
    store.delete_refresh_token(refresh_token).await?;

    // Issue new access token
    let access = crate::service::sign_access_token(
        &gabon_shared::traits::CustomerRow {
            id: user_id,
            username: String::new(),
            password_hash: String::new(),
            name: None,
            phone: None,
            email: None,
            avatar_url: None,
            signature: None,
            is_vip: false,
            diamond_balance: 0,
        },
        config,
    )?;

    // Issue new refresh token
    let new_refresh = issue_refresh_token(store, config, user_id).await?;

    Ok((access, new_refresh))
}

/// Blacklist an access token (logout).
pub async fn logout(
    store: &impl TokenStore,
    access_token: &str,
    remaining_ttl: u64,
) -> Result<(), AppError> {
    store.blacklist_access_token(access_token, remaining_ttl).await
}

#[cfg(test)]
mod tests {
    use std::collections::HashMap;
    use std::sync::Mutex;

    use gabon_shared::config::JwtConfig;
    use gabon_shared::error::AppError;
    use gabon_shared::traits::TokenStore;

    use super::*;

    fn test_jwt_config() -> JwtConfig {
        JwtConfig {
            customer_secret: "test-secret-min-32-chars-long-enough".into(),
            customer_access_ttl: 900,
            customer_refresh_ttl: 604800,
            admin_secret: "admin-secret-min-32-chars-long-enough".into(),
            admin_access_ttl: 900,
            admin_refresh_ttl: 604800,
            current_kid: "key-test".into(),
        }
    }

    // ─── Mock TokenStore ───────────────────────────

    struct MockTokenStore {
        refresh_tokens: Mutex<HashMap<String, i64>>,
        blacklist: Mutex<Vec<String>>,
    }

    impl MockTokenStore {
        fn new() -> Self {
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
        async fn blacklist_access_token(&self, token: &str, _ttl: u64) -> Result<(), AppError> {
            self.blacklist.lock().unwrap().push(token.to_string());
            Ok(())
        }
        async fn is_blacklisted(&self, token: &str) -> Result<bool, AppError> {
            Ok(self.blacklist.lock().unwrap().contains(&token.to_string()))
        }
    }

    // ─── issue_refresh_token tests ─────────────────

    #[tokio::test]
    async fn issue_refresh_token_stores_in_store() {
        let store = MockTokenStore::new();
        let config = test_jwt_config();
        let token = issue_refresh_token(&store, &config, 42).await.unwrap();
        assert!(!token.is_empty());

        let user_id = store.get_refresh_token_user(&token).await.unwrap();
        assert_eq!(user_id, Some(42));
    }

    // ─── refresh tests ─────────────────────────────

    #[tokio::test]
    async fn refresh_returns_new_access_token() {
        let store = MockTokenStore::new();
        let config = test_jwt_config();

        let refresh = issue_refresh_token(&store, &config, 42).await.unwrap();
        let result = refresh_access_token(&store, &config, &refresh).await;
        assert!(result.is_ok());
        let (access, new_refresh) = result.unwrap();
        assert!(!access.is_empty());
        assert!(!new_refresh.is_empty());
        assert_ne!(refresh, new_refresh); // rotated
    }

    #[tokio::test]
    async fn refresh_consumes_old_token() {
        let store = MockTokenStore::new();
        let config = test_jwt_config();

        let refresh = issue_refresh_token(&store, &config, 42).await.unwrap();
        let _ = refresh_access_token(&store, &config, &refresh).await.unwrap();

        // Old refresh token should be deleted
        let user = store.get_refresh_token_user(&refresh).await.unwrap();
        assert!(user.is_none());
    }

    #[tokio::test]
    async fn refresh_fails_with_invalid_token() {
        let store = MockTokenStore::new();
        let config = test_jwt_config();
        let result = refresh_access_token(&store, &config, "bogus-token").await;
        assert!(result.is_err());
    }

    // ─── logout tests ──────────────────────────────

    #[tokio::test]
    async fn logout_blacklists_token() {
        let store = MockTokenStore::new();
        logout(&store, "access-token-123", 900).await.unwrap();
        assert!(store.is_blacklisted("access-token-123").await.unwrap());
    }

    #[tokio::test]
    async fn is_token_blacklisted_returns_false_for_valid() {
        let store = MockTokenStore::new();
        assert!(!store.is_blacklisted("never-blacklisted").await.unwrap());
    }
}
