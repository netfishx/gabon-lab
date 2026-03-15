package service

import (
	"context"
	"fmt"
	"io"
	"strings"
	"time"

	"github.com/aws/aws-sdk-go-v2/aws"
	"github.com/aws/aws-sdk-go-v2/credentials"
	"github.com/aws/aws-sdk-go-v2/service/s3"

	"gabon-go/internal/config"
	"gabon-go/internal/model"
)

// StorageService handles file storage via S3-compatible API.
// When endpoint is empty, operates in stub mode (returns mock URLs without uploading).
type StorageService struct {
	client        *s3.Client
	endpoint      string
	bucketVideos  string
	bucketAvatars string
	stub          bool
}

func NewStorageService(cfg *config.S3Config) *StorageService {
	if cfg.Endpoint == "" {
		return &StorageService{
			stub:          true,
			bucketVideos:  cfg.BucketVideos,
			bucketAvatars: cfg.BucketAvatars,
		}
	}

	endpoint := strings.TrimRight(cfg.Endpoint, "/")

	client := s3.New(s3.Options{
		BaseEndpoint: aws.String(endpoint),
		Region:       cfg.Region,
		Credentials:  credentials.NewStaticCredentialsProvider(cfg.AccessKey, cfg.SecretKey, ""),
		UsePathStyle: true, // Garage uses path-style, not virtual-hosted
	})

	return &StorageService{
		client:        client,
		endpoint:      endpoint,
		bucketVideos:  cfg.BucketVideos,
		bucketAvatars: cfg.BucketAvatars,
	}
}

// BucketVideos returns the configured videos bucket name.
func (s *StorageService) BucketVideos() string {
	return s.bucketVideos
}

// BucketAvatars returns the configured avatars bucket name.
func (s *StorageService) BucketAvatars() string {
	return s.bucketAvatars
}

// Upload sends a file to S3 and returns its public URL.
// In stub mode, returns a mock URL.
func (s *StorageService) Upload(ctx context.Context, bucket, path, contentType string, body io.Reader) (string, error) {
	if s.stub {
		return fmt.Sprintf("https://stub.local/storage/%s/%s", bucket, path), nil
	}

	_, err := s.client.PutObject(ctx, &s3.PutObjectInput{
		Bucket:      aws.String(bucket),
		Key:         aws.String(path),
		Body:        body,
		ContentType: aws.String(contentType),
	})
	if err != nil {
		return "", model.WrapError(model.ErrInternal, "failed to upload file to S3", err)
	}

	publicURL := fmt.Sprintf("%s/%s/%s", s.endpoint, bucket, path)
	return publicURL, nil
}

// GeneratePresignedUploadURL generates a presigned PUT URL for direct client upload.
func (s *StorageService) GeneratePresignedUploadURL(ctx context.Context, bucket, key, contentType string, durationMins int) (string, error) {
	if s.stub {
		return fmt.Sprintf("https://stub.local/presign-put/%s/%s", bucket, key), nil
	}

	presignClient := s3.NewPresignClient(s.client)
	req, err := presignClient.PresignPutObject(ctx, &s3.PutObjectInput{
		Bucket:      aws.String(bucket),
		Key:         aws.String(key),
		ContentType: aws.String(contentType),
	}, func(opts *s3.PresignOptions) {
		opts.Expires = time.Duration(durationMins) * time.Minute
	})
	if err != nil {
		return "", model.WrapError(model.ErrInternal, "failed to generate presigned URL", err)
	}
	return req.URL, nil
}

// BuildPublicURL returns the public URL for an object.
func (s *StorageService) BuildPublicURL(bucket, key string) string {
	if s.stub {
		return fmt.Sprintf("https://stub.local/storage/%s/%s", bucket, key)
	}
	return fmt.Sprintf("%s/%s/%s", s.endpoint, bucket, key)
}

// Delete removes a file from S3. No-op in stub mode.
func (s *StorageService) Delete(ctx context.Context, bucket, path string) error {
	if s.stub {
		return nil
	}

	_, err := s.client.DeleteObject(ctx, &s3.DeleteObjectInput{
		Bucket: aws.String(bucket),
		Key:    aws.String(path),
	})
	if err != nil {
		return model.WrapError(model.ErrInternal, "failed to delete file from S3", err)
	}

	return nil
}
