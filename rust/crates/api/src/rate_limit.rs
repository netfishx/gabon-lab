use std::net::SocketAddr;

use axum::body::Body;
use axum::extract::connect_info::ConnectInfo;
use axum::extract::State;
use axum::http::{Request, StatusCode, header::HeaderValue};
use axum::middleware::Next;
use axum::response::{IntoResponse, Response};
use deadpool_redis::redis;

use gabon_shared::response::JsonData;

use crate::AppState;

const LIMIT: i64 = 200;
const WINDOW_SECS: u64 = 60;

/// Global per-IP rate limiter: 200 requests/minute sliding window (Redis sorted set).
pub async fn rate_limit(
    State(state): State<AppState>,
    request: Request<Body>,
    next: Next,
) -> Response {
    let ip = resolve_client_ip(&request);

    match check_limit(&state.redis, &ip).await {
        Ok(remaining) => {
            let mut response = next.run(request).await;
            set_rate_headers(response.headers_mut(), remaining);
            response
        }
        Err(resp) => resp,
    }
}

/// Resolve client IP: X-Forwarded-For → X-Real-IP → peer address.
fn resolve_client_ip(req: &Request<Body>) -> String {
    if let Some(forwarded) = req
        .headers()
        .get("x-forwarded-for")
        .and_then(|v| v.to_str().ok())
        .and_then(|s| s.split(',').next())
        .map(str::trim)
    {
        return forwarded.to_string();
    }

    if let Some(real_ip) = req
        .headers()
        .get("x-real-ip")
        .and_then(|v| v.to_str().ok())
    {
        return real_ip.to_string();
    }

    req.extensions()
        .get::<ConnectInfo<SocketAddr>>()
        .map_or_else(|| "0.0.0.0".into(), |ci| ci.0.ip().to_string())
}

fn set_rate_headers(headers: &mut axum::http::HeaderMap, remaining: i64) {
    if let Ok(v) = HeaderValue::from_str(&LIMIT.to_string()) {
        headers.insert("X-RateLimit-Limit", v);
    }
    if let Ok(v) = HeaderValue::from_str(&remaining.to_string()) {
        headers.insert("X-RateLimit-Remaining", v);
    }
}

async fn check_limit(pool: &deadpool_redis::Pool, ip: &str) -> Result<i64, Response> {
    let mut conn = pool
        .get()
        .await
        .map_err(|_| StatusCode::SERVICE_UNAVAILABLE.into_response())?;

    let key = format!("rl:{ip}");
    let now_us = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .expect("system clock")
        .as_micros();
    let now_us_u64 = u64::try_from(now_us).unwrap_or(u64::MAX);
    let window_start = now_us_u64.saturating_sub(WINDOW_SECS * 1_000_000);

    let (count,): (i64,) = redis::pipe()
        .cmd("ZREMRANGEBYSCORE")
        .arg(&key)
        .arg(0u64)
        .arg(window_start)
        .ignore()
        .cmd("ZADD")
        .arg(&key)
        .arg(now_us_u64)
        .arg(now_us_u64.to_string())
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
        let remaining = 0i64;
        let mut resp = (
            StatusCode::TOO_MANY_REQUESTS,
            axum::Json(JsonData::<()>::error(429, "请求过于频繁，请稍后重试".into())),
        )
            .into_response();
        set_rate_headers(resp.headers_mut(), remaining);
        if let Ok(v) = HeaderValue::from_str(&WINDOW_SECS.to_string()) {
            resp.headers_mut().insert("Retry-After", v);
        }
        return Err(resp);
    }

    Ok((LIMIT - count).max(0))
}
