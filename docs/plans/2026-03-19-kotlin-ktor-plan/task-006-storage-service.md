# Task 006: S3 storage service with presigned URLs

**type**: setup
**depends-on**: [002]

## Description

Implement the object storage service using AWS SDK for Kotlin, with support for Garage S3-compatible storage and a stub mode for development without S3.

Key decisions:

- **StorageService** (`service/StorageService.kt`): define an interface or class with three operations:
  - `suspend fun presignUpload(bucket: String, key: String, contentType: String): String` — generate a PUT presigned URL valid for a configured duration (e.g., 15 minutes). Clients use this URL to upload directly to S3 without going through the app server.
  - `fun buildPublicUrl(bucket: String, key: String): String` — construct the public access URL for an already-uploaded object. Format: `{endpoint}/{bucket}/{key}`.
  - `suspend fun delete(bucket: String, key: String)` — delete an object from S3. Used when cleaning up rejected videos or old avatars.

- **S3 client setup**: use `aws.sdk.kotlin:s3` (AWS SDK for Kotlin). Create `S3Client` with:
  - `endpointUrl` from S3_ENDPOINT config
  - `region` from S3_REGION config (default "garage")
  - `forcePathStyle = true` — required for Garage compatibility (Garage does not support virtual-hosted-style)
  - Static credentials from S3_ACCESS_KEY and S3_SECRET_KEY
  For presigned URLs, use `S3Presigner` (or the SDK's presigning support) to generate time-limited PUT URLs.

- **Stub mode**: if `S3_ENDPOINT` is blank or empty in config, do NOT create an S3 client. Instead, return mock URLs:
  - `presignUpload` returns `"https://stub.local/{bucket}/{key}?upload=true"`
  - `buildPublicUrl` returns `"https://stub.local/{bucket}/{key}"`
  - `delete` is a no-op
  This allows running the app locally without Garage for frontend development or API testing.

- **Bucket constants**: two predefined buckets used across the app:
  - `gabon-videos` — video files and cover images
  - `gabon-avatars` — user avatar images
  These should be defined in Constants.kt (Task 002) and referenced here.

- **Key generation**: provide a helper to generate unique object keys with path prefixes, e.g., `videos/{customerId}/{uuid}.{ext}` or `avatars/{customerId}/{uuid}.{ext}`. This keeps S3 objects organized and avoids name collisions.

- **Lifecycle**: close the S3Client on application shutdown to release HTTP connections.

## Files

- `kotlin/src/main/kotlin/lab/gabon/service/StorageService.kt` — StorageService with presignUpload, buildPublicUrl, delete; S3Client setup with Garage-compatible config; stub mode fallback; key generation helper; shutdown hook

## Verification

1. `./gradlew build` compiles without errors
2. **Stub mode**: start app without S3_ENDPOINT set, call presignUpload("gabon-videos", "test/file.mp4", "video/mp4") — returns `"https://stub.local/gabon-videos/test/file.mp4?upload=true"`
3. **Live mode**: with Garage running (`make up && make init-storage`), start app with S3 config, call presignUpload — returns a valid HTTP URL pointing to localhost:3900 with query parameters (X-Amz-Signature, etc.)
4. **Live mode**: use the presigned URL to PUT a small test file with curl, then verify buildPublicUrl returns an accessible URL
5. **Delete**: upload a test object, call delete, verify the object is no longer accessible
