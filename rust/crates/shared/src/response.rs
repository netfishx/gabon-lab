use axum::response::{IntoResponse, Response};
use serde::Serialize;

/// Unified API response format, matching gabon's `JsonData`.
/// `{ "code": 0, "msg": "ok", "data": T }`
#[derive(Debug, Serialize)]
pub struct JsonData<T: Serialize> {
    pub code: i32,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub msg: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub data: Option<T>,
}

impl<T: Serialize> JsonData<T> {
    pub fn ok(data: T) -> Self {
        Self {
            code: 0,
            msg: None,
            data: Some(data),
        }
    }

    pub fn error(code: i32, msg: String) -> Self {
        Self {
            code,
            msg: Some(msg),
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
    fn ok_response_has_code_zero_and_data() {
        let resp = JsonData::ok("hello");
        let json = serde_json::to_value(&resp).unwrap();

        assert_eq!(json["code"], 0);
        assert_eq!(json["data"], "hello");
        assert!(json.get("msg").is_none());
    }

    #[test]
    fn error_response_has_code_and_msg_no_data() {
        let resp = JsonData::<()>::error(400, "bad request".into());
        let json = serde_json::to_value(&resp).unwrap();

        assert_eq!(json["code"], 400);
        assert_eq!(json["msg"], "bad request");
        assert!(json.get("data").is_none());
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
        assert_eq!(json["data"]["id"], 1);
        assert_eq!(json["data"]["name"], "test");
    }
}
