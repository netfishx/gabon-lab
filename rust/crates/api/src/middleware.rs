use axum::extract::FromRequestParts;
use axum::http::request::Parts;

use crate::service;
use gabon_shared::error::AppError;

use crate::AppState;

/// Extractor that validates JWT and provides the customer ID.
/// Usage: `async fn handler(claims: AuthCustomer) -> ...`
pub struct AuthCustomer(pub service::Claims);

impl FromRequestParts<AppState> for AuthCustomer {
    type Rejection = AppError;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &AppState,
    ) -> Result<Self, Self::Rejection> {
        let header = parts
            .headers
            .get("authorization")
            .and_then(|v| v.to_str().ok())
            .ok_or(AppError::Unauthorized)?;

        let token = header
            .strip_prefix("Bearer ")
            .ok_or(AppError::Unauthorized)?;

        let claims = service::verify_customer_token(token, &state.config.jwt)?;
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
        let Some(header) = parts
            .headers
            .get("authorization")
            .and_then(|v| v.to_str().ok())
        else {
            return Ok(Self(None));
        };

        let Some(token) = header.strip_prefix("Bearer ") else {
            return Ok(Self(None));
        };

        match service::verify_customer_token(token, &state.config.jwt) {
            Ok(claims) => Ok(Self(Some(claims))),
            Err(_) => Ok(Self(None)),
        }
    }
}
