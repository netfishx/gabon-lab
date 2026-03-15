use std::env;

/// Application configuration loaded from environment variables.
/// Required fields panic on missing; optional fields have defaults.
#[derive(Debug, Clone)]
pub struct Config {
    pub port: u16,
    pub database_url: String,
    pub redis_url: String,
    pub jwt: JwtConfig,
    pub s3: S3Config,
    pub rust_log: String,
}

/// S3-compatible storage configuration (Garage / MinIO / AWS).
/// All fields optional — empty endpoint activates stub mode.
#[derive(Debug, Clone)]
pub struct S3Config {
    pub endpoint: String,
    pub region: String,
    pub access_key: String,
    pub secret_key: String,
    pub bucket_videos: String,
    pub bucket_avatars: String,
}

impl S3Config {
    /// Returns true when S3 is properly configured (endpoint is non-empty).
    pub fn is_configured(&self) -> bool {
        !self.endpoint.is_empty()
    }
}

#[derive(Debug, Clone)]
pub struct JwtConfig {
    pub customer_secret: String,
    pub customer_access_ttl: u64,
    pub customer_refresh_ttl: u64,
    pub admin_secret: String,
    pub admin_access_ttl: u64,
    pub admin_refresh_ttl: u64,
    pub current_kid: String,
}

impl Config {
    pub fn from_env() -> Self {
        Self {
            port: env_or("PORT", 3000),
            database_url: env_required("DATABASE_URL"),
            redis_url: env_or_string("REDIS_URL", "redis://localhost:6379/0"),
            jwt: JwtConfig {
                customer_secret: env_required("JWT_CUSTOMER_SECRET"),
                customer_access_ttl: env_or("JWT_CUSTOMER_ACCESS_TTL", 900),
                customer_refresh_ttl: env_or("JWT_CUSTOMER_REFRESH_TTL", 604800),
                admin_secret: env_required("JWT_ADMIN_SECRET"),
                admin_access_ttl: env_or("JWT_ADMIN_ACCESS_TTL", 900),
                admin_refresh_ttl: env_or("JWT_ADMIN_REFRESH_TTL", 604800),
                current_kid: env_or_string("JWT_CURRENT_KID", "key-2026-02"),
            },
            s3: S3Config {
                endpoint: env_or_string("S3_ENDPOINT", ""),
                region: env_or_string("S3_REGION", "garage"),
                access_key: env_or_string("S3_ACCESS_KEY", ""),
                secret_key: env_or_string("S3_SECRET_KEY", ""),
                bucket_videos: env_or_string("S3_BUCKET_VIDEOS", "gabon-videos"),
                bucket_avatars: env_or_string("S3_BUCKET_AVATARS", "gabon-avatars"),
            },
            rust_log: env_or_string("RUST_LOG", "info"),
        }
    }
}

fn env_required(key: &str) -> String {
    env::var(key).unwrap_or_else(|_| panic!("{key} must be set"))
}

fn env_or_string(key: &str, default: &str) -> String {
    env::var(key).unwrap_or_else(|_| default.to_string())
}

fn env_or<T: std::str::FromStr>(key: &str, default: T) -> T {
    env::var(key)
        .ok()
        .and_then(|v| v.parse().ok())
        .unwrap_or(default)
}
