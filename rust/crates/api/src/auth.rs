use axum::extract::State;
use axum::Json;
use serde::Deserialize;

use gabon_infra::customer_repo::PgAuthRepo;
use gabon_infra::redis_store::RedisTokenStore;
use gabon_shared::error::AppError;
use gabon_shared::response::JsonData;

use crate::AppState;
use crate::middleware::AuthCustomer;
use crate::service;

#[derive(Deserialize)]
pub struct AuthRequest {
    pub username: String,
    pub password: String,
}

pub async fn register(
    State(state): State<AppState>,
    Json(body): Json<AuthRequest>,
) -> Result<JsonData<service::AuthResponse>, AppError> {
    let repo = PgAuthRepo { pool: &state.db };
    let store = RedisTokenStore { pool: &state.redis };
    let result = service::register(&repo, &store, &state.config.jwt, &body.username, &body.password).await?;
    Ok(JsonData::ok(result))
}

pub async fn login(
    State(state): State<AppState>,
    Json(body): Json<AuthRequest>,
) -> Result<JsonData<service::AuthResponse>, AppError> {
    let repo = PgAuthRepo { pool: &state.db };
    let store = RedisTokenStore { pool: &state.redis };
    let result = service::login(&repo, &store, &state.config.jwt, &body.username, &body.password).await?;
    Ok(JsonData::ok(result))
}

pub async fn me(
    State(state): State<AppState>,
    AuthCustomer(claims): AuthCustomer,
) -> Result<JsonData<service::CustomerProfile>, AppError> {
    let repo = PgAuthRepo { pool: &state.db };
    let profile = service::get_me(&repo, claims.sub).await?;
    Ok(JsonData::ok(profile))
}

#[derive(Deserialize)]
pub struct ChangePasswordRequest {
    pub old_password: String,
    pub new_password: String,
}

pub async fn change_password(
    State(state): State<AppState>,
    AuthCustomer(claims): AuthCustomer,
    Json(body): Json<ChangePasswordRequest>,
) -> Result<JsonData<()>, AppError> {
    let repo = PgAuthRepo { pool: &state.db };
    service::change_password(&repo, claims.sub, &body.old_password, &body.new_password).await?;
    Ok(JsonData::ok(()))
}
