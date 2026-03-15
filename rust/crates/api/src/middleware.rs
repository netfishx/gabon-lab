use axum::extract::FromRequestParts;
use axum::http::request::Parts;

use crate::service;
use gabon_infra::redis_store::RedisTokenStore;
use gabon_shared::error::AppError;
use gabon_shared::traits::TokenStore;

use crate::AppState;

/// Extract raw Bearer token from Authorization header.
pub(crate) fn extract_bearer(parts: &Parts) -> Option<&str> {
    parts
        .headers
        .get("authorization")
        .and_then(|v| v.to_str().ok())
        .and_then(|h| h.strip_prefix("Bearer "))
}

/// Extractor that validates JWT and provides the customer ID.
/// Usage: `async fn handler(claims: AuthCustomer) -> ...`
pub struct AuthCustomer(pub service::Claims);

impl FromRequestParts<AppState> for AuthCustomer {
    type Rejection = AppError;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &AppState,
    ) -> Result<Self, Self::Rejection> {
        let token = extract_bearer(parts).ok_or(AppError::Unauthorized)?;
        let claims = service::verify_customer_token(token, &state.config.jwt)?;

        let store = RedisTokenStore { pool: &state.redis };
        if store.is_blacklisted(token).await? {
            return Err(AppError::Unauthorized);
        }

        Ok(Self(claims))
    }
}

/// Optional auth extractor — returns None if no token, errors only on invalid token.
pub struct OptionalAuth(pub Option<service::Claims>);

impl FromRequestParts<AppState> for OptionalAuth {
    type Rejection = AppError;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &AppState,
    ) -> Result<Self, Self::Rejection> {
        let Some(token) = extract_bearer(parts) else {
            return Ok(Self(None));
        };

        let Ok(claims) = service::verify_customer_token(token, &state.config.jwt) else {
            return Ok(Self(None));
        };

        let store = RedisTokenStore { pool: &state.redis };
        if store.is_blacklisted(token).await.unwrap_or(false) {
            return Ok(Self(None));
        }

        Ok(Self(Some(claims)))
    }
}
