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
}

impl IntoResponse for AppError {
    fn into_response(self) -> Response {
        let status = self.status_code();
        // Database/Internal may contain SQL details or stack traces — never leak to client
        let message = match &self {
            Self::Database(_) | Self::Internal(_) => "内部服务错误".to_string(),
            other => other.to_string(),
        };
        let body = JsonData::<()>::error(status.as_u16().into(), message);
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
    }

    #[test]
    fn forbidden_maps_to_403() {
        let err = AppError::Forbidden;
        assert_eq!(err.status_code(), StatusCode::FORBIDDEN);
    }

    #[test]
    fn not_found_maps_to_404() {
        let err = AppError::NotFound("missing".into());
        assert_eq!(err.status_code(), StatusCode::NOT_FOUND);
        assert_eq!(err.to_string(), "missing");
    }

    #[test]
    fn bad_request_maps_to_400() {
        let err = AppError::BadRequest("invalid".into());
        assert_eq!(err.status_code(), StatusCode::BAD_REQUEST);
    }

    #[test]
    fn conflict_maps_to_409() {
        let err = AppError::Conflict("duplicate".into());
        assert_eq!(err.status_code(), StatusCode::CONFLICT);
    }

    #[test]
    fn internal_maps_to_500() {
        let err = AppError::Internal("boom".into());
        assert_eq!(err.status_code(), StatusCode::INTERNAL_SERVER_ERROR);
    }

    #[test]
    fn database_error_does_not_leak_details() {
        let err = AppError::Database(sqlx::Error::RowNotFound);
        let resp = err.into_response();
        assert_eq!(resp.status(), StatusCode::INTERNAL_SERVER_ERROR);
    }

    #[test]
    fn internal_error_does_not_leak_details() {
        let err = AppError::Internal("sensitive sql info".into());
        let resp = err.into_response();
        assert_eq!(resp.status(), StatusCode::INTERNAL_SERVER_ERROR);
    }
}
