use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};

use crate::response::JsonData;

/// Domain-level typed errors. Use `thiserror` for compile-time exhaustiveness.
/// Only `anyhow` at the API handler level for catch-all context.
#[derive(Debug, thiserror::Error)]
pub enum AppError {
    #[error("未授权")]
    Unauthorized,

    #[error("禁止访问")]
    Forbidden,

    #[error("{0}")]
    NotFound(String),

    #[error("{0}")]
    BadRequest(String),

    #[error("{0}")]
    Conflict(String),

    #[error("数据库错误: {0}")]
    Database(#[from] sqlx::Error),

    #[error("内部错误: {0}")]
    Internal(String),
}

impl AppError {
    fn status_code(&self) -> StatusCode {
        match self {
            Self::Unauthorized => StatusCode::UNAUTHORIZED,
            Self::Forbidden => StatusCode::FORBIDDEN,
            Self::NotFound(_) => StatusCode::NOT_FOUND,
            Self::BadRequest(_) => StatusCode::BAD_REQUEST,
            Self::Conflict(_) => StatusCode::CONFLICT,
            Self::Database(_) | Self::Internal(_) => StatusCode::INTERNAL_SERVER_ERROR,
        }
    }

    fn code(&self) -> i32 {
        match self {
            Self::Unauthorized => 401,
            Self::Forbidden => 403,
            Self::NotFound(_) => 404,
            Self::BadRequest(_) => 400,
            Self::Conflict(_) => 409,
            Self::Database(_) | Self::Internal(_) => 500,
        }
    }
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let status = self.status_code();
        let body = JsonData::<()>::error(self.code(), self.to_string());
        (status, axum::Json(body)).into_response()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn unauthorized_maps_to_401() {
        let err = AppError::Unauthorized;
        assert_eq!(err.status_code(), StatusCode::UNAUTHORIZED);
        assert_eq!(err.code(), 401);
    }

    #[test]
    fn forbidden_maps_to_403() {
        let err = AppError::Forbidden;
        assert_eq!(err.status_code(), StatusCode::FORBIDDEN);
        assert_eq!(err.code(), 403);
    }

    #[test]
    fn not_found_maps_to_404() {
        let err = AppError::NotFound("missing".into());
        assert_eq!(err.status_code(), StatusCode::NOT_FOUND);
        assert_eq!(err.code(), 404);
        assert_eq!(err.to_string(), "missing");
    }

    #[test]
    fn bad_request_maps_to_400() {
        let err = AppError::BadRequest("invalid".into());
        assert_eq!(err.status_code(), StatusCode::BAD_REQUEST);
        assert_eq!(err.code(), 400);
    }

    #[test]
    fn conflict_maps_to_409() {
        let err = AppError::Conflict("duplicate".into());
        assert_eq!(err.status_code(), StatusCode::CONFLICT);
        assert_eq!(err.code(), 409);
    }

    #[test]
    fn internal_maps_to_500() {
        let err = AppError::Internal("boom".into());
        assert_eq!(err.status_code(), StatusCode::INTERNAL_SERVER_ERROR);
        assert_eq!(err.code(), 500);
    }
}
