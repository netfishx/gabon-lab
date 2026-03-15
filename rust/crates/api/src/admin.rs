use axum::extract::{Path, Query, State};
use axum::Json;
use chrono::Utc;
use jsonwebtoken::{DecodingKey, EncodingKey, Header, Validation, decode, encode};
use serde::{Deserialize, Serialize};

use gabon_infra::admin_repo::PgAdminRepo;
use gabon_infra::redis_store::RedisTokenStore;
use gabon_infra::report_repo::PgReportRepo;
use gabon_shared::config::JwtConfig;
use gabon_shared::error::AppError;
use gabon_shared::pagination::Paginated;
use gabon_shared::response::JsonData;
use gabon_shared::traits::{
    AdminCustomerRow, AdminRepo, AdminRow, AdminVideoDetailRow, AdminVideoRow,
    ReportRepo, RevenueRow, TokenStore, VideoDailyRow, VideoSummaryRow,
};

use crate::AppState;
use crate::service::Claims;

// ─── Handlers ──────────────────────────────────

pub async fn login_handler(
    State(state): State<AppState>,
    Json(body): Json<AdminAuthRequest>,
) -> Result<JsonData<AdminLoginResponse>, AppError> {
    let repo = PgAdminRepo { pool: &state.db };
    let store = RedisTokenStore { pool: &state.redis };
    let result = admin_login(&repo, &store, &state.config.jwt, &body.username, &body.password).await?;
    Ok(JsonData::ok(result))
}

#[derive(Deserialize)]
pub struct AdminAuthRequest {
    pub username: String,
    pub password: String,
}

pub async fn me_handler(
    State(state): State<AppState>,
    AuthAdmin(claims): AuthAdmin,
) -> Result<JsonData<AdminProfile>, AppError> {
    let repo = PgAdminRepo { pool: &state.db };
    let result = admin_get_me(&repo, claims.sub).await?;
    Ok(JsonData::ok(result))
}

pub async fn list_videos_handler(
    State(state): State<AppState>,
    AuthAdmin(_claims): AuthAdmin,
    Query(params): Query<AdminListParams>,
) -> Result<JsonData<Paginated<AdminVideoRow>>, AppError> {
    let repo = PgAdminRepo { pool: &state.db };
    let page = params.page.unwrap_or(1);
    let size = params.page_size.unwrap_or(20);
    let (items, total) = repo.list_videos(page, size, params.status).await?;
    Ok(JsonData::ok(Paginated::new(items, page, size, total)))
}

pub async fn delete_video_handler(
    State(state): State<AppState>,
    Path(id): Path<i64>,
    AuthAdmin(_claims): AuthAdmin,
) -> Result<JsonData<()>, AppError> {
    let repo = PgAdminRepo { pool: &state.db };
    admin_delete_video(&repo, id).await?;
    Ok(JsonData::ok(()))
}

pub async fn list_customers_handler(
    State(state): State<AppState>,
    AuthAdmin(_claims): AuthAdmin,
    Query(params): Query<AdminListParams>,
) -> Result<JsonData<Paginated<AdminCustomerRow>>, AppError> {
    let repo = PgAdminRepo { pool: &state.db };
    let page = params.page.unwrap_or(1);
    let size = params.page_size.unwrap_or(20);
    let (items, total) = repo.list_customers(page, size, params.name.as_deref()).await?;
    Ok(JsonData::ok(Paginated::new(items, page, size, total)))
}

pub async fn change_customer_password_handler(
    State(state): State<AppState>,
    Path(id): Path<i64>,
    AuthAdmin(_claims): AuthAdmin,
    Json(body): Json<AdminPasswordRequest>,
) -> Result<JsonData<()>, AppError> {
    let repo = PgAdminRepo { pool: &state.db };
    admin_change_customer_password(&repo, id, &body.new_password).await?;
    Ok(JsonData::ok(()))
}

pub async fn list_admins_handler(
    State(state): State<AppState>,
    AuthAdmin(_claims): AuthAdmin,
    Query(params): Query<AdminListParams>,
) -> Result<JsonData<Paginated<AdminRow>>, AppError> {
    let repo = PgAdminRepo { pool: &state.db };
    let page = params.page.unwrap_or(1);
    let size = params.page_size.unwrap_or(20);
    let (items, total) = repo.list_admins(page, size).await?;
    Ok(JsonData::ok(Paginated::new(items, page, size, total)))
}

pub async fn create_admin_handler(
    State(state): State<AppState>,
    AuthAdmin(claims): AuthAdmin,
    Json(body): Json<CreateAdminRequest>,
) -> Result<JsonData<AdminRow>, AppError> {
    require_superadmin(&claims)?;
    let repo = PgAdminRepo { pool: &state.db };
    let admin = admin_create_user(&repo, &body.username, &body.password, body.role, body.full_name.as_deref()).await?;
    Ok(JsonData::ok(admin))
}

pub async fn revenue_report_handler(
    State(state): State<AppState>,
    AuthAdmin(_claims): AuthAdmin,
    Query(params): Query<AdminListParams>,
) -> Result<JsonData<Paginated<RevenueRow>>, AppError> {
    let repo = PgReportRepo { pool: &state.db };
    let page = params.page.unwrap_or(1);
    let size = params.page_size.unwrap_or(20);
    let (items, total) = repo.revenue_report(page, size).await?;
    Ok(JsonData::ok(Paginated::new(items, page, size, total)))
}

pub async fn video_daily_report_handler(
    State(state): State<AppState>,
    AuthAdmin(_claims): AuthAdmin,
    Query(params): Query<AdminListParams>,
) -> Result<JsonData<Paginated<VideoDailyRow>>, AppError> {
    let repo = PgReportRepo { pool: &state.db };
    let page = params.page.unwrap_or(1);
    let size = params.page_size.unwrap_or(20);
    let (items, total) = repo.video_daily_report(page, size).await?;
    Ok(JsonData::ok(Paginated::new(items, page, size, total)))
}

pub async fn video_summary_handler(
    State(state): State<AppState>,
    AuthAdmin(_claims): AuthAdmin,
) -> Result<JsonData<VideoSummaryRow>, AppError> {
    let repo = PgReportRepo { pool: &state.db };
    let summary = repo.video_summary().await?;
    Ok(JsonData::ok(summary))
}

pub async fn delete_admin_handler(
    State(state): State<AppState>,
    Path(id): Path<i64>,
    AuthAdmin(claims): AuthAdmin,
) -> Result<JsonData<()>, AppError> {
    require_superadmin(&claims)?;
    let repo = PgAdminRepo { pool: &state.db };
    admin_delete_user(&repo, id, claims.sub).await?;
    Ok(JsonData::ok(()))
}

// ─── Admin auth: refresh & logout ─────────────

pub async fn admin_refresh_handler(
    State(state): State<AppState>,
    Json(body): Json<AdminRefreshRequest>,
) -> Result<JsonData<AdminRefreshResponse>, AppError> {
    let repo = PgAdminRepo { pool: &state.db };
    let store = RedisTokenStore { pool: &state.redis };
    let (access, refresh) =
        admin_refresh_access_token(&repo, &store, &state.config.jwt, &body.refresh_token).await?;
    Ok(JsonData::ok(AdminRefreshResponse {
        access_token: access,
        refresh_token: refresh,
    }))
}

pub async fn admin_logout_handler(
    State(state): State<AppState>,
    headers: axum::http::HeaderMap,
    AuthAdmin(claims): AuthAdmin,
) -> Result<JsonData<()>, AppError> {
    let raw_token = headers
        .get("authorization")
        .and_then(|v| v.to_str().ok())
        .and_then(|h| h.strip_prefix("Bearer "))
        .ok_or(AppError::Unauthorized)?;
    let store = RedisTokenStore { pool: &state.redis };
    let remaining = (claims.exp - chrono::Utc::now().timestamp()).max(0).cast_unsigned();
    store.blacklist_access_token(raw_token, remaining).await?;
    store.revoke_user_sessions(claims.sub, state.config.jwt.admin_refresh_ttl).await?;
    Ok(JsonData::ok(()))
}

#[derive(Deserialize)]
pub struct AdminRefreshRequest {
    pub refresh_token: String,
}

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminRefreshResponse {
    pub access_token: String,
    pub refresh_token: String,
}

// ─── Admin user single/update/password ────────

pub async fn get_admin_handler(
    State(state): State<AppState>,
    Path(id): Path<i64>,
    AuthAdmin(_claims): AuthAdmin,
) -> Result<JsonData<AdminRow>, AppError> {
    let repo = PgAdminRepo { pool: &state.db };
    let admin = admin_get_by_id(&repo, id).await?;
    Ok(JsonData::ok(admin))
}

#[derive(Deserialize)]
pub struct UpdateAdminRequest {
    pub role: i16,
    pub full_name: Option<String>,
    pub status: i16,
}

pub async fn update_admin_handler(
    State(state): State<AppState>,
    Path(id): Path<i64>,
    AuthAdmin(claims): AuthAdmin,
    Json(body): Json<UpdateAdminRequest>,
) -> Result<JsonData<()>, AppError> {
    require_superadmin(&claims)?;
    let repo = PgAdminRepo { pool: &state.db };
    admin_update_user(&repo, id, body.role, body.full_name.as_deref(), body.status).await?;
    Ok(JsonData::ok(()))
}

pub async fn change_admin_password_handler(
    State(state): State<AppState>,
    Path(id): Path<i64>,
    AuthAdmin(claims): AuthAdmin,
    Json(body): Json<AdminPasswordRequest>,
) -> Result<JsonData<()>, AppError> {
    if id != claims.sub {
        require_superadmin(&claims)?;
    }
    let repo = PgAdminRepo { pool: &state.db };
    admin_change_password(&repo, id, &body.new_password).await?;
    Ok(JsonData::ok(()))
}

// ─── Admin video detail ───────────────────────

pub async fn get_video_detail_handler(
    State(state): State<AppState>,
    Path(id): Path<i64>,
    AuthAdmin(_claims): AuthAdmin,
) -> Result<JsonData<AdminVideoDetailRow>, AppError> {
    let repo = PgAdminRepo { pool: &state.db };
    let detail = admin_get_video_detail(&repo, id).await?;
    Ok(JsonData::ok(detail))
}

#[derive(Deserialize)]
pub struct AdminListParams {
    pub page: Option<i64>,
    pub page_size: Option<i64>,
    pub status: Option<i16>,
    pub name: Option<String>,
}

#[derive(Deserialize)]
pub struct AdminPasswordRequest {
    pub new_password: String,
}

#[derive(Deserialize)]
pub struct CreateAdminRequest {
    pub username: String,
    pub password: String,
    pub role: i16,
    pub full_name: Option<String>,
}

#[derive(Deserialize)]
pub struct ReviewRequest {
    pub status: i16,
    pub notes: Option<String>,
}

pub async fn review_handler(
    State(state): State<AppState>,
    Path(video_id): Path<i64>,
    AuthAdmin(claims): AuthAdmin,
    Json(body): Json<ReviewRequest>,
) -> Result<JsonData<()>, AppError> {
    let repo = PgAdminRepo { pool: &state.db };
    review_video_action(&repo, video_id, claims.sub, body.status, body.notes.as_deref()).await?;
    Ok(JsonData::ok(()))
}

/// Admin JWT extractor — uses admin secret, verifies iss=gabon-admin.
pub struct AuthAdmin(pub Claims);

impl axum::extract::FromRequestParts<AppState> for AuthAdmin {
    type Rejection = AppError;

    async fn from_request_parts(
        parts: &mut axum::http::request::Parts,
        state: &AppState,
    ) -> Result<Self, Self::Rejection> {
        let token = crate::middleware::extract_bearer(parts).ok_or(AppError::Unauthorized)?;
        let claims = verify_admin_token(token, &state.config.jwt)?;

        let store = RedisTokenStore { pool: &state.redis };
        if store.is_blacklisted(token).await? {
            return Err(AppError::Unauthorized);
        }

        Ok(Self(claims))
    }
}

// ─── Role checks ──────────────────────────────

fn require_superadmin(claims: &Claims) -> Result<(), AppError> {
    if claims.role != "superadmin" {
        return Err(AppError::Forbidden);
    }
    Ok(())
}

// ─── Service ───────────────────────────────────

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminLoginResponse {
    pub access_token: String,
    pub refresh_token: String,
    pub id: i64,
    pub username: String,
    pub full_name: Option<String>,
    pub role: i16,
}

pub async fn admin_login(
    repo: &impl AdminRepo,
    store: &impl TokenStore,
    jwt_config: &JwtConfig,
    username: &str,
    password: &str,
) -> Result<AdminLoginResponse, AppError> {
    let admin = repo
        .find_admin_by_username(username)
        .await?
        .ok_or(AppError::Unauthorized)?;

    if admin.status != 1 {
        return Err(AppError::Forbidden);
    }

    let valid =
        bcrypt::verify(password, &admin.password_hash).map_err(|e| AppError::Internal(e.to_string()))?;
    if !valid {
        return Err(AppError::Unauthorized);
    }

    repo.update_admin_last_login(admin.id).await?;
    store.clear_user_revocation(admin.id).await?;
    let access_token = sign_admin_token(&admin, jwt_config)?;
    let refresh_token = uuid::Uuid::new_v4().to_string();
    store
        .store_refresh_token(&refresh_token, admin.id, jwt_config.admin_refresh_ttl)
        .await?;

    Ok(AdminLoginResponse {
        access_token,
        refresh_token,
        id: admin.id,
        username: admin.username,
        full_name: admin.full_name,
        role: admin.role,
    })
}

pub async fn review_video_action(
    repo: &impl AdminRepo,
    video_id: i64,
    admin_id: i64,
    status: i16,
    notes: Option<&str>,
) -> Result<(), AppError> {
    // Only allow approve(4) or reject(5)
    if status != 4 && status != 5 {
        return Err(AppError::BadRequest("审核状态只能是通过(4)或驳回(5)".into()));
    }

    let updated = repo.review_video(video_id, admin_id, status, notes).await?;
    if !updated {
        return Err(AppError::BadRequest("视频不在待审核状态".into()));
    }
    Ok(())
}

pub fn sign_admin_token(admin: &AdminRow, config: &JwtConfig) -> Result<String, AppError> {
    let now = Utc::now().timestamp();
    let role_str = match admin.role {
        1 => "superadmin",
        _ => "admin",
    };
    let claims = Claims {
        sub: admin.id,
        iss: "gabon-admin".into(),
        aud: "admin".into(),
        iat: now,
        exp: now + config.admin_access_ttl.cast_signed(),
        kid: config.current_kid.clone(),
        role: role_str.into(),
    };

    let header = Header {
        kid: Some(config.current_kid.clone()),
        ..Header::default()
    };

    encode(&header, &claims, &EncodingKey::from_secret(config.admin_secret.as_bytes()))
        .map_err(|e| AppError::Internal(e.to_string()))
}

#[derive(Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminProfile {
    pub id: i64,
    pub username: String,
    pub full_name: Option<String>,
    pub role: i16,
}

pub async fn admin_get_me(repo: &impl AdminRepo, admin_id: i64) -> Result<AdminProfile, AppError> {
    let admin = repo.find_admin_by_id(admin_id).await?.ok_or(AppError::NotFound("管理员不存在".into()))?;
    Ok(AdminProfile {
        id: admin.id,
        username: admin.username,
        full_name: admin.full_name,
        role: admin.role,
    })
}

pub async fn admin_delete_video(repo: &impl AdminRepo, video_id: i64) -> Result<(), AppError> {
    let deleted = repo.delete_video(video_id).await?;
    if !deleted {
        return Err(AppError::NotFound("视频不存在".into()));
    }
    Ok(())
}

pub async fn admin_change_customer_password(repo: &impl AdminRepo, customer_id: i64, new_password: &str) -> Result<(), AppError> {
    let hash = bcrypt::hash(new_password, bcrypt::DEFAULT_COST).map_err(|e| AppError::Internal(e.to_string()))?;
    let updated = repo.change_customer_password(customer_id, &hash).await?;
    if !updated {
        return Err(AppError::NotFound("用户不存在".into()));
    }
    Ok(())
}

pub async fn admin_create_user(repo: &impl AdminRepo, username: &str, password: &str, role: i16, full_name: Option<&str>) -> Result<AdminRow, AppError> {
    let hash = bcrypt::hash(password, bcrypt::DEFAULT_COST).map_err(|e| AppError::Internal(e.to_string()))?;
    repo.create_admin(username, &hash, role, full_name).await
}

pub async fn admin_get_by_id(repo: &impl AdminRepo, id: i64) -> Result<AdminRow, AppError> {
    repo.find_admin_by_id(id)
        .await?
        .ok_or(AppError::NotFound("管理员不存在".into()))
}

pub async fn admin_update_user(
    repo: &impl AdminRepo,
    id: i64,
    role: i16,
    full_name: Option<&str>,
    status: i16,
) -> Result<(), AppError> {
    let updated = repo.update_admin(id, role, full_name, status).await?;
    if !updated {
        return Err(AppError::NotFound("管理员不存在".into()));
    }
    Ok(())
}

pub async fn admin_change_password(repo: &impl AdminRepo, id: i64, new_password: &str) -> Result<(), AppError> {
    let hash = bcrypt::hash(new_password, bcrypt::DEFAULT_COST)
        .map_err(|e| AppError::Internal(e.to_string()))?;
    let updated = repo.change_admin_password(id, &hash).await?;
    if !updated {
        return Err(AppError::NotFound("管理员不存在".into()));
    }
    Ok(())
}

pub async fn admin_get_video_detail(repo: &impl AdminRepo, video_id: i64) -> Result<AdminVideoDetailRow, AppError> {
    repo.get_video_detail(video_id)
        .await?
        .ok_or(AppError::NotFound("视频不存在".into()))
}

/// Admin refresh: consume old refresh token, issue new admin access + refresh pair.
/// Uses atomic CAS rotation to prevent replay attacks.
pub async fn admin_refresh_access_token(
    repo: &impl AdminRepo,
    store: &impl TokenStore,
    config: &JwtConfig,
    refresh_token: &str,
) -> Result<(String, String), AppError> {
    let new_refresh = uuid::Uuid::new_v4().to_string();

    // Atomic: GET old → DELETE old → SET new (Lua script in Redis impl)
    let admin_id = store
        .rotate_refresh_token(refresh_token, &new_refresh, config.admin_refresh_ttl)
        .await?
        .ok_or(AppError::Unauthorized)?;

    // Reject if admin logged out (revoked all sessions)
    if store.is_user_revoked(admin_id).await? {
        store.delete_refresh_token(&new_refresh).await?;
        return Err(AppError::Unauthorized);
    }

    let admin_row = repo
        .find_admin_by_id(admin_id)
        .await?
        .ok_or(AppError::Unauthorized)?;
    let access = sign_admin_token(&admin_row, config)?;

    Ok((access, new_refresh))
}

pub async fn admin_delete_user(repo: &impl AdminRepo, target_id: i64, requester_id: i64) -> Result<(), AppError> {
    if target_id == requester_id {
        return Err(AppError::BadRequest("不能删除自己".into()));
    }
    let deleted = repo.delete_admin(target_id).await?;
    if !deleted {
        return Err(AppError::NotFound("管理员不存在".into()));
    }
    Ok(())
}

pub fn verify_admin_token(token: &str, jwt_config: &JwtConfig) -> Result<Claims, AppError> {
    let mut validation = Validation::default();
    validation.set_issuer(&["gabon-admin"]);
    validation.set_audience(&["admin"]);

    let key = DecodingKey::from_secret(jwt_config.admin_secret.as_bytes());
    let data = decode::<Claims>(token, &key, &validation).map_err(|_| AppError::Unauthorized)?;

    Ok(data.claims)
}

#[cfg(test)]
mod tests {
    use std::collections::HashMap;
    use std::sync::Mutex;

    use gabon_shared::config::JwtConfig;
    use gabon_shared::error::AppError;
    use gabon_shared::traits::{AdminCustomerRow, AdminRepo, AdminRole, AdminRow, AdminVideoDetailRow, AdminVideoRow, TokenStore};

    use super::*;

    fn test_jwt_config() -> JwtConfig {
        JwtConfig {
            customer_secret: "customer-secret-32-chars-long!!!".into(),
            customer_access_ttl: 900,
            customer_refresh_ttl: 604800,
            admin_secret: "admin-secret-min-32-chars-long-enough".into(),
            admin_access_ttl: 900,
            admin_refresh_ttl: 604800,
            current_kid: "key-test".into(),
        }
    }

    fn test_admin_row() -> AdminRow {
        AdminRow {
            id: 1,
            username: "admin".into(),
            password_hash: bcrypt::hash("admin123", 4).unwrap(),
            role: AdminRole::Admin as i16,
            full_name: Some("Admin User".into()),
            status: 1,
        }
    }

    // ─── Mock AdminRepo ────────────────────────────

    struct MockAdminRepo {
        admin: Option<AdminRow>,
        review_returns: bool,
    }

    impl MockAdminRepo {
        fn empty() -> Self {
            Self { admin: None, review_returns: false }
        }

        fn with_admin(admin: AdminRow) -> Self {
            Self { admin: Some(admin), review_returns: true }
        }

        fn with_review_result(mut self, val: bool) -> Self {
            self.review_returns = val;
            self
        }
    }

    impl AdminRepo for MockAdminRepo {
        async fn find_admin_by_username(&self, _username: &str) -> Result<Option<AdminRow>, AppError> {
            Ok(self.admin.clone())
        }
        async fn find_admin_by_id(&self, _id: i64) -> Result<Option<AdminRow>, AppError> {
            Ok(self.admin.clone())
        }
        async fn update_admin_last_login(&self, _id: i64) -> Result<(), AppError> {
            Ok(())
        }
        async fn review_video(&self, _video_id: i64, _admin_id: i64, _status: i16, _notes: Option<&str>) -> Result<bool, AppError> {
            Ok(self.review_returns)
        }
        async fn list_videos(&self, _page: i64, _page_size: i64, _status: Option<i16>) -> Result<(Vec<AdminVideoRow>, i64), AppError> {
            Ok((vec![], 0))
        }
        async fn delete_video(&self, _video_id: i64) -> Result<bool, AppError> {
            Ok(self.review_returns)
        }
        async fn list_customers(&self, _page: i64, _page_size: i64, _name: Option<&str>) -> Result<(Vec<AdminCustomerRow>, i64), AppError> {
            Ok((vec![AdminCustomerRow { id: 1, username: "user1".into(), name: Some("User".into()), is_vip: false, diamond_balance: 100 }], 1))
        }
        async fn change_customer_password(&self, _id: i64, _hash: &str) -> Result<bool, AppError> {
            Ok(true)
        }
        async fn list_admins(&self, _page: i64, _page_size: i64) -> Result<(Vec<AdminRow>, i64), AppError> {
            Ok((vec![test_admin_row()], 1))
        }
        async fn create_admin(&self, username: &str, _hash: &str, role: i16, full_name: Option<&str>) -> Result<AdminRow, AppError> {
            Ok(AdminRow { id: 99, username: username.into(), password_hash: String::new(), role, full_name: full_name.map(Into::into), status: 1 })
        }
        async fn delete_admin(&self, _id: i64) -> Result<bool, AppError> {
            Ok(true)
        }
        async fn update_admin(&self, _id: i64, _role: i16, _full_name: Option<&str>, _status: i16) -> Result<bool, AppError> {
            Ok(self.admin.is_some())
        }
        async fn change_admin_password(&self, _id: i64, _hash: &str) -> Result<bool, AppError> {
            Ok(self.admin.is_some())
        }
        async fn get_video_detail(&self, video_id: i64) -> Result<Option<AdminVideoDetailRow>, AppError> {
            if self.review_returns {
                Ok(Some(AdminVideoDetailRow {
                    id: video_id,
                    title: Some("Test Video".into()),
                    description: None,
                    file_url: "https://cdn.example.com/v.mp4".into(),
                    thumbnail_url: None,
                    duration: Some(60),
                    status: 3,
                    like_count: 0,
                    total_clicks: 0,
                    valid_clicks: 0,
                    uploader_id: 1,
                    uploader_name: Some("Uploader".into()),
                    review_notes: None,
                }))
            } else {
                Ok(None)
            }
        }
    }

    // ─── admin_login tests ─────────────────────────

    #[tokio::test]
    async fn admin_login_succeeds() {
        let repo = MockAdminRepo::with_admin(test_admin_row());
        let store = MockTokenStore::new();
        let config = test_jwt_config();
        let result = admin_login(&repo, &store, &config, "admin", "admin123").await;
        assert!(result.is_ok());
        assert!(!result.unwrap().refresh_token.is_empty());
    }

    #[tokio::test]
    async fn admin_login_fails_wrong_password() {
        let repo = MockAdminRepo::with_admin(test_admin_row());
        let store = MockTokenStore::new();
        let config = test_jwt_config();
        let result = admin_login(&repo, &store, &config, "admin", "wrong").await;
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn admin_login_fails_nonexistent() {
        let repo = MockAdminRepo::empty();
        let store = MockTokenStore::new();
        let config = test_jwt_config();
        let result = admin_login(&repo, &store, &config, "nobody", "pass").await;
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn admin_login_fails_disabled_account() {
        let mut admin = test_admin_row();
        admin.status = 0; // disabled
        let repo = MockAdminRepo::with_admin(admin);
        let store = MockTokenStore::new();
        let config = test_jwt_config();
        let result = admin_login(&repo, &store, &config, "admin", "admin123").await;
        assert!(result.is_err());
    }

    // ─── admin JWT tests ───────────────────────────

    #[test]
    fn admin_jwt_uses_admin_secret() {
        let config = test_jwt_config();
        let admin = test_admin_row();
        let token = sign_admin_token(&admin, &config).unwrap();
        let claims = verify_admin_token(&token, &config).unwrap();
        assert_eq!(claims.sub, 1);
        assert_eq!(claims.iss, "gabon-admin");
        assert_eq!(claims.aud, "admin");
    }

    #[test]
    fn admin_jwt_rejects_customer_secret() {
        let config = test_jwt_config();
        let admin = test_admin_row();
        let token = sign_admin_token(&admin, &config).unwrap();
        // Try verifying with customer verify function (wrong iss/aud/secret)
        let bad_config = JwtConfig {
            admin_secret: "wrong-admin-secret-32-chars-long!".into(),
            ..config
        };
        assert!(verify_admin_token(&token, &bad_config).is_err());
    }

    // ─── review_video tests ────────────────────────

    #[tokio::test]
    async fn review_approve_succeeds() {
        let repo = MockAdminRepo::with_admin(test_admin_row());
        let result = review_video_action(&repo, 1, 1, 4, None).await;
        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn review_reject_succeeds() {
        let repo = MockAdminRepo::with_admin(test_admin_row()).with_review_result(true);
        let result = review_video_action(&repo, 1, 1, 5, Some("内容不合规")).await;
        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn review_fails_invalid_status() {
        let repo = MockAdminRepo::with_admin(test_admin_row());
        let result = review_video_action(&repo, 1, 1, 2, None).await; // 2=TRANSCODING, invalid
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn review_fails_video_not_reviewable() {
        let repo = MockAdminRepo::with_admin(test_admin_row()).with_review_result(false);
        let result = review_video_action(&repo, 1, 1, 4, None).await;
        assert!(result.is_err());
    }

    // ─── admin_get_me tests ────────────────────────

    #[tokio::test]
    async fn admin_get_me_succeeds() {
        let repo = MockAdminRepo::with_admin(test_admin_row());
        let profile = admin_get_me(&repo, 1).await.unwrap();
        assert_eq!(profile.username, "admin");
        // AdminProfile has no access_token field (unlike AdminLoginResponse)
        let json = serde_json::to_value(&profile).unwrap();
        assert!(json.get("accessToken").is_none());
    }

    #[tokio::test]
    async fn admin_get_me_fails_nonexistent() {
        let repo = MockAdminRepo::empty();
        assert!(admin_get_me(&repo, 999).await.is_err());
    }

    // ─── admin video management tests ──────────────

    #[tokio::test]
    async fn admin_delete_video_succeeds() {
        let repo = MockAdminRepo::with_admin(test_admin_row());
        assert!(admin_delete_video(&repo, 1).await.is_ok());
    }

    #[tokio::test]
    async fn admin_delete_video_fails_not_found() {
        let repo = MockAdminRepo::with_admin(test_admin_row()).with_review_result(false);
        assert!(admin_delete_video(&repo, 999).await.is_err());
    }

    // ─── admin customer management tests ───────────

    #[tokio::test]
    async fn admin_change_customer_password_succeeds() {
        let repo = MockAdminRepo::with_admin(test_admin_row());
        assert!(admin_change_customer_password(&repo, 1, "newpass").await.is_ok());
    }

    // ─── admin user CRUD tests ─────────────────────

    #[tokio::test]
    async fn admin_create_user_succeeds() {
        let repo = MockAdminRepo::with_admin(test_admin_row());
        let result = admin_create_user(&repo, "newadmin", "pass123", 2, Some("New Admin")).await;
        assert!(result.is_ok());
        assert_eq!(result.unwrap().username, "newadmin");
    }

    #[tokio::test]
    async fn admin_delete_self_fails() {
        let repo = MockAdminRepo::with_admin(test_admin_row());
        let result = admin_delete_user(&repo, 1, 1).await; // deleting self
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn admin_delete_other_succeeds() {
        let repo = MockAdminRepo::with_admin(test_admin_row());
        assert!(admin_delete_user(&repo, 2, 1).await.is_ok());
    }

    // ─── admin_get_by_id tests ────────────────────

    #[tokio::test]
    async fn admin_get_by_id_succeeds() {
        let repo = MockAdminRepo::with_admin(test_admin_row());
        let admin = admin_get_by_id(&repo, 1).await.unwrap();
        assert_eq!(admin.username, "admin");
    }

    #[tokio::test]
    async fn admin_get_by_id_fails_nonexistent() {
        let repo = MockAdminRepo::empty();
        assert!(admin_get_by_id(&repo, 999).await.is_err());
    }

    // ─── admin_update_user tests ──────────────────

    #[tokio::test]
    async fn admin_update_user_succeeds() {
        let repo = MockAdminRepo::with_admin(test_admin_row());
        assert!(admin_update_user(&repo, 1, 2, Some("New Name"), 1).await.is_ok());
    }

    #[tokio::test]
    async fn admin_update_user_fails_nonexistent() {
        let repo = MockAdminRepo::empty();
        assert!(admin_update_user(&repo, 999, 2, None, 1).await.is_err());
    }

    // ─── admin_change_password tests ──────────────

    #[tokio::test]
    async fn admin_change_password_succeeds() {
        let repo = MockAdminRepo::with_admin(test_admin_row());
        assert!(admin_change_password(&repo, 1, "newpass123").await.is_ok());
    }

    #[tokio::test]
    async fn admin_change_password_fails_nonexistent() {
        let repo = MockAdminRepo::empty();
        assert!(admin_change_password(&repo, 999, "newpass").await.is_err());
    }

    // ─── admin_get_video_detail tests ─────────────

    #[tokio::test]
    async fn admin_get_video_detail_succeeds() {
        let repo = MockAdminRepo::with_admin(test_admin_row());
        let detail = admin_get_video_detail(&repo, 1).await.unwrap();
        assert_eq!(detail.id, 1);
        assert_eq!(detail.status, 3);
    }

    #[tokio::test]
    async fn admin_get_video_detail_fails_nonexistent() {
        let repo = MockAdminRepo::with_admin(test_admin_row()).with_review_result(false);
        assert!(admin_get_video_detail(&repo, 999).await.is_err());
    }

    // ─── admin refresh token tests ────────────────

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
        async fn rotate_refresh_token(&self, old: &str, new: &str, ttl: u64) -> Result<Option<i64>, AppError> {
            let uid = self.get_refresh_token_user(old).await?;
            if let Some(id) = uid {
                self.delete_refresh_token(old).await?;
                self.store_refresh_token(new, id, ttl).await?;
            }
            Ok(uid)
        }
        async fn blacklist_access_token(&self, token: &str, _ttl: u64) -> Result<(), AppError> {
            self.blacklist.lock().unwrap().push(token.to_string());
            Ok(())
        }
        async fn is_blacklisted(&self, token: &str) -> Result<bool, AppError> {
            Ok(self.blacklist.lock().unwrap().contains(&token.to_string()))
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
    }

    #[tokio::test]
    async fn admin_refresh_succeeds() {
        let repo = MockAdminRepo::with_admin(test_admin_row());
        let store = MockTokenStore::new();
        let config = test_jwt_config();
        // Seed a refresh token
        store.store_refresh_token("admin-refresh-123", 1, 604800).await.unwrap();
        let result = admin_refresh_access_token(&repo, &store, &config, "admin-refresh-123").await;
        assert!(result.is_ok());
        let (access, new_refresh) = result.unwrap();
        assert!(!access.is_empty());
        assert!(!new_refresh.is_empty());
        // Old token consumed
        assert!(store.get_refresh_token_user("admin-refresh-123").await.unwrap().is_none());
    }

    #[tokio::test]
    async fn admin_refresh_fails_invalid_token() {
        let repo = MockAdminRepo::empty();
        let store = MockTokenStore::new();
        let config = test_jwt_config();
        assert!(admin_refresh_access_token(&repo, &store, &config, "bogus").await.is_err());
    }
}
