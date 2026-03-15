use aws_config::Region;
use aws_sdk_s3::config::Credentials;
use aws_sdk_s3::primitives::ByteStream;
use aws_sdk_s3::Client;
use tracing::info;

use gabon_shared::config::S3Config;

/// S3-compatible object storage client (Garage / MinIO / AWS).
/// Falls back to stub URLs when endpoint is unconfigured.
#[derive(Clone)]
pub struct S3Storage {
    client: Option<Client>,
    endpoint: String,
}

impl S3Storage {
    /// Create a new S3Storage from config.
    /// When `config.endpoint` is empty, all operations return stub URLs.
    pub fn new(config: &S3Config) -> Self {
        if !config.is_configured() {
            info!("S3 endpoint not configured — running in stub mode");
            return Self {
                client: None,
                endpoint: String::new(),
            };
        }

        let credentials = Credentials::new(
            &config.access_key,
            &config.secret_key,
            None,
            None,
            "gabon-env",
        );

        let s3_config = aws_sdk_s3::Config::builder()
            .region(Region::new(config.region.clone()))
            .endpoint_url(&config.endpoint)
            .credentials_provider(credentials)
            .force_path_style(true)
            .behavior_version_latest()
            .build();

        let client = Client::from_conf(s3_config);

        info!(endpoint = %config.endpoint, "S3 storage initialized");

        Self {
            client: Some(client),
            endpoint: config.endpoint.clone(),
        }
    }

    /// Upload an object to the given bucket.
    /// Returns the public URL of the uploaded object.
    pub async fn upload(
        &self,
        bucket: &str,
        key: &str,
        content_type: &str,
        body: Vec<u8>,
    ) -> Result<String, S3Error> {
        let Some(client) = &self.client else {
            return Ok(format!(
                "https://s3.example.com/{bucket}/{key}"
            ));
        };

        client
            .put_object()
            .bucket(bucket)
            .key(key)
            .content_type(content_type)
            .body(ByteStream::from(body))
            .send()
            .await
            .map_err(|e| S3Error::Upload(e.to_string()))?;

        Ok(format!("{}/{bucket}/{key}", self.endpoint))
    }

    /// Delete an object from the given bucket.
    pub async fn delete(&self, bucket: &str, key: &str) -> Result<(), S3Error> {
        let Some(client) = &self.client else {
            return Ok(());
        };

        client
            .delete_object()
            .bucket(bucket)
            .key(key)
            .send()
            .await
            .map_err(|e| S3Error::Delete(e.to_string()))?;

        Ok(())
    }
}

#[derive(Debug, thiserror::Error)]
pub enum S3Error {
    #[error("S3 upload failed: {0}")]
    Upload(String),

    #[error("S3 delete failed: {0}")]
    Delete(String),
}

#[cfg(test)]
mod tests {
    use super::*;
    use gabon_shared::config::S3Config;

    fn stub_config() -> S3Config {
        S3Config {
            endpoint: String::new(),
            region: "garage".into(),
            access_key: String::new(),
            secret_key: String::new(),
            bucket_videos: "test-videos".into(),
            bucket_avatars: "test-avatars".into(),
        }
    }

    #[test]
    fn stub_mode_when_endpoint_empty() {
        let storage = S3Storage::new(&stub_config());
        assert!(storage.client.is_none());
    }

    #[test]
    fn configured_mode_when_endpoint_set() {
        let mut config = stub_config();
        config.endpoint = "http://localhost:3900".into();
        config.access_key = "test-key".into();
        config.secret_key = "test-secret".into();
        let storage = S3Storage::new(&config);
        assert!(storage.client.is_some());
    }

    #[tokio::test]
    async fn stub_upload_returns_example_url() {
        let storage = S3Storage::new(&stub_config());
        let url = storage
            .upload("test-bucket", "test.mp4", "video/mp4", vec![0u8; 10])
            .await
            .unwrap();
        assert_eq!(url, "https://s3.example.com/test-bucket/test.mp4");
    }

    #[tokio::test]
    async fn stub_delete_succeeds() {
        let storage = S3Storage::new(&stub_config());
        storage.delete("test-bucket", "test.mp4").await.unwrap();
    }
}
