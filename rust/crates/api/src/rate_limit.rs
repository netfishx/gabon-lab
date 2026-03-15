use axum::body::Body;
use axum::extract::State;
use axum::http::{Request, StatusCode};
use axum::middleware::Next;
use axum::response::{IntoResponse, Response};
use deadpool_redis::redis;

use crate::AppState;

const LIMIT: i64 = 200;
const WINDOW_SECS: u64 = 60;

/// Global per-IP rate limiter: 200 requests/minute sliding window (Redis sorted set).
pub async fn rate_limit(
    State(state): State<AppState>,
    request: Request<Body>,
    next: Next,
) -> Response {
    let ip = request
        .headers()
        .get("x-forwarded-for")
        .and_then(|v| v.to_str().ok())
        .and_then(|s| s.split(',').next())
        .map(str::trim)
        .or_else(|| {
            request
                .headers()
                .get("x-real-ip")
                .and_then(|v| v.to_str().ok())
        })
        .unwrap_or("unknown")
        .to_string();

    if let Err(resp) = check_limit(&state.redis, &ip).await {
        return resp;
    }

    next.run(request).await
}

async fn check_limit(pool: &deadpool_redis::Pool, ip: &str) -> Result<(), Response> {
    let mut conn = pool
        .get()
        .await
        .map_err(|_| StatusCode::SERVICE_UNAVAILABLE.into_response())?;

    let key = format!("rl:{ip}");
    let now_us = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .expect("system clock")
        .as_micros();
    let window_start = now_us.saturating_sub(u128::from(WINDOW_SECS) * 1_000_000);
    let member = uuid::Uuid::new_v4().to_string();

    let (count,): (i64,) = redis::pipe()
        .cmd("ZREMRANGEBYSCORE")
        .arg(&key)
        .arg(0u64)
        .arg(window_start as u64)
        .ignore()
        .cmd("ZADD")
        .arg(&key)
        .arg(now_us as f64)
        .arg(&member)
        .ignore()
        .cmd("ZCARD")
        .arg(&key)
        .cmd("EXPIRE")
        .arg(&key)
        .arg(WINDOW_SECS + 1)
        .ignore()
        .query_async(&mut conn)
        .await
        .map_err(|_| StatusCode::SERVICE_UNAVAILABLE.into_response())?;

    if count > LIMIT {
        return Err(StatusCode::TOO_MANY_REQUESTS.into_response());
    }

    Ok(())
}
