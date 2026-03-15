use std::net::SocketAddr;

use axum::routing::{get, post, put};
use axum::Router;
use deadpool_redis::{Config as RedisConfig, Runtime};
use sqlx::PgPool;
use tower_http::compression::CompressionLayer;
use tower_http::timeout::TimeoutLayer;
use tower_http::trace::TraceLayer;
use tracing_subscriber::EnvFilter;

use gabon_infra::db;
use gabon_infra::s3_storage::S3Storage;
use gabon_shared::config::Config;
use gabon_shared::response::JsonData;

mod activity;
mod admin;
mod auth;
mod customer;
mod middleware;
mod rate_limit;
mod service;
mod social;
mod token;
mod video;

#[derive(Clone)]
pub struct AppState {
    pub db: PgPool,
    pub redis: deadpool_redis::Pool,
    pub s3: S3Storage,
    pub config: Config,
}

#[tokio::main]
async fn main() {
    dotenvy::dotenv().ok();

    let config = Config::from_env();

    tracing_subscriber::fmt()
        .with_env_filter(
            EnvFilter::try_from_default_env()
                .unwrap_or_else(|_| config.rust_log.clone().into()),
        )
        .json()
        .init();

    let pool = db::create_pool(&config.database_url).await;
    sqlx::migrate!("../../migrations")
        .run(&pool)
        .await
        .expect("Failed to run migrations");

    let redis = RedisConfig::from_url(&config.redis_url)
        .create_pool(Some(Runtime::Tokio1))
        .expect("Failed to create Redis pool");

    let s3 = S3Storage::new(&config.s3);

    let addr = SocketAddr::from(([0, 0, 0, 0], config.port));
    let state = AppState {
        db: pool,
        redis,
        s3,
        config,
    };

    // Upload routes: no timeout (large file transfers can exceed 30s)
    let upload_routes = Router::new()
        .route("/api/videos/upload", post(video::upload))
        .route("/api/users/me/avatar", post(customer::upload_avatar));

    // All other routes: 30s timeout
    let api_routes = Router::new()
        .route("/health", get(health))
        .route("/api/auth/register", post(auth::register))
        .route("/api/auth/login", post(auth::login))
        .route("/api/auth/me", get(auth::me))
        .route("/api/auth/password", put(auth::change_password))
        .route("/api/auth/refresh", post(token::refresh_handler))
        .route("/api/auth/logout", post(token::logout_handler))
        // Videos
        .route("/api/videos", get(video::list))
        .route("/api/videos/featured", get(video::featured))
        .route("/api/videos/my", get(video::my_videos))
        .route("/api/videos/{id}", get(video::detail).delete(video::delete))
        .route("/api/videos/{id}/like", post(video::like).delete(video::unlike))
        .route("/api/videos/{videoId}/play-click", post(video::play_click))
        .route("/api/videos/{videoId}/play-valid", post(video::play_valid))
        // Activity
        .route("/api/activity/sign-in", post(activity::sign_in_handler))
        .route("/api/tasks", get(activity::list_tasks_handler))
        .route("/api/tasks/claim/{progressId}", post(activity::claim_handler))
        // Users (me)
        .route("/api/users/me/profile", get(customer::get_my_profile).put(customer::update_my_profile))
        // Users (by id)
        .route("/api/users/{id}/following", get(customer::get_user_following))
        .route("/api/users/{id}/followers", get(customer::get_user_followers))
        .route("/api/users/{id}/videos", get(video::user_videos))
        // Admin
        .route("/admin/auth/login", post(admin::login_handler))
        .route("/admin/auth/me", get(admin::me_handler))
        .route("/admin/auth/refresh", post(admin::admin_refresh_handler))
        .route("/admin/auth/logout", post(admin::admin_logout_handler))
        .route("/admin/videos", get(admin::list_videos_handler))
        .route("/admin/videos/{id}", get(admin::get_video_detail_handler).delete(admin::delete_video_handler))
        .route("/admin/videos/{id}/review", post(admin::review_handler))
        .route("/admin/customers", get(admin::list_customers_handler))
        .route("/admin/customers/{id}/password", put(admin::change_customer_password_handler))
        .route("/admin/admin-users", get(admin::list_admins_handler).post(admin::create_admin_handler))
        .route("/admin/admin-users/{id}", get(admin::get_admin_handler).put(admin::update_admin_handler).delete(admin::delete_admin_handler))
        .route("/admin/admin-users/{id}/password", put(admin::change_admin_password_handler))
        .route("/admin/reports/revenue", get(admin::revenue_report_handler))
        .route("/admin/reports/video/daily", get(admin::video_daily_report_handler))
        .route("/admin/reports/video/summary", get(admin::video_summary_handler))
        // Customer (legacy paths)
        .route("/api/customer/following", get(customer::get_following))
        .route("/api/customer/followers", get(customer::get_followers))
        .route("/api/customer/{userId}/profile", get(customer::get_profile))
        .route("/api/customer/{userId}/follow", post(social::follow_handler).delete(social::unfollow_handler))
        .layer(TimeoutLayer::with_status_code(
            axum::http::StatusCode::REQUEST_TIMEOUT,
            std::time::Duration::from_secs(30),
        ));

    // Merge: upload (no timeout) + api (with timeout), then shared layers
    let app = Router::new()
        .merge(upload_routes)
        .merge(api_routes)
        .layer(TraceLayer::new_for_http())
        .layer(CompressionLayer::new())
        .layer(axum::middleware::from_fn_with_state(
            state.clone(),
            rate_limit::rate_limit,
        ))
        .with_state(state);

    tracing::info!("listening on {addr}");
    let listener = tokio::net::TcpListener::bind(addr).await.unwrap();
    axum::serve(
        listener,
        app.into_make_service_with_connect_info::<SocketAddr>(),
    )
    .await
    .unwrap();
}

async fn health() -> JsonData<&'static str> {
    JsonData::ok("ok")
}
