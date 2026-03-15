use chrono::Utc;
use jsonwebtoken::{DecodingKey, EncodingKey, Header, Validation, decode, encode};
use serde::{Deserialize, Serialize};

use gabon_shared::config::JwtConfig;
use gabon_shared::error::AppError;
use gabon_shared::traits::{AuthRepo, CustomerRow, TokenStore};

#[derive(Debug, Serialize, Deserialize)]
pub struct Claims {
    pub sub: i64,
    #[serde(default)]
    pub jti: String,
    pub iss: String,
    pub aud: String,
    pub exp: i64,
    pub iat: i64,
    pub kid: String,
    #[serde(default)]
    pub role: String,
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct CustomerProfile {
    pub id: i64,
    pub username: String,
    pub name: Option<String>,
    pub phone: Option<String>,
    pub email: Option<String>,
    pub avatar_url: Option<String>,
    pub signature: Option<String>,
    pub is_vip: bool,
    pub diamond_balance: i64,
}

impl From<CustomerRow> for CustomerProfile {
    fn from(c: CustomerRow) -> Self {
        Self {
            id: c.id,
            username: c.username,
            name: c.name,
            phone: c.phone,
            email: c.email,
            avatar_url: c.avatar_url,
            signature: c.signature,
            is_vip: c.is_vip,
            diamond_balance: c.diamond_balance,
        }
    }
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AuthResponse {
    pub access_token: String,
    pub refresh_token: String,
    pub profile: CustomerProfile,
}

pub async fn register(
    repo: &impl AuthRepo,
    store: &impl TokenStore,
    jwt_config: &JwtConfig,
    username: &str,
    password: &str,
) -> Result<AuthResponse, AppError> {
    let existing = repo.find_by_username(username).await?;
    if existing.is_some() {
        return Err(AppError::Conflict("用户名已存在".into()));
    }

    let password_hash =
        bcrypt::hash(password, bcrypt::DEFAULT_COST).map_err(|e| AppError::Internal(e.to_string()))?;

    let customer = repo.create(username, &password_hash).await?;
    store.clear_user_revocation(customer.id).await?;
    let access_token = sign_access_token(&customer, jwt_config)?;
    let refresh_token = crate::token::issue_refresh_token(store, jwt_config, customer.id).await?;

    Ok(AuthResponse {
        access_token,
        refresh_token,
        profile: customer.into(),
    })
}

pub async fn login(
    repo: &impl AuthRepo,
    store: &impl TokenStore,
    jwt_config: &JwtConfig,
    username: &str,
    password: &str,
) -> Result<AuthResponse, AppError> {
    let customer = repo
        .find_by_username(username)
        .await?
        .ok_or(AppError::Unauthorized)?;

    let valid =
        bcrypt::verify(password, &customer.password_hash).map_err(|e| AppError::Internal(e.to_string()))?;
    if !valid {
        return Err(AppError::Unauthorized);
    }

    repo.update_last_login(customer.id).await?;
    store.clear_user_revocation(customer.id).await?;
    let access_token = sign_access_token(&customer, jwt_config)?;
    let refresh_token = crate::token::issue_refresh_token(store, jwt_config, customer.id).await?;

    Ok(AuthResponse {
        access_token,
        refresh_token,
        profile: customer.into(),
    })
}

pub async fn get_me(repo: &impl AuthRepo, user_id: i64) -> Result<CustomerProfile, AppError> {
    let customer = repo
        .find_by_id(user_id)
        .await?
        .ok_or(AppError::NotFound("用户不存在".into()))?;
    Ok(customer.into())
}

pub async fn change_password(
    repo: &impl AuthRepo,
    user_id: i64,
    old_password: &str,
    new_password: &str,
) -> Result<(), AppError> {
    let customer = repo
        .find_by_id(user_id)
        .await?
        .ok_or(AppError::NotFound("用户不存在".into()))?;

    let valid =
        bcrypt::verify(old_password, &customer.password_hash).map_err(|e| AppError::Internal(e.to_string()))?;
    if !valid {
        return Err(AppError::Unauthorized);
    }

    let new_hash =
        bcrypt::hash(new_password, bcrypt::DEFAULT_COST).map_err(|e| AppError::Internal(e.to_string()))?;
    repo.change_password(user_id, &new_hash).await
}

pub fn verify_customer_token(token: &str, jwt_config: &JwtConfig) -> Result<Claims, AppError> {
    let mut validation = Validation::default();
    validation.set_issuer(&["gabon-service"]);
    validation.set_audience(&["customer"]);

    let key = DecodingKey::from_secret(jwt_config.customer_secret.as_bytes());
    let data = decode::<Claims>(token, &key, &validation).map_err(|_| AppError::Unauthorized)?;

    Ok(data.claims)
}

pub(crate) fn sign_access_token(customer: &CustomerRow, config: &JwtConfig) -> Result<String, AppError> {
    let now = Utc::now().timestamp();
    let jti = uuid::Uuid::new_v4().to_string();
    let claims = Claims {
        sub: customer.id,
        jti,
        iss: "gabon-service".into(),
        aud: "customer".into(),
        iat: now,
        exp: now + config.customer_access_ttl.cast_signed(),
        kid: config.current_kid.clone(),
        role: "customer".into(),
    };

    let header = Header {
        kid: Some(config.current_kid.clone()),
        ..Header::default()
    };

    encode(&header, &claims, &EncodingKey::from_secret(config.customer_secret.as_bytes()))
        .map_err(|e| AppError::Internal(e.to_string()))
}

#[cfg(test)]
mod tests {
    use std::sync::Mutex;

    use super::*;

    use crate::test_util::MockTokenStore;

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

    fn test_customer_row() -> CustomerRow {
        CustomerRow {
            id: 42,
            username: "testuser".into(),
            password_hash: bcrypt::hash("password123", 4).unwrap(),
            name: Some("Test User".into()),
            phone: None,
            email: None,
            avatar_url: None,
            signature: None,
            is_vip: false,
            diamond_balance: 0,
        }
    }

    // ─── Mock AuthRepo ─────────────────────────────

    struct MockAuthRepo {
        customer: Mutex<Option<CustomerRow>>,
        last_login_updated: Mutex<bool>,
    }

    impl MockAuthRepo {
        fn empty() -> Self {
            Self {
                customer: Mutex::new(None),
                last_login_updated: Mutex::new(false),
            }
        }

        fn with_customer(row: CustomerRow) -> Self {
            Self {
                customer: Mutex::new(Some(row)),
                last_login_updated: Mutex::new(false),
            }
        }
    }

    impl AuthRepo for MockAuthRepo {
        async fn find_by_username(&self, _username: &str) -> Result<Option<CustomerRow>, AppError> {
            Ok(self.customer.lock().unwrap().clone())
        }

        async fn find_by_id(&self, _id: i64) -> Result<Option<CustomerRow>, AppError> {
            Ok(self.customer.lock().unwrap().clone())
        }

        async fn create(&self, username: &str, password_hash: &str) -> Result<CustomerRow, AppError> {
            Ok(CustomerRow {
                id: 99,
                username: username.into(),
                password_hash: password_hash.into(),
                name: Some(username.into()),
                phone: None,
                email: None,
                avatar_url: None,
                signature: None,
                is_vip: false,
                diamond_balance: 0,
            })
        }

        async fn update_last_login(&self, _id: i64) -> Result<(), AppError> {
            *self.last_login_updated.lock().unwrap() = true;
            Ok(())
        }

        async fn change_password(&self, _id: i64, _new_hash: &str) -> Result<(), AppError> {
            Ok(())
        }

        async fn update_avatar(&self, _id: i64, _avatar_url: &str) -> Result<(), AppError> {
            Ok(())
        }

        async fn update_profile(
            &self,
            id: i64,
            name: Option<&str>,
            _phone: Option<&str>,
            _email: Option<&str>,
            _signature: Option<&str>,
        ) -> Result<CustomerRow, AppError> {
            let mut row = self.customer.lock().unwrap().clone()
                .ok_or(AppError::NotFound("not found".into()))?;
            row.id = id;
            if let Some(n) = name { row.name = Some(n.into()); }
            Ok(row)
        }
    }

    // ─── JWT tests ─────────────────────────────────

    #[test]
    fn sign_and_verify_round_trip() {
        let config = test_jwt_config();
        let customer = test_customer_row();
        let token = sign_access_token(&customer, &config).unwrap();
        let claims = verify_customer_token(&token, &config).unwrap();
        assert_eq!(claims.sub, 42);
        assert_eq!(claims.iss, "gabon-service");
        assert_eq!(claims.aud, "customer");
        assert_eq!(claims.kid, "key-test");
    }

    #[test]
    fn verify_rejects_wrong_secret() {
        let config = test_jwt_config();
        let customer = test_customer_row();
        let token = sign_access_token(&customer, &config).unwrap();
        let bad_config = JwtConfig {
            customer_secret: "wrong-secret-also-32-chars-long!!".into(),
            ..config
        };
        assert!(verify_customer_token(&token, &bad_config).is_err());
    }

    #[test]
    fn verify_rejects_expired_token() {
        let config = test_jwt_config();
        let claims = Claims {
            sub: 42,
            jti: uuid::Uuid::new_v4().to_string(),
            iss: "gabon-service".into(),
            aud: "customer".into(),
            iat: Utc::now().timestamp() - 300,
            exp: Utc::now().timestamp() - 120,
            kid: "key-test".into(),
            role: "customer".into(),
        };
        let token = encode(
            &Header::default(),
            &claims,
            &EncodingKey::from_secret(config.customer_secret.as_bytes()),
        )
        .unwrap();
        assert!(verify_customer_token(&token, &config).is_err());
    }

    #[test]
    fn sign_sets_kid_in_header() {
        let config = test_jwt_config();
        let customer = test_customer_row();
        let token = sign_access_token(&customer, &config).unwrap();
        let header = jsonwebtoken::decode_header(&token).unwrap();
        assert_eq!(header.kid, Some("key-test".into()));
    }

    // ─── register tests ────────────────────────────

    #[tokio::test]
    async fn register_succeeds_for_new_user() {
        let repo = MockAuthRepo::empty();
        let store = MockTokenStore::new();
        let config = test_jwt_config();
        let result = register(&repo, &store, &config, "newuser", "password123").await;
        assert!(result.is_ok());
        let resp = result.unwrap();
        assert_eq!(resp.profile.username, "newuser");
        assert!(!resp.access_token.is_empty());
        assert!(!resp.refresh_token.is_empty());
    }

    #[tokio::test]
    async fn register_fails_for_existing_user() {
        let repo = MockAuthRepo::with_customer(test_customer_row());
        let store = MockTokenStore::new();
        let config = test_jwt_config();
        let result = register(&repo, &store, &config, "testuser", "password123").await;
        assert!(result.is_err());
    }

    // ─── login tests ──────────────────────────────

    #[tokio::test]
    async fn login_succeeds_with_correct_password() {
        let repo = MockAuthRepo::with_customer(test_customer_row());
        let store = MockTokenStore::new();
        let config = test_jwt_config();
        let result = login(&repo, &store, &config, "testuser", "password123").await;
        assert!(result.is_ok());
        assert!(*repo.last_login_updated.lock().unwrap());
        assert!(!result.unwrap().refresh_token.is_empty());
    }

    #[tokio::test]
    async fn login_fails_with_wrong_password() {
        let repo = MockAuthRepo::with_customer(test_customer_row());
        let store = MockTokenStore::new();
        let config = test_jwt_config();
        let result = login(&repo, &store, &config, "testuser", "wrongpass").await;
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn login_fails_for_nonexistent_user() {
        let repo = MockAuthRepo::empty();
        let store = MockTokenStore::new();
        let config = test_jwt_config();
        let result = login(&repo, &store, &config, "nobody", "password123").await;
        assert!(result.is_err());
    }

    // ─── get_me tests ──────────────────────────────

    #[tokio::test]
    async fn get_me_returns_profile() {
        let repo = MockAuthRepo::with_customer(test_customer_row());
        let profile = get_me(&repo, 42).await.unwrap();
        assert_eq!(profile.id, 42);
        assert_eq!(profile.username, "testuser");
    }

    #[tokio::test]
    async fn get_me_fails_for_nonexistent_user() {
        let repo = MockAuthRepo::empty();
        assert!(get_me(&repo, 999).await.is_err());
    }

    // ─── change_password tests ─────────────────────

    #[tokio::test]
    async fn change_password_succeeds() {
        let repo = MockAuthRepo::with_customer(test_customer_row());
        let result = change_password(&repo, 42, "password123", "newpass456").await;
        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn change_password_fails_wrong_old_password() {
        let repo = MockAuthRepo::with_customer(test_customer_row());
        let result = change_password(&repo, 42, "wrongold", "newpass456").await;
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn change_password_fails_nonexistent_user() {
        let repo = MockAuthRepo::empty();
        let result = change_password(&repo, 999, "old", "new").await;
        assert!(result.is_err());
    }
}
