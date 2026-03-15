use axum::extract::{Path, Query, State};
use axum::Json;
use serde::Deserialize;

use gabon_infra::video_repo::PgVideoRepo;
use gabon_shared::error::AppError;
use gabon_shared::pagination::Paginated;
use gabon_shared::response::JsonData;
use gabon_shared::traits::{MyVideoRow, VideoDetailRow, VideoListRow, VideoRepo};

use crate::AppState;
use crate::middleware::{AuthCustomer, OptionalAuth};

#[derive(Deserialize)]
pub struct ListParams {
    pub page: Option<i64>,
    pub size: Option<i64>,
    pub keyword: Option<String>,
}

pub async fn list(
    State(state): State<AppState>,
    Query(params): Query<ListParams>,
) -> Result<JsonData<Paginated<VideoListRow>>, AppError> {
    let repo = PgVideoRepo { pool: &state.db };
    let page = params.page.unwrap_or(1);
    let size = params.size.unwrap_or(20);
    let (items, total) = repo.list_approved(page, size, params.keyword.as_deref()).await?;
    Ok(JsonData::ok(Paginated::new(items, page, size, total)))
}

pub async fn featured(
    State(state): State<AppState>,
    Query(params): Query<ListParams>,
) -> Result<JsonData<Paginated<VideoListRow>>, AppError> {
    let repo = PgVideoRepo { pool: &state.db };
    let page = params.page.unwrap_or(1);
    let size = params.size.unwrap_or(20);
    let (items, total) = repo.list_featured(page, size).await?;
    Ok(JsonData::ok(Paginated::new(items, page, size, total)))
}

pub async fn my_videos(
    State(state): State<AppState>,
    AuthCustomer(claims): AuthCustomer,
    Query(params): Query<ListParams>,
) -> Result<JsonData<Paginated<MyVideoRow>>, AppError> {
    let repo = PgVideoRepo { pool: &state.db };
    let page = params.page.unwrap_or(1);
    let size = params.size.unwrap_or(20);
    let (items, total) = repo.list_my(claims.sub, page, size).await?;
    Ok(JsonData::ok(Paginated::new(items, page, size, total)))
}

pub async fn like(
    State(state): State<AppState>,
    Path(id): Path<i64>,
    AuthCustomer(claims): AuthCustomer,
) -> Result<JsonData<()>, AppError> {
    let repo = PgVideoRepo { pool: &state.db };
    let created = repo.like(id, claims.sub).await?;
    if !created {
        return Err(AppError::Conflict("已点赞".into()));
    }
    Ok(JsonData::ok(()))
}

pub async fn unlike(
    State(state): State<AppState>,
    Path(id): Path<i64>,
    AuthCustomer(claims): AuthCustomer,
) -> Result<JsonData<()>, AppError> {
    let repo = PgVideoRepo { pool: &state.db };
    let deleted = repo.unlike(id, claims.sub).await?;
    if !deleted {
        return Err(AppError::NotFound("未点赞".into()));
    }
    Ok(JsonData::ok(()))
}

#[derive(Deserialize)]
pub struct PlayPath {
    #[serde(rename = "videoId")]
    pub video_id: i64,
}

pub async fn play_click(
    State(state): State<AppState>,
    Path(path): Path<PlayPath>,
    OptionalAuth(claims): OptionalAuth,
) -> Result<JsonData<i64>, AppError> {
    let repo = PgVideoRepo { pool: &state.db };
    let id = repo.record_play(path.video_id, claims.map(|c| c.sub), 1).await?;
    Ok(JsonData::ok(id))
}

pub async fn play_valid(
    State(state): State<AppState>,
    Path(path): Path<PlayPath>,
    OptionalAuth(claims): OptionalAuth,
) -> Result<JsonData<i64>, AppError> {
    let repo = PgVideoRepo { pool: &state.db };
    let id = repo.record_play(path.video_id, claims.map(|c| c.sub), 2).await?;
    Ok(JsonData::ok(id))
}

// ─── Service functions ─────────────────────────

pub async fn get_video_detail(
    repo: &impl VideoRepo,
    video_id: i64,
    viewer_id: Option<i64>,
) -> Result<VideoDetailRow, AppError> {
    repo.get_detail(video_id, viewer_id)
        .await?
        .ok_or(AppError::NotFound("视频不存在".into()))
}

pub async fn delete_my_video(
    repo: &impl VideoRepo,
    video_id: i64,
    customer_id: i64,
) -> Result<(), AppError> {
    let deleted = repo.delete_video(video_id, customer_id).await?;
    if !deleted {
        return Err(AppError::BadRequest("视频不存在或无法删除".into()));
    }
    Ok(())
}

// ─── Handlers for new endpoints ────────────────

pub async fn detail(
    State(state): State<AppState>,
    Path(id): Path<i64>,
    OptionalAuth(claims): OptionalAuth,
) -> Result<JsonData<VideoDetailRow>, AppError> {
    let repo = PgVideoRepo { pool: &state.db };
    let detail = get_video_detail(&repo, id, claims.map(|c| c.sub)).await?;
    Ok(JsonData::ok(detail))
}

pub async fn delete(
    State(state): State<AppState>,
    Path(id): Path<i64>,
    AuthCustomer(claims): AuthCustomer,
) -> Result<JsonData<()>, AppError> {
    let repo = PgVideoRepo { pool: &state.db };
    delete_my_video(&repo, id, claims.sub).await?;
    Ok(JsonData::ok(()))
}

pub async fn user_videos(
    State(state): State<AppState>,
    Path(user_id): Path<i64>,
    Query(params): Query<ListParams>,
) -> Result<JsonData<Paginated<VideoListRow>>, AppError> {
    let repo = PgVideoRepo { pool: &state.db };
    let page = params.page.unwrap_or(1);
    let size = params.size.unwrap_or(20);
    let (items, total) = repo.list_user_videos(user_id, page, size).await?;
    Ok(JsonData::ok(Paginated::new(items, page, size, total)))
}

// ─── Presigned URL Upload ────────────────────

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PresignUploadRequest {
    pub file_name: String,
    pub content_type: String,
}

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct PresignUploadResponse {
    pub upload_url: String,
    pub file_url: String,
    pub s3_key: String,
}

pub async fn upload_url(
    State(state): State<AppState>,
    AuthCustomer(claims): AuthCustomer,
    Json(body): Json<PresignUploadRequest>,
) -> Result<JsonData<PresignUploadResponse>, AppError> {
    let ext = body.file_name.rsplit('.').next().unwrap_or("mp4");
    let id = uuid::Uuid::new_v4();
    let key = format!("videos/{}/{id}.{ext}", claims.sub);

    let upload_url = state
        .s3
        .presign_put(&state.config.s3.bucket_videos, &key, &body.content_type, 3600)
        .await
        .map_err(|e| AppError::Internal(e.to_string()))?;
    let file_url = state.s3.build_public_url(&state.config.s3.bucket_videos, &key);

    Ok(JsonData::ok(PresignUploadResponse {
        upload_url,
        file_url,
        s3_key: key,
    }))
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ConfirmUploadRequest {
    pub s3_key: String,
    pub file_name: String,
    pub file_size: i64,
    pub mime_type: String,
    pub title: Option<String>,
    pub description: Option<String>,
    pub duration: Option<i32>,
}

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ConfirmUploadResponse {
    pub video_id: i64,
}

pub async fn confirm_upload(
    State(state): State<AppState>,
    AuthCustomer(claims): AuthCustomer,
    Json(body): Json<ConfirmUploadRequest>,
) -> Result<JsonData<ConfirmUploadResponse>, AppError> {
    let file_url = state.s3.build_public_url(&state.config.s3.bucket_videos, &body.s3_key);
    let repo = PgVideoRepo { pool: &state.db };
    let video_id = repo
        .create_video(
            claims.sub,
            body.title.as_deref(),
            body.description.as_deref(),
            &body.file_name,
            body.file_size,
            &file_url,
            &body.mime_type,
            None,
            body.duration,
        )
        .await
        .map_err(|e| {
            tracing::error!("Failed to create video record: {e}");
            e
        })?;

    Ok(JsonData::ok(ConfirmUploadResponse { video_id }))
}

#[cfg(test)]
mod tests {
    use std::sync::Mutex;

    use gabon_shared::traits::{MyVideoRow, VideoListRow, VideoRepo};

    use super::*;

    struct MockVideoRepo {
        like_returns: Mutex<bool>,
        unlike_returns: Mutex<bool>,
    }

    impl MockVideoRepo {
        fn new() -> Self {
            Self {
                like_returns: Mutex::new(true),
                unlike_returns: Mutex::new(true),
            }
        }
    }

    impl VideoRepo for MockVideoRepo {
        async fn list_approved(&self, _page: i64, _page_size: i64, _keyword: Option<&str>) -> Result<(Vec<VideoListRow>, i64), AppError> {
            let items = vec![VideoListRow {
                id: 1, customer_id: 1, title: Some("test".into()),
                thumbnail_url: None, duration: Some(60), like_count: 10, total_clicks: 100,
            }];
            Ok((items, 1))
        }

        async fn list_featured(&self, _page: i64, _page_size: i64) -> Result<(Vec<VideoListRow>, i64), AppError> {
            Ok((vec![], 0))
        }

        async fn list_my(&self, _customer_id: i64, _page: i64, _page_size: i64) -> Result<(Vec<MyVideoRow>, i64), AppError> {
            Ok((vec![], 0))
        }

        async fn like(&self, _video_id: i64, _customer_id: i64) -> Result<bool, AppError> {
            Ok(*self.like_returns.lock().unwrap())
        }

        async fn unlike(&self, _video_id: i64, _customer_id: i64) -> Result<bool, AppError> {
            Ok(*self.unlike_returns.lock().unwrap())
        }

        async fn record_play(&self, _video_id: i64, _customer_id: Option<i64>, _play_type: i16) -> Result<i64, AppError> {
            Ok(1)
        }

        async fn get_detail(&self, video_id: i64, _viewer_id: Option<i64>) -> Result<Option<VideoDetailRow>, AppError> {
            if video_id == 1 {
                Ok(Some(VideoDetailRow {
                    id: 1, title: Some("test video".into()), description: None,
                    file_url: "https://cdn.example.com/v.mp4".into(), thumbnail_url: None,
                    duration: Some(60), like_count: 10, total_clicks: 100,
                    author_id: 42, author_name: Some("Author".into()),
                    author_avatar_url: None, author_is_vip: false,
                    viewer_liked: false, viewer_followed: false,
                }))
            } else {
                Ok(None)
            }
        }

        async fn delete_video(&self, _video_id: i64, _customer_id: i64) -> Result<bool, AppError> {
            Ok(*self.unlike_returns.lock().unwrap()) // reuse flag for simplicity
        }

        async fn list_user_videos(&self, _user_id: i64, _page: i64, _page_size: i64) -> Result<(Vec<VideoListRow>, i64), AppError> {
            Ok((vec![], 0))
        }

        async fn create_video(&self, _customer_id: i64, _title: Option<&str>, _description: Option<&str>, _file_name: &str, _file_size: i64, _file_url: &str, _mime_type: &str, _thumbnail_url: Option<&str>, _duration: Option<i32>) -> Result<i64, AppError> {
            Ok(1)
        }
    }

    // Service-level tests using mock repo

    #[tokio::test]
    async fn list_approved_returns_paginated() {
        let repo = MockVideoRepo::new();
        let (items, total) = repo.list_approved(1, 20, None).await.unwrap();
        assert_eq!(items.len(), 1);
        assert_eq!(total, 1);
        assert_eq!(items[0].title, Some("test".into()));
    }

    #[tokio::test]
    async fn like_returns_true_on_first_like() {
        let repo = MockVideoRepo::new();
        assert!(repo.like(1, 1).await.unwrap());
    }

    #[tokio::test]
    async fn like_returns_false_on_duplicate() {
        let repo = MockVideoRepo::new();
        *repo.like_returns.lock().unwrap() = false;
        assert!(!repo.like(1, 1).await.unwrap());
    }

    #[tokio::test]
    async fn unlike_returns_false_when_not_liked() {
        let repo = MockVideoRepo::new();
        *repo.unlike_returns.lock().unwrap() = false;
        assert!(!repo.unlike(1, 1).await.unwrap());
    }

    #[tokio::test]
    async fn get_detail_returns_video() {
        let repo = MockVideoRepo::new();
        let detail = get_video_detail(&repo, 1, None).await.unwrap();
        assert_eq!(detail.id, 1);
        assert_eq!(detail.author_id, 42);
    }

    #[tokio::test]
    async fn get_detail_fails_for_nonexistent() {
        let repo = MockVideoRepo::new();
        let result = get_video_detail(&repo, 999, None).await;
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn delete_video_succeeds() {
        let repo = MockVideoRepo::new();
        let result = delete_my_video(&repo, 1, 42).await;
        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn delete_video_fails_not_deletable() {
        let repo = MockVideoRepo::new();
        *repo.unlike_returns.lock().unwrap() = false;
        let result = delete_my_video(&repo, 1, 42).await;
        assert!(result.is_err());
    }

    #[test]
    fn presign_upload_response_serializes_camel_case() {
        let resp = PresignUploadResponse {
            upload_url: "https://s3.example.com/presign-put/bucket/key".into(),
            file_url: "https://s3.example.com/bucket/key".into(),
            s3_key: "videos/1/abc.mp4".into(),
        };
        let json = serde_json::to_value(&resp).unwrap();
        assert!(json["uploadUrl"].is_string());
        assert!(json["fileUrl"].is_string());
        assert_eq!(json["s3Key"], "videos/1/abc.mp4");
    }

    #[test]
    fn confirm_upload_response_serializes_camel_case() {
        let resp = ConfirmUploadResponse { video_id: 42 };
        let json = serde_json::to_value(&resp).unwrap();
        assert_eq!(json["videoId"], 42);
    }
}
