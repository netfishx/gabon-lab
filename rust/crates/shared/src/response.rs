use axum::response::{IntoResponse, Response};
use serde::Serialize;

/// Unified API response format.
/// Success: `{ "code": 0, "message": "ok", "data": T }`
/// Error:   `{ "code": <http_status>, "message": "...", "data": null }`
#[derive(Debug, Serialize)]
pub struct JsonData<T: Serialize> {
    pub code: i32,
    pub message: Option<String>,
    pub data: Option<T>,
}

impl<T: Serialize> JsonData<T> {
    pub fn ok(data: T) -> Self {
        Self {
            code: 0,
            message: Some("ok".into()),
            data: Some(data),
        }
    }

    pub fn error(code: i32, message: String) -> Self {
        Self {
            code,
            message: Some(message),
            data: None,
        }
    }
}

impl<T: Serialize> IntoResponse for JsonData<T> {
    fn into_response(self) -> Response {
        axum::Json(self).into_response()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ok_response_has_code_zero_and_message_ok() {
        let resp = JsonData::ok("hello");
        let json = serde_json::to_value(&resp).unwrap();

        assert_eq!(json["code"], 0);
        assert_eq!(json["message"], "ok");
        assert_eq!(json["data"], "hello");
    }

    #[test]
    fn error_response_has_code_and_message_null_data() {
        let resp = JsonData::<()>::error(400, "bad request".into());
        let json = serde_json::to_value(&resp).unwrap();

        assert_eq!(json["code"], 400);
        assert_eq!(json["message"], "bad request");
        assert!(json["data"].is_null());
    }

    #[test]
    fn ok_with_struct_serializes_correctly() {
        #[derive(serde::Serialize)]
        struct User {
            id: i64,
            name: String,
        }

        let resp = JsonData::ok(User { id: 1, name: "test".into() });
        let json = serde_json::to_value(&resp).unwrap();

        assert_eq!(json["code"], 0);
        assert_eq!(json["message"], "ok");
        assert_eq!(json["data"]["id"], 1);
        assert_eq!(json["data"]["name"], "test");
    }
}
