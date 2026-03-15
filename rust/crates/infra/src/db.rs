use sqlx::postgres::PgPoolOptions;
use sqlx::PgPool;

/// # Panics
///
/// Panics if the database connection cannot be established.
pub async fn create_pool(database_url: &str) -> PgPool {
    PgPoolOptions::new()
        .max_connections(20)
        .min_connections(0)
        .connect(database_url)
        .await
        .expect("Failed to create database pool")
}
