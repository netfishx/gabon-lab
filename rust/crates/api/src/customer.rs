use axum::extract::{Multipart, Path, Query, State};
use axum::Json;
use serde::Deserialize;

use gabon_infra::customer_repo::PgAuthRepo;
use gabon_infra::social_repo::PgSocialRepo;
use gabon_shared::error::AppError;
use gabon_shared::pagination::Paginated;
use gabon_shared::response::JsonData;
use gabon_shared::traits::{AuthRepo, FollowRow, SocialRepo};

use crate::AppState;
use crate::middleware::AuthCustomer;
use crate::service::{self, CustomerProfile};

#[derive(Deserialize)]
pub struct ListParams {
    pub page: Option<i64>,
    pub size: Option<i64>,
}

pub async fn get_profile(
    State(state): State<AppState>,
    Path(user_id): Path<i64>,
) -> Result<JsonData<CustomerProfile>, AppError> {
    let repo = PgAuthRepo { pool: &state.db };
    let profile = service::get_me(&repo, user_id).await?;
    Ok(JsonData::ok(profile))
}

pub async fn get_following(
    State(state): State<AppState>,
    AuthCustomer(claims): AuthCustomer,
    Query(params): Query<ListParams>,
) -> Result<JsonData<Paginated<FollowRow>>, AppError> {
    let repo = PgSocialRepo { pool: &state.db };
    let page = params.page.unwrap_or(1);
    let size = params.size.unwrap_or(20);
    let (list, total) = repo.get_following(claims.sub, page, size).await?;
    Ok(JsonData::ok(Paginated::new(list, page, size, total)))
}

pub async fn get_followers(
    State(state): State<AppState>,
    AuthCustomer(claims): AuthCustomer,
    Query(params): Query<ListParams>,
) -> Result<JsonData<Paginated<FollowRow>>, AppError> {
    let repo = PgSocialRepo { pool: &state.db };
    let page = params.page.unwrap_or(1);
    let size = params.size.unwrap_or(20);
    let (list, total) = repo.get_followers(claims.sub, page, size).await?;
    Ok(JsonData::ok(Paginated::new(list, page, size, total)))
}

// ─── /api/users/me/* handlers ─────────────────

pub async fn get_my_profile(
    State(state): State<AppState>,
    AuthCustomer(claims): AuthCustomer,
) -> Result<JsonData<CustomerProfile>, AppError> {
    let repo = PgAuthRepo { pool: &state.db };
    let profile = service::get_me(&repo, claims.sub).await?;
    Ok(JsonData::ok(profile))
}

#[derive(Deserialize)]
pub struct UpdateProfileRequest {
    pub name: Option<String>,
    pub phone: Option<String>,
    pub email: Option<String>,
    pub signature: Option<String>,
}

pub async fn update_my_profile(
    State(state): State<AppState>,
    AuthCustomer(claims): AuthCustomer,
    Json(body): Json<UpdateProfileRequest>,
) -> Result<JsonData<CustomerProfile>, AppError> {
    let repo = PgAuthRepo { pool: &state.db };
    let profile = update_profile(
        &repo,
        claims.sub,
        body.name.as_deref(),
        body.phone.as_deref(),
        body.email.as_deref(),
        body.signature.as_deref(),
    )
    .await?;
    Ok(JsonData::ok(profile))
}

const MAX_AVATAR_SIZE: usize = 5 * 1024 * 1024; // 5 MB

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AvatarResponse {
    pub avatar_url: String,
}

pub async fn upload_avatar(
    State(state): State<AppState>,
    AuthCustomer(claims): AuthCustomer,
    mut multipart: Multipart,
) -> Result<JsonData<AvatarResponse>, AppError> {
    let field = multipart
        .next_field()
        .await
        .map_err(|e| AppError::BadRequest(format!("multipart 解析失败: {e}")))?
        .ok_or_else(|| AppError::BadRequest("缺少文件字段".into()))?;

    let content_type = field
        .content_type()
        .unwrap_or("image/jpeg")
        .to_string();

    let data = field
        .bytes()
        .await
        .map_err(|e| AppError::BadRequest(format!("读取文件失败: {e}")))?;

    if data.len() > MAX_AVATAR_SIZE {
        return Err(AppError::BadRequest(format!(
            "文件过大，最大 {}MB",
            MAX_AVATAR_SIZE / 1024 / 1024
        )));
    }

    let ext = match content_type.as_str() {
        "image/png" => "png",
        "image/webp" => "webp",
        "image/gif" => "gif",
        _ => "jpg",
    };
    let key = format!("avatars/{}.{ext}", claims.sub);

    let avatar_url = state
        .s3
        .upload(
            &state.config.s3.bucket_avatars,
            &key,
            &content_type,
            data.to_vec(),
        )
        .await
        .map_err(|e| AppError::Internal(e.to_string()))?;

    let repo = PgAuthRepo { pool: &state.db };
    repo.update_avatar(claims.sub, &avatar_url).await?;

    Ok(JsonData::ok(AvatarResponse { avatar_url }))
}

// ─── /api/users/{id}/following, /api/users/{id}/followers ──

pub async fn get_user_following(
    State(state): State<AppState>,
    Path(user_id): Path<i64>,
    Query(params): Query<ListParams>,
) -> Result<JsonData<Paginated<FollowRow>>, AppError> {
    let repo = PgSocialRepo { pool: &state.db };
    let page = params.page.unwrap_or(1);
    let size = params.size.unwrap_or(20);
    let (list, total) = repo.get_following(user_id, page, size).await?;
    Ok(JsonData::ok(Paginated::new(list, page, size, total)))
}

pub async fn get_user_followers(
    State(state): State<AppState>,
    Path(user_id): Path<i64>,
    Query(params): Query<ListParams>,
) -> Result<JsonData<Paginated<FollowRow>>, AppError> {
    let repo = PgSocialRepo { pool: &state.db };
    let page = params.page.unwrap_or(1);
    let size = params.size.unwrap_or(20);
    let (list, total) = repo.get_followers(user_id, page, size).await?;
    Ok(JsonData::ok(Paginated::new(list, page, size, total)))
}

// ─── Service ───────────────────────────────────

pub async fn update_profile(
    repo: &impl AuthRepo,
    user_id: i64,
    name: Option<&str>,
    phone: Option<&str>,
    email: Option<&str>,
    signature: Option<&str>,
) -> Result<CustomerProfile, AppError> {
    let row = repo.update_profile(user_id, name, phone, email, signature).await?;
    Ok(row.into())
}

#[cfg(test)]
mod tests {
    use gabon_shared::traits::{AuthRepo, CustomerRow, FollowRow, SocialRepo};
    use gabon_shared::error::AppError;

    use super::*;

    struct MockSocialRepoWithLists;

    impl SocialRepo for MockSocialRepoWithLists {
        async fn follow(&self, _: i64, _: i64) -> Result<bool, AppError> { Ok(true) }
        async fn unfollow(&self, _: i64, _: i64) -> Result<bool, AppError> { Ok(true) }

        async fn get_following(&self, customer_id: i64, _page: i64, _page_size: i64) -> Result<(Vec<FollowRow>, i64), AppError> {
            if customer_id == 1 {
                let items = vec![
                    FollowRow { id: 2, name: Some("Bob".into()), avatar_url: None, is_vip: false },
                    FollowRow { id: 3, name: Some("Carol".into()), avatar_url: None, is_vip: true },
                ];
                Ok((items, 2))
            } else {
                Ok((vec![], 0))
            }
        }

        async fn get_followers(&self, customer_id: i64, _page: i64, _page_size: i64) -> Result<(Vec<FollowRow>, i64), AppError> {
            if customer_id == 2 {
                let items = vec![
                    FollowRow { id: 1, name: Some("Alice".into()), avatar_url: None, is_vip: false },
                ];
                Ok((items, 1))
            } else {
                Ok((vec![], 0))
            }
        }
    }

    #[tokio::test]
    async fn get_following_returns_list() {
        let repo = MockSocialRepoWithLists;
        let (list, total) = repo.get_following(1, 1, 20).await.unwrap();
        assert_eq!(list.len(), 2);
        assert_eq!(total, 2);
        assert_eq!(list[0].name, Some("Bob".into()));
    }

    #[tokio::test]
    async fn get_following_empty_for_unknown_user() {
        let repo = MockSocialRepoWithLists;
        let (list, total) = repo.get_following(999, 1, 20).await.unwrap();
        assert!(list.is_empty());
        assert_eq!(total, 0);
    }

    #[tokio::test]
    async fn get_followers_returns_list() {
        let repo = MockSocialRepoWithLists;
        let (list, total) = repo.get_followers(2, 1, 20).await.unwrap();
        assert_eq!(list.len(), 1);
        assert_eq!(total, 1);
        assert_eq!(list[0].name, Some("Alice".into()));
    }

    #[tokio::test]
    async fn get_followers_empty_for_unknown_user() {
        let repo = MockSocialRepoWithLists;
        let (list, total) = repo.get_followers(999, 1, 20).await.unwrap();
        assert!(list.is_empty());
        assert_eq!(total, 0);
    }

    // ─── Mock AuthRepo for update_profile ─────────

    struct MockAuthRepoForProfile;

    impl AuthRepo for MockAuthRepoForProfile {
        async fn find_by_username(&self, _: &str) -> Result<Option<CustomerRow>, AppError> { Ok(None) }
        async fn find_by_id(&self, _: i64) -> Result<Option<CustomerRow>, AppError> { Ok(None) }
        async fn create(&self, _: &str, _: &str) -> Result<CustomerRow, AppError> {
            Err(AppError::Internal("not implemented".into()))
        }
        async fn update_last_login(&self, _: i64) -> Result<(), AppError> { Ok(()) }
        async fn change_password(&self, _: i64, _: &str) -> Result<(), AppError> { Ok(()) }
        async fn update_avatar(&self, _: i64, _: &str) -> Result<(), AppError> { Ok(()) }
        async fn update_profile(
            &self,
            id: i64,
            name: Option<&str>,
            _phone: Option<&str>,
            _email: Option<&str>,
            signature: Option<&str>,
        ) -> Result<CustomerRow, AppError> {
            Ok(CustomerRow {
                id,
                username: "testuser".into(),
                password_hash: String::new(),
                name: name.map(Into::into),
                phone: None,
                email: None,
                avatar_url: None,
                signature: signature.map(Into::into),
                is_vip: false,
                diamond_balance: 0,
            })
        }
    }

    #[tokio::test]
    async fn update_profile_returns_updated_fields() {
        let repo = MockAuthRepoForProfile;
        let profile = update_profile(&repo, 42, Some("New Name"), None, None, Some("Hello")).await.unwrap();
        assert_eq!(profile.id, 42);
        assert_eq!(profile.name, Some("New Name".into()));
        assert_eq!(profile.signature, Some("Hello".into()));
    }

    #[tokio::test]
    async fn update_profile_with_no_fields() {
        let repo = MockAuthRepoForProfile;
        let profile = update_profile(&repo, 42, None, None, None, None).await.unwrap();
        assert_eq!(profile.id, 42);
        assert!(profile.name.is_none());
    }
}
