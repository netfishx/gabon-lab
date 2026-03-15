//! Infrastructure adapters: `PostgreSQL` repositories, Redis token store, S3 storage.

pub mod activity_repo;
pub mod admin_repo;
pub mod customer_repo;
pub mod db;
pub mod redis_store;
pub mod report_repo;
pub mod s3_storage;
pub mod social_repo;
pub mod video_repo;
