use crate::error::AppError;

/// Customer data operations for auth flows.
#[allow(async_fn_in_trait)]
pub trait AuthRepo {
    async fn find_by_username(&self, username: &str) -> Result<Option<CustomerRow>, AppError>;
    async fn find_by_id(&self, id: i64) -> Result<Option<CustomerRow>, AppError>;
    async fn create(&self, username: &str, password_hash: &str) -> Result<CustomerRow, AppError>;
    async fn update_last_login(&self, id: i64) -> Result<(), AppError>;
    async fn change_password(&self, id: i64, new_password_hash: &str) -> Result<(), AppError>;
    async fn update_profile(
        &self,
        id: i64,
        name: Option<&str>,
        phone: Option<&str>,
        email: Option<&str>,
        signature: Option<&str>,
    ) -> Result<CustomerRow, AppError>;
    async fn update_avatar(&self, id: i64, avatar_url: &str) -> Result<(), AppError>;
}

/// Minimal row type returned by `AuthRepo` — decoupled from domain entity.
#[derive(Debug, Clone, sqlx::FromRow)]
pub struct CustomerRow {
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
}

/// Video data operations.
#[allow(async_fn_in_trait)]
pub trait VideoRepo {
    async fn list_approved(
        &self,
        page: i64,
        page_size: i64,
        keyword: Option<&str>,
    ) -> Result<(Vec<VideoListRow>, i64), AppError>;
    async fn list_featured(
        &self,
        page: i64,
        page_size: i64,
    ) -> Result<(Vec<VideoListRow>, i64), AppError>;
    async fn list_my(&self, customer_id: i64, page: i64, page_size: i64) -> Result<(Vec<MyVideoRow>, i64), AppError>;
    async fn like(&self, video_id: i64, customer_id: i64) -> Result<bool, AppError>;
    async fn unlike(&self, video_id: i64, customer_id: i64) -> Result<bool, AppError>;
    async fn record_play(
        &self,
        video_id: i64,
        customer_id: Option<i64>,
        play_type: i16,
    ) -> Result<i64, AppError>;
    async fn get_detail(&self, video_id: i64, viewer_id: Option<i64>) -> Result<Option<VideoDetailRow>, AppError>;
    /// Delete video. Only `PENDING_REVIEW(3)` videos owned by customer can be deleted.
    async fn delete_video(&self, video_id: i64, customer_id: i64) -> Result<bool, AppError>;
    async fn list_user_videos(&self, user_id: i64) -> Result<Vec<VideoListRow>, AppError>;
    async fn create_video(
        &self,
        customer_id: i64,
        title: Option<&str>,
        file_url: &str,
        thumbnail_url: Option<&str>,
        duration: Option<i32>,
    ) -> Result<i64, AppError>;
}

#[derive(Debug, Clone, serde::Serialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct VideoListRow {
    pub id: i64,
    pub customer_id: i64,
    pub title: Option<String>,
    pub thumbnail_url: Option<String>,
    pub duration: Option<i32>,
    pub like_count: i64,
    pub total_clicks: i64,
}

#[derive(Debug, Clone, serde::Serialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct MyVideoRow {
    pub id: i64,
    pub title: Option<String>,
    pub thumbnail_url: Option<String>,
    pub file_url: String,
    pub duration: Option<i32>,
    pub status: i16,
    pub like_count: i64,
    pub total_clicks: i64,
    pub valid_clicks: i64,
}

/// Video detail (assembled from video + author + interaction).
#[derive(Debug, Clone, serde::Serialize)]
#[serde(rename_all = "camelCase")]
pub struct VideoDetailRow {
    pub id: i64,
    pub title: Option<String>,
    pub description: Option<String>,
    pub file_url: String,
    pub thumbnail_url: Option<String>,
    pub duration: Option<i32>,
    pub like_count: i64,
    pub total_clicks: i64,
    pub author_id: i64,
    pub author_name: Option<String>,
    pub author_avatar_url: Option<String>,
    pub author_is_vip: bool,
    pub viewer_liked: bool,
    pub viewer_followed: bool,
}

/// Social relationship operations.
#[allow(async_fn_in_trait)]
pub trait SocialRepo {
    async fn follow(&self, follower_id: i64, followed_id: i64) -> Result<bool, AppError>;
    async fn unfollow(&self, follower_id: i64, followed_id: i64) -> Result<bool, AppError>;
    async fn get_following(&self, customer_id: i64, page: i64, page_size: i64) -> Result<(Vec<FollowRow>, i64), AppError>;
    async fn get_followers(&self, customer_id: i64, page: i64, page_size: i64) -> Result<(Vec<FollowRow>, i64), AppError>;
}

/// Follow relationship row with user info.
#[derive(Debug, Clone, serde::Serialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct FollowRow {
    pub id: i64,
    pub name: Option<String>,
    pub avatar_url: Option<String>,
    pub is_vip: bool,
}

/// Activity / task system operations.
#[allow(async_fn_in_trait)]
pub trait ActivityRepo {
    /// Check if user already signed in today. Returns true if already signed.
    async fn has_signed_in_today(&self, customer_id: i64, period_key: &str) -> Result<bool, AppError>;
    /// Record sign-in and award diamonds. Returns diamonds awarded.
    async fn record_sign_in(&self, customer_id: i64, period_key: &str, diamonds: i64) -> Result<i64, AppError>;
    /// List task progress for a customer.
    async fn list_tasks(&self, customer_id: i64) -> Result<Vec<TaskProgressRow>, AppError>;
    /// Get task progress by id. Returns None if not found.
    async fn get_task_progress(&self, progress_id: i64) -> Result<Option<TaskProgressRow>, AppError>;
    /// Mark task as claimed and award diamonds. Returns diamonds awarded.
    async fn claim_task(&self, progress_id: i64, customer_id: i64, diamonds: i64) -> Result<(), AppError>;
}

/// Task status: `1=in_progress`, `2=completed`, `3=claimed`, `4=expired`
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum TaskStatus {
    InProgress = 1,
    Completed = 2,
    Claimed = 3,
    Expired = 4,
}

#[derive(Debug, Clone, serde::Serialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct TaskProgressRow {
    pub id: i64,
    pub customer_id: i64,
    pub task_id: i64,
    pub current_count: i32,
    pub target_count: i32,
    pub task_status: i16,
    pub reward_diamonds: i32,
}

/// Admin user data operations.
#[allow(async_fn_in_trait)]
pub trait AdminRepo {
    async fn find_admin_by_username(&self, username: &str) -> Result<Option<AdminRow>, AppError>;
    async fn find_admin_by_id(&self, id: i64) -> Result<Option<AdminRow>, AppError>;
    async fn update_admin_last_login(&self, id: i64) -> Result<(), AppError>;
    /// Review a video: set status to approved(4) or rejected(5).
    /// Only videos with `status=PENDING_REVIEW(3)` can be reviewed.
    /// Returns true if updated, false if video not in reviewable state.
    async fn review_video(&self, video_id: i64, admin_id: i64, status: i16, notes: Option<&str>) -> Result<bool, AppError>;
    /// Admin video list with filters (status, pagination).
    async fn list_videos(&self, page: i64, page_size: i64, status: Option<i16>) -> Result<(Vec<AdminVideoRow>, i64), AppError>;
    /// Soft-delete video (admin).
    async fn delete_video(&self, video_id: i64) -> Result<bool, AppError>;
    /// List customers with optional name filter.
    async fn list_customers(&self, page: i64, page_size: i64, name: Option<&str>) -> Result<(Vec<AdminCustomerRow>, i64), AppError>;
    /// Change customer password (admin, no old password required).
    async fn change_customer_password(&self, customer_id: i64, new_hash: &str) -> Result<bool, AppError>;
    /// CRUD: list admin users.
    async fn list_admins(&self, page: i64, page_size: i64) -> Result<(Vec<AdminRow>, i64), AppError>;
    /// CRUD: create admin user.
    async fn create_admin(&self, username: &str, password_hash: &str, role: i16, full_name: Option<&str>) -> Result<AdminRow, AppError>;
    /// CRUD: delete admin user (soft delete).
    async fn delete_admin(&self, id: i64) -> Result<bool, AppError>;
    /// CRUD: update admin user (role, `full_name`, status).
    async fn update_admin(&self, id: i64, role: i16, full_name: Option<&str>, status: i16) -> Result<bool, AppError>;
    /// Change admin password.
    async fn change_admin_password(&self, id: i64, new_hash: &str) -> Result<bool, AppError>;
    /// Get single video detail for admin review (any status).
    async fn get_video_detail(&self, video_id: i64) -> Result<Option<AdminVideoDetailRow>, AppError>;
}

/// Admin role: 1=admin, 2=normal
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum AdminRole {
    Admin = 1,
    Normal = 2,
}

#[derive(Debug, Clone, serde::Serialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct AdminRow {
    pub id: i64,
    pub username: String,
    #[serde(skip_serializing)]
    pub password_hash: String,
    pub role: i16,
    pub full_name: Option<String>,
    pub status: i16,
}

#[derive(Debug, Clone, serde::Serialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct AdminVideoRow {
    pub id: i64,
    pub title: Option<String>,
    pub uploader_name: Option<String>,
    pub status: i16,
    pub like_count: i64,
    pub total_clicks: i64,
}

#[derive(Debug, Clone, serde::Serialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct AdminVideoDetailRow {
    pub id: i64,
    pub title: Option<String>,
    pub description: Option<String>,
    pub file_url: String,
    pub thumbnail_url: Option<String>,
    pub duration: Option<i32>,
    pub status: i16,
    pub like_count: i64,
    pub total_clicks: i64,
    pub valid_clicks: i64,
    pub uploader_id: i64,
    pub uploader_name: Option<String>,
    pub review_notes: Option<String>,
}

#[derive(Debug, Clone, serde::Serialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct AdminCustomerRow {
    pub id: i64,
    pub username: String,
    pub name: Option<String>,
    pub is_vip: bool,
    pub diamond_balance: i64,
}

/// Report queries.
#[allow(async_fn_in_trait)]
pub trait ReportRepo {
    async fn revenue_report(&self, page: i64, page_size: i64) -> Result<(Vec<RevenueRow>, i64), AppError>;
    async fn video_daily_report(&self, page: i64, page_size: i64) -> Result<(Vec<VideoDailyRow>, i64), AppError>;
    async fn video_summary(&self) -> Result<VideoSummaryRow, AppError>;
}

#[derive(Debug, Clone, serde::Serialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct RevenueRow {
    pub date: String,
    pub total_diamonds: i64,
    pub claim_count: i64,
}

#[derive(Debug, Clone, serde::Serialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct VideoDailyRow {
    pub date: String,
    pub video_count: i64,
    pub total_clicks: i64,
    pub valid_clicks: i64,
}

#[derive(Debug, Clone, serde::Serialize, sqlx::FromRow)]
#[serde(rename_all = "camelCase")]
pub struct VideoSummaryRow {
    pub total_videos: i64,
    pub approved_videos: i64,
    pub pending_videos: i64,
    pub total_clicks: i64,
}

/// Token store for refresh tokens and access token blacklist (Redis).
#[allow(async_fn_in_trait)]
pub trait TokenStore {
    /// Store refresh token → `user_id` mapping with TTL.
    async fn store_refresh_token(&self, token: &str, user_id: i64, ttl_secs: u64) -> Result<(), AppError>;
    /// Get `user_id` for refresh token. Returns None if expired/missing.
    async fn get_refresh_token_user(&self, token: &str) -> Result<Option<i64>, AppError>;
    /// Delete refresh token (consumed after use).
    async fn delete_refresh_token(&self, token: &str) -> Result<(), AppError>;
    /// Atomically consume old refresh token and issue new one (CAS rotation).
    /// Returns `Some(user_id)` on success, `None` if old token missing/expired.
    async fn rotate_refresh_token(
        &self,
        old_token: &str,
        new_token: &str,
        ttl_secs: u64,
    ) -> Result<Option<i64>, AppError>;
    /// Blacklist an access token for remaining TTL.
    async fn blacklist_access_token(&self, token: &str, ttl_secs: u64) -> Result<(), AppError>;
    /// Mark all refresh tokens for a user as revoked (logout invalidation).
    async fn revoke_user_sessions(&self, user_id: i64, ttl_secs: u64) -> Result<(), AppError>;
    /// Check if a user's sessions have been revoked.
    async fn is_user_revoked(&self, user_id: i64) -> Result<bool, AppError>;
    /// Check if access token is blacklisted.
    async fn is_blacklisted(&self, token: &str) -> Result<bool, AppError>;
}
