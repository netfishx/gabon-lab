use chrono::{DateTime, Utc};
use serde::Serialize;
use sqlx::FromRow;

/// Customer account — maps to `customers` table.
#[derive(Debug, Clone, FromRow)]
pub struct Customer {
    pub id: i64,
    pub username: String,
    pub password_hash: String,
    pub name: Option<String>,
    pub phone: Option<String>,
    pub email: Option<String>,
    pub avatar_url: Option<String>,
    pub signature: Option<String>,
    pub is_vip: bool,
    pub diamond_balance: i64,
    pub last_login_at: Option<DateTime<Utc>>,
    pub created_at: DateTime<Utc>,
    pub updated_at: DateTime<Utc>,
    pub deleted_at: Option<DateTime<Utc>>,
}

/// Public-facing customer profile (no `password_hash`).
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

impl From<Customer> for CustomerProfile {
    fn from(c: Customer) -> Self {
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

/// Video status lifecycle:
/// `0=FAILED`, `1=PENDING_TRANSCODE`, `2=TRANSCODING`,
/// `3=PENDING_REVIEW`, `4=APPROVED`, `5=REJECTED`
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, sqlx::Type)]
#[repr(i16)]
pub enum VideoStatus {
    Failed = 0,
    PendingTranscode = 1,
    Transcoding = 2,
    PendingReview = 3,
    Approved = 4,
    Rejected = 5,
}

#[cfg(test)]
mod tests {
    use super::*;
    use chrono::Utc;

    fn test_customer() -> Customer {
        Customer {
            id: 1,
            username: "alice".into(),
            password_hash: "secret_hash_value".into(),
            name: Some("Alice".into()),
            phone: Some("1234567890".into()),
            email: Some("alice@test.com".into()),
            avatar_url: None,
            signature: None,
            is_vip: true,
            diamond_balance: 500,
            last_login_at: None,
            created_at: Utc::now(),
            updated_at: Utc::now(),
            deleted_at: None,
        }
    }

    #[test]
    fn customer_profile_excludes_password_hash() {
        let customer = test_customer();
        let profile: CustomerProfile = customer.into();
        let json = serde_json::to_value(&profile).unwrap();

        assert_eq!(json["id"], 1);
        assert_eq!(json["username"], "alice");
        assert!(json.get("passwordHash").is_none());
        assert!(json.get("password_hash").is_none());
    }

    #[test]
    fn customer_profile_preserves_fields() {
        let customer = test_customer();
        let profile: CustomerProfile = customer.into();

        assert_eq!(profile.name, Some("Alice".into()));
        assert_eq!(profile.email, Some("alice@test.com".into()));
        assert!(profile.is_vip);
        assert_eq!(profile.diamond_balance, 500);
    }

    #[test]
    fn customer_profile_serializes_camel_case() {
        let customer = test_customer();
        let profile: CustomerProfile = customer.into();
        let json = serde_json::to_value(&profile).unwrap();

        assert!(json.get("diamondBalance").is_some());
        assert!(json.get("isVip").is_some());
        assert!(json.get("avatarUrl").is_some());
    }

    #[test]
    fn video_status_repr_values() {
        assert_eq!(VideoStatus::Failed as i16, 0);
        assert_eq!(VideoStatus::PendingTranscode as i16, 1);
        assert_eq!(VideoStatus::Approved as i16, 4);
        assert_eq!(VideoStatus::Rejected as i16, 5);
    }
}
