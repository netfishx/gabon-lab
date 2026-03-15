package service

import (
	"context"
	"fmt"
	"path/filepath"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgtype"

	"gabon-go/internal/model"
	"gabon-go/internal/repository"
)

type VideoRepo interface {
	CreateVideo(ctx context.Context, arg repository.CreateVideoParams) (repository.Video, error)
	GetVideoByID(ctx context.Context, id int64) (repository.Video, error)
	ListVideos(ctx context.Context, arg repository.ListVideosParams) ([]repository.ListVideosRow, error)
	CountVideos(ctx context.Context, keyword pgtype.Text) (int64, error)
	ListFeaturedVideos(ctx context.Context, arg repository.ListFeaturedVideosParams) ([]repository.ListFeaturedVideosRow, error)
	ListVideosByCustomer(ctx context.Context, arg repository.ListVideosByCustomerParams) ([]repository.ListVideosByCustomerRow, error)
	CountVideosByCustomer(ctx context.Context, customerID int64) (int64, error)
	ListMyVideos(ctx context.Context, arg repository.ListMyVideosParams) ([]repository.Video, error)
	CountMyVideos(ctx context.Context, arg repository.CountMyVideosParams) (int64, error)
	SoftDeleteVideo(ctx context.Context, arg repository.SoftDeleteVideoParams) error
	LikeVideo(ctx context.Context, arg repository.LikeVideoParams) error
	UnlikeVideo(ctx context.Context, arg repository.UnlikeVideoParams) error
	IsVideoLiked(ctx context.Context, arg repository.IsVideoLikedParams) (bool, error)
	IncrVideoClicks(ctx context.Context, id int64) error
	IncrVideoValidClicks(ctx context.Context, id int64) error
	CreatePlayRecord(ctx context.Context, arg repository.CreatePlayRecordParams) error
}

type VideoService struct {
	repo    VideoRepo
	storage *StorageService
}

func NewVideoService(repo VideoRepo, storage *StorageService) *VideoService {
	return &VideoService{repo: repo, storage: storage}
}

func (s *VideoService) videoBucket() string {
	return s.storage.BucketVideos()
}

// PresignedUploadResult is returned when generating a presigned upload URL.
type PresignedUploadResult struct {
	UploadURL string `json:"uploadUrl"`
	FileURL   string `json:"fileUrl"`
	S3Key     string `json:"s3Key"`
}

// GenerateVideoUploadURL generates a presigned PUT URL for video upload.
func (s *VideoService) GenerateVideoUploadURL(ctx context.Context, customerID int64, fileName, contentType string) (*PresignedUploadResult, error) {
	ext := filepath.Ext(fileName)
	if ext == "" {
		ext = ".mp4"
	}
	key := fmt.Sprintf("%d/%s%s", customerID, uuid.New().String(), ext)

	uploadURL, err := s.storage.GeneratePresignedUploadURL(ctx, s.videoBucket(), key, contentType, 60)
	if err != nil {
		return nil, err
	}

	fileURL := s.storage.BuildPublicURL(s.videoBucket(), key)

	return &PresignedUploadResult{
		UploadURL: uploadURL,
		FileURL:   fileURL,
		S3Key:     key,
	}, nil
}

// ConfirmVideoUploadRequest is the body for confirm-upload endpoint.
type ConfirmVideoUploadRequest struct {
	S3Key       string  `json:"s3Key"`
	FileName    string  `json:"fileName"`
	FileSize    int64   `json:"fileSize"`
	MimeType    string  `json:"mimeType"`
	Title       *string `json:"title"`
	Description *string `json:"description"`
	Duration    *int32  `json:"duration"`
}

// ConfirmVideoUpload creates the video record after client has uploaded to S3.
func (s *VideoService) ConfirmVideoUpload(ctx context.Context, customerID int64, req *ConfirmVideoUploadRequest) (*repository.Video, error) {
	fileURL := s.storage.BuildPublicURL(s.videoBucket(), req.S3Key)

	title := ""
	if req.Title != nil {
		title = *req.Title
	}
	desc := ""
	if req.Description != nil {
		desc = *req.Description
	}

	video, err := s.repo.CreateVideo(ctx, repository.CreateVideoParams{
		CustomerID:  customerID,
		Title:       pgtype.Text{String: title, Valid: title != ""},
		Description: pgtype.Text{String: desc, Valid: desc != ""},
		FileName:    req.FileName,
		FileSize:    req.FileSize,
		FileUrl:     fileURL,
		MimeType:    req.MimeType,
		Status:      model.VideoStatusPendingReview,
	})
	if err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to create video", err)
	}

	return &video, nil
}

type VideoDetail struct {
	ID           int64  `json:"id"`
	CustomerID   int64  `json:"customer_id"`
	Title        string `json:"title"`
	Description  string `json:"description"`
	FileURL      string `json:"file_url"`
	ThumbnailURL string `json:"thumbnail_url"`
	MimeType     string `json:"mime_type"`
	Duration     int32  `json:"duration"`
	Width        int32  `json:"width"`
	Height       int32  `json:"height"`
	Status       int16  `json:"status"`
	TotalClicks  int64  `json:"total_clicks"`
	ValidClicks  int64  `json:"valid_clicks"`
	LikeCount    int64  `json:"like_count"`
	CreatedAt    string `json:"created_at"`
	AuthorName   string `json:"author_name,omitempty"`
	AuthorAvatar string `json:"author_avatar,omitempty"`
	IsLiked      bool   `json:"is_liked"`
}

func (s *VideoService) GetVideo(ctx context.Context, videoID int64, customerID *int64) (*VideoDetail, error) {
	v, err := s.repo.GetVideoByID(ctx, videoID)
	if err != nil {
		return nil, model.NewAppError(model.ErrVideoNotFound, "video not found")
	}

	if v.Status != model.VideoStatusApproved && (customerID == nil || *customerID != v.CustomerID) {
		return nil, model.NewAppError(model.ErrVideoNotApproved, "video not approved")
	}

	detail := videoToDetail(&v)

	if customerID != nil {
		liked, _ := s.repo.IsVideoLiked(ctx, repository.IsVideoLikedParams{
			VideoID:    videoID,
			CustomerID: *customerID,
		})
		detail.IsLiked = liked
	}

	return detail, nil
}

func (s *VideoService) ListVideos(ctx context.Context, page, pageSize int, keyword *string) ([]VideoDetail, int64, error) {
	offset := (page - 1) * pageSize
	kw := toPgText(keyword)

	total, err := s.repo.CountVideos(ctx, kw)
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to count videos", err)
	}

	rows, err := s.repo.ListVideos(ctx, repository.ListVideosParams{
		Keyword:   kw,
		OffsetVal: int32(offset),
		LimitVal:  int32(pageSize),
	})
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to list videos", err)
	}

	items := make([]VideoDetail, len(rows))
	for i := range rows {
		r := &rows[i]
		items[i] = VideoDetail{
			ID:           r.ID,
			CustomerID:   r.CustomerID,
			Title:        r.Title.String,
			Description:  r.Description.String,
			FileURL:      r.FileUrl,
			ThumbnailURL: r.ThumbnailUrl.String,
			MimeType:     r.MimeType,
			Duration:     r.Duration.Int32,
			LikeCount:    r.LikeCount,
			TotalClicks:  r.TotalClicks,
			CreatedAt:    r.CreatedAt.Format(time.RFC3339),
			AuthorName:   r.AuthorName,
			AuthorAvatar: r.AuthorAvatar.String,
		}
	}

	return items, total, nil
}

func (s *VideoService) ListFeatured(ctx context.Context, page, pageSize int, keyword *string) ([]VideoDetail, int64, error) {
	offset := (page - 1) * pageSize
	kw := toPgText(keyword)

	total, err := s.repo.CountVideos(ctx, kw)
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to count videos", err)
	}

	rows, err := s.repo.ListFeaturedVideos(ctx, repository.ListFeaturedVideosParams{
		Keyword:   kw,
		OffsetVal: int32(offset),
		LimitVal:  int32(pageSize),
	})
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to list featured videos", err)
	}

	items := make([]VideoDetail, len(rows))
	for i := range rows {
		r := &rows[i]
		items[i] = VideoDetail{
			ID:           r.ID,
			CustomerID:   r.CustomerID,
			Title:        r.Title.String,
			FileURL:      r.FileUrl,
			ThumbnailURL: r.ThumbnailUrl.String,
			LikeCount:    r.LikeCount,
			CreatedAt:    r.CreatedAt.Format(time.RFC3339),
			AuthorName:   r.AuthorName,
			AuthorAvatar: r.AuthorAvatar.String,
		}
	}

	return items, total, nil
}

func (s *VideoService) ListMyVideos(ctx context.Context, customerID int64, page, pageSize int, status *int16) ([]VideoDetail, int64, error) {
	offset := (page - 1) * pageSize
	st := toPgInt2(status)

	total, err := s.repo.CountMyVideos(ctx, repository.CountMyVideosParams{
		CustomerID: customerID,
		Status:     st,
	})
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to count my videos", err)
	}

	rows, err := s.repo.ListMyVideos(ctx, repository.ListMyVideosParams{
		CustomerID: customerID,
		Status:     st,
		OffsetVal:  int32(offset),
		LimitVal:   int32(pageSize),
	})
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to list my videos", err)
	}

	items := make([]VideoDetail, len(rows))
	for i := range rows {
		items[i] = *videoToDetail(&rows[i])
	}

	return items, total, nil
}

func (s *VideoService) ListUserVideos(ctx context.Context, customerID int64, page, pageSize int) ([]VideoDetail, int64, error) {
	offset := (page - 1) * pageSize

	total, err := s.repo.CountVideosByCustomer(ctx, customerID)
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to count user videos", err)
	}

	rows, err := s.repo.ListVideosByCustomer(ctx, repository.ListVideosByCustomerParams{
		CustomerID: customerID,
		OffsetVal:  int32(offset),
		LimitVal:   int32(pageSize),
	})
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to list user videos", err)
	}

	items := make([]VideoDetail, len(rows))
	for i := range rows {
		r := &rows[i]
		items[i] = VideoDetail{
			ID:           r.ID,
			CustomerID:   r.CustomerID,
			Title:        r.Title.String,
			FileURL:      r.FileUrl,
			ThumbnailURL: r.ThumbnailUrl.String,
			LikeCount:    r.LikeCount,
			CreatedAt:    r.CreatedAt.Format(time.RFC3339),
			AuthorName:   r.AuthorName,
			AuthorAvatar: r.AuthorAvatar.String,
		}
	}

	return items, total, nil
}

func (s *VideoService) DeleteVideo(ctx context.Context, videoID, customerID int64) error {
	return s.repo.SoftDeleteVideo(ctx, repository.SoftDeleteVideoParams{
		ID:         videoID,
		CustomerID: customerID,
	})
}

func (s *VideoService) Like(ctx context.Context, videoID, customerID int64) error {
	v, err := s.repo.GetVideoByID(ctx, videoID)
	if err != nil {
		return model.NewAppError(model.ErrVideoNotFound, "video not found")
	}
	if v.Status != model.VideoStatusApproved {
		return model.NewAppError(model.ErrVideoNotApproved, "video not approved")
	}

	return s.repo.LikeVideo(ctx, repository.LikeVideoParams{
		VideoID:    videoID,
		CustomerID: customerID,
	})
}

func (s *VideoService) Unlike(ctx context.Context, videoID, customerID int64) error {
	return s.repo.UnlikeVideo(ctx, repository.UnlikeVideoParams{
		VideoID:    videoID,
		CustomerID: customerID,
	})
}

func (s *VideoService) RecordClick(ctx context.Context, videoID int64, customerID *int64, ip string) error {
	_ = s.repo.IncrVideoClicks(ctx, videoID)

	return s.repo.CreatePlayRecord(ctx, repository.CreatePlayRecordParams{
		VideoID:    videoID,
		CustomerID: toPgInt8(customerID),
		PlayType:   model.PlayTypeClick,
		IpAddress:  pgtype.Text{String: ip, Valid: ip != ""},
	})
}

func (s *VideoService) RecordValidPlay(ctx context.Context, videoID int64, customerID *int64, ip string) error {
	_ = s.repo.IncrVideoValidClicks(ctx, videoID)

	return s.repo.CreatePlayRecord(ctx, repository.CreatePlayRecordParams{
		VideoID:    videoID,
		CustomerID: toPgInt8(customerID),
		PlayType:   model.PlayTypeValidPlay,
		IpAddress:  pgtype.Text{String: ip, Valid: ip != ""},
	})
}

func videoToDetail(v *repository.Video) *VideoDetail {
	return &VideoDetail{
		ID:           v.ID,
		CustomerID:   v.CustomerID,
		Title:        v.Title.String,
		Description:  v.Description.String,
		FileURL:      v.FileUrl,
		ThumbnailURL: v.ThumbnailUrl.String,
		MimeType:     v.MimeType,
		Duration:     v.Duration.Int32,
		Width:        v.Width.Int32,
		Height:       v.Height.Int32,
		Status:       v.Status,
		TotalClicks:  v.TotalClicks,
		ValidClicks:  v.ValidClicks,
		LikeCount:    v.LikeCount,
		CreatedAt:    v.CreatedAt.Format(time.RFC3339),
	}
}
