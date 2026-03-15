use axum::extract::{Multipart, Path, Query, State};
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
) -> Result<JsonData<Vec<MyVideoRow>>, AppError> {
    let repo = PgVideoRepo { pool: &state.db };
    let items = repo.list_my(claims.sub).await?;
    Ok(JsonData::ok(items))
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
) -> Result<JsonData<Vec<VideoListRow>>, AppError> {
    let repo = PgVideoRepo { pool: &state.db };
    let items = repo.list_user_videos(user_id).await?;
    Ok(JsonData::ok(items))
}

// ─── Upload ───────────────────────────────────

const MAX_VIDEO_SIZE: usize = 200 * 1024 * 1024; // 200 MB

#[derive(serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UploadResponse {
    pub resource_url: String,
}

pub async fn upload(
    State(state): State<AppState>,
    AuthCustomer(_claims): AuthCustomer,
    mut multipart: Multipart,
) -> Result<JsonData<UploadResponse>, AppError> {
    let field = multipart
        .next_field()
        .await
        .map_err(|e| AppError::BadRequest(format!("multipart 解析失败: {e}")))?
        .ok_or_else(|| AppError::BadRequest("缺少文件字段".into()))?;

    let content_type = field
        .content_type()
        .unwrap_or("video/mp4")
        .to_string();

    let data = field
        .bytes()
        .await
        .map_err(|e| AppError::BadRequest(format!("读取文件失败: {e}")))?;

    if data.len() > MAX_VIDEO_SIZE {
        return Err(AppError::BadRequest(format!(
            "文件过大，最大 {}MB",
            MAX_VIDEO_SIZE / 1024 / 1024
        )));
    }

    let ext = mime_to_ext(&content_type);
    let id = uuid::Uuid::new_v4();
    let key = format!("videos/{id}.{ext}");

    let resource_url = state
        .s3
        .upload(
            &state.config.s3.bucket_videos,
            &key,
            &content_type,
            data.to_vec(),
        )
        .await
        .map_err(|e| AppError::Internal(e.to_string()))?;

    Ok(JsonData::ok(UploadResponse { resource_url }))
}

fn mime_to_ext(content_type: &str) -> &str {
    match content_type {
        "video/webm" => "webm",
        "video/quicktime" => "mov",
        _ => "mp4",
    }
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

        async fn list_my(&self, _customer_id: i64) -> Result<Vec<MyVideoRow>, AppError> {
            Ok(vec![])
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

        async fn list_user_videos(&self, _user_id: i64) -> Result<Vec<VideoListRow>, AppError> {
            Ok(vec![])
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
    fn upload_response_serializes_camel_case() {
        let resp = UploadResponse {
            resource_url: "https://cdn.example.com/videos/123.mp4".into(),
        };
        let json = serde_json::to_value(&resp).unwrap();
        assert!(json["resourceUrl"].is_string());
    }

    #[test]
    fn mime_to_ext_maps_correctly() {
        assert_eq!(mime_to_ext("video/mp4"), "mp4");
        assert_eq!(mime_to_ext("video/webm"), "webm");
        assert_eq!(mime_to_ext("video/quicktime"), "mov");
        assert_eq!(mime_to_ext("application/octet-stream"), "mp4");
    }
}
