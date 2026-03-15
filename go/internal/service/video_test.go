package service

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgtype"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"gabon-go/internal/config"
	"gabon-go/internal/repository"
)

// --- Mock VideoRepo ---

type mockVideoRepo struct {
	mu          sync.Mutex
	videos      map[int64]*repository.Video
	likes       map[string]bool // "videoID:customerID"
	playRecords []repository.CreatePlayRecordParams
	nextID      int64
}

func newMockVideoRepo() *mockVideoRepo {
	now := time.Now()
	return &mockVideoRepo{
		videos: map[int64]*repository.Video{
			1: {
				ID: 1, CustomerID: 1, Status: 4,
				Title:    pgtype.Text{String: "Approved Video", Valid: true},
				FileName: "a.mp4", FileUrl: "https://example.com/a.mp4", MimeType: "video/mp4",
				CreatedAt: now, UpdatedAt: now,
			},
			2: {
				ID: 2, CustomerID: 1, Status: 3,
				Title:    pgtype.Text{String: "Pending Video", Valid: true},
				FileName: "b.mp4", FileUrl: "https://example.com/b.mp4", MimeType: "video/mp4",
				CreatedAt: now, UpdatedAt: now,
			},
			3: {
				ID: 3, CustomerID: 2, Status: 4,
				Title:    pgtype.Text{String: "Bob Video", Valid: true},
				FileName: "c.mp4", FileUrl: "https://example.com/c.mp4", MimeType: "video/mp4",
				CreatedAt: now, UpdatedAt: now,
			},
		},
		likes:  make(map[string]bool),
		nextID: 4,
	}
}

func likeKey(videoID, customerID int64) string {
	return fmt.Sprintf("%d:%d", videoID, customerID)
}

func (m *mockVideoRepo) CreateVideo(_ context.Context, arg repository.CreateVideoParams) (repository.Video, error) { //nolint:gocritic // hugeParam: interface requires value receiver
	m.mu.Lock()
	defer m.mu.Unlock()
	v := repository.Video{
		ID:           m.nextID,
		CustomerID:   arg.CustomerID,
		Title:        arg.Title,
		Description:  arg.Description,
		FileName:     arg.FileName,
		FileSize:     arg.FileSize,
		FileUrl:      arg.FileUrl,
		ThumbnailUrl: arg.ThumbnailUrl,
		MimeType:     arg.MimeType,
		Duration:     arg.Duration,
		Width:        arg.Width,
		Height:       arg.Height,
		Status:       arg.Status,
		CreatedAt:    time.Now(),
		UpdatedAt:    time.Now(),
	}
	m.videos[m.nextID] = &v
	m.nextID++
	return v, nil
}

func (m *mockVideoRepo) GetVideoByID(_ context.Context, id int64) (repository.Video, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	v, ok := m.videos[id]
	if !ok || v.DeletedAt != nil {
		return repository.Video{}, errors.New("not found")
	}
	return *v, nil
}

func (m *mockVideoRepo) ListVideos(_ context.Context, _ repository.ListVideosParams) ([]repository.ListVideosRow, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var rows []repository.ListVideosRow
	for _, v := range m.videos {
		if v.Status == 4 && v.DeletedAt == nil {
			rows = append(rows, repository.ListVideosRow{
				ID: v.ID, CustomerID: v.CustomerID,
				Title: v.Title, Description: v.Description,
				FileUrl: v.FileUrl, ThumbnailUrl: v.ThumbnailUrl,
				MimeType: v.MimeType, Duration: v.Duration,
				LikeCount: v.LikeCount, TotalClicks: v.TotalClicks,
				CreatedAt: v.CreatedAt,
			})
		}
	}
	return rows, nil
}

func (m *mockVideoRepo) CountVideos(_ context.Context, _ pgtype.Text) (int64, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var count int64
	for _, v := range m.videos {
		if v.Status == 4 && v.DeletedAt == nil {
			count++
		}
	}
	return count, nil
}

func (m *mockVideoRepo) ListFeaturedVideos(_ context.Context, _ repository.ListFeaturedVideosParams) ([]repository.ListFeaturedVideosRow, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var rows []repository.ListFeaturedVideosRow
	for _, v := range m.videos {
		if v.Status == 4 && v.DeletedAt == nil {
			rows = append(rows, repository.ListFeaturedVideosRow{
				ID: v.ID, CustomerID: v.CustomerID,
				Title: v.Title, FileUrl: v.FileUrl, ThumbnailUrl: v.ThumbnailUrl,
				LikeCount: v.LikeCount, CreatedAt: v.CreatedAt,
			})
		}
	}
	return rows, nil
}

func (m *mockVideoRepo) ListVideosByCustomer(_ context.Context, arg repository.ListVideosByCustomerParams) ([]repository.ListVideosByCustomerRow, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var rows []repository.ListVideosByCustomerRow
	for _, v := range m.videos {
		if v.CustomerID == arg.CustomerID && v.Status == 4 && v.DeletedAt == nil {
			rows = append(rows, repository.ListVideosByCustomerRow{
				ID: v.ID, CustomerID: v.CustomerID,
				Title: v.Title, FileUrl: v.FileUrl, ThumbnailUrl: v.ThumbnailUrl,
				LikeCount: v.LikeCount, CreatedAt: v.CreatedAt,
			})
		}
	}
	return rows, nil
}

func (m *mockVideoRepo) CountVideosByCustomer(_ context.Context, customerID int64) (int64, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var count int64
	for _, v := range m.videos {
		if v.CustomerID == customerID && v.Status == 4 && v.DeletedAt == nil {
			count++
		}
	}
	return count, nil
}

func (m *mockVideoRepo) ListMyVideos(_ context.Context, arg repository.ListMyVideosParams) ([]repository.Video, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var rows []repository.Video
	for _, v := range m.videos {
		if v.CustomerID == arg.CustomerID && v.DeletedAt == nil {
			rows = append(rows, *v)
		}
	}
	return rows, nil
}

func (m *mockVideoRepo) CountMyVideos(_ context.Context, arg repository.CountMyVideosParams) (int64, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var count int64
	for _, v := range m.videos {
		if v.CustomerID == arg.CustomerID && v.DeletedAt == nil {
			count++
		}
	}
	return count, nil
}

func (m *mockVideoRepo) SoftDeleteVideo(_ context.Context, arg repository.SoftDeleteVideoParams) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	v, ok := m.videos[arg.ID]
	if !ok || v.CustomerID != arg.CustomerID {
		return errors.New("not found or not owner")
	}
	now := time.Now()
	v.DeletedAt = &now
	return nil
}

func (m *mockVideoRepo) LikeVideo(_ context.Context, arg repository.LikeVideoParams) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	key := likeKey(arg.VideoID, arg.CustomerID)
	if !m.likes[key] {
		m.likes[key] = true
		if v, ok := m.videos[arg.VideoID]; ok {
			v.LikeCount++
		}
	}
	return nil
}

func (m *mockVideoRepo) UnlikeVideo(_ context.Context, arg repository.UnlikeVideoParams) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	key := likeKey(arg.VideoID, arg.CustomerID)
	if m.likes[key] {
		delete(m.likes, key)
		if v, ok := m.videos[arg.VideoID]; ok && v.LikeCount > 0 {
			v.LikeCount--
		}
	}
	return nil
}

func (m *mockVideoRepo) IsVideoLiked(_ context.Context, arg repository.IsVideoLikedParams) (bool, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.likes[likeKey(arg.VideoID, arg.CustomerID)], nil
}

func (m *mockVideoRepo) IncrVideoClicks(_ context.Context, id int64) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if v, ok := m.videos[id]; ok {
		v.TotalClicks++
	}
	return nil
}

func (m *mockVideoRepo) IncrVideoValidClicks(_ context.Context, id int64) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if v, ok := m.videos[id]; ok {
		v.ValidClicks++
	}
	return nil
}

func (m *mockVideoRepo) CreatePlayRecord(_ context.Context, arg repository.CreatePlayRecordParams) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.playRecords = append(m.playRecords, arg)
	return nil
}

// --- Tests ---

func TestVideo_UploadVideo(t *testing.T) {
	storage := NewStorageService(&config.S3Config{
		BucketVideos:  "test-videos",
		BucketAvatars: "test-avatars",
	}) // stub mode (no endpoint)
	svc := NewVideoService(newMockVideoRepo(), storage)

	v, err := svc.UploadVideo(context.Background(), 1, &UploadVideoRequest{
		FileName: "test.mp4", FileSize: 1024, ContentType: "video/mp4",
		Body: strings.NewReader("fake-video-data"), Title: "My Video",
	})
	require.NoError(t, err)
	assert.Equal(t, int64(1), v.CustomerID)
	assert.Equal(t, int16(3), v.Status)
	assert.Equal(t, "My Video", v.Title.String)
	assert.Contains(t, v.FileUrl, "stub.local/storage/test-videos/")
}

func TestVideo_GetVideo_Approved(t *testing.T) {
	svc := NewVideoService(newMockVideoRepo(), nil)
	detail, err := svc.GetVideo(context.Background(), 1, nil)
	require.NoError(t, err)
	assert.Equal(t, int64(1), detail.ID)
	assert.Equal(t, "Approved Video", detail.Title)
}

func TestVideo_GetVideo_NotApproved(t *testing.T) {
	svc := NewVideoService(newMockVideoRepo(), nil)
	_, err := svc.GetVideo(context.Background(), 2, nil)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "video not approved")
}

func TestVideo_GetVideo_NotFound(t *testing.T) {
	svc := NewVideoService(newMockVideoRepo(), nil)
	_, err := svc.GetVideo(context.Background(), 999, nil)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "video not found")
}

func TestVideo_GetVideo_WithLikeStatus(t *testing.T) {
	repo := newMockVideoRepo()
	svc := NewVideoService(repo, nil)
	ctx := context.Background()

	_ = svc.Like(ctx, 1, 10)
	customerID := int64(10)
	detail, err := svc.GetVideo(ctx, 1, &customerID)
	require.NoError(t, err)
	assert.True(t, detail.IsLiked)
}

func TestVideo_ListVideos(t *testing.T) {
	svc := NewVideoService(newMockVideoRepo(), nil)
	items, total, err := svc.ListVideos(context.Background(), 1, 20, nil)
	require.NoError(t, err)
	assert.Equal(t, int64(2), total) // 2 approved videos (id=1,3)
	assert.Len(t, items, 2)
}

func TestVideo_ListMyVideos(t *testing.T) {
	svc := NewVideoService(newMockVideoRepo(), nil)
	items, total, err := svc.ListMyVideos(context.Background(), 1, 1, 20, nil)
	require.NoError(t, err)
	assert.Equal(t, int64(2), total) // customer 1 has 2 videos (approved + pending)
	assert.Len(t, items, 2)
}

func TestVideo_ListUserVideos(t *testing.T) {
	svc := NewVideoService(newMockVideoRepo(), nil)
	items, total, err := svc.ListUserVideos(context.Background(), 2, 1, 20)
	require.NoError(t, err)
	assert.Equal(t, int64(1), total) // customer 2 has 1 approved video
	assert.Len(t, items, 1)
}

func TestVideo_DeleteVideo(t *testing.T) {
	repo := newMockVideoRepo()
	svc := NewVideoService(repo, nil)
	ctx := context.Background()

	err := svc.DeleteVideo(ctx, 1, 1)
	require.NoError(t, err)

	_, err = svc.GetVideo(ctx, 1, nil)
	assert.Error(t, err)
}

func TestVideo_Like_Unlike(t *testing.T) {
	repo := newMockVideoRepo()
	svc := NewVideoService(repo, nil)
	ctx := context.Background()

	err := svc.Like(ctx, 1, 10)
	require.NoError(t, err)
	assert.Equal(t, int64(1), repo.videos[1].LikeCount)

	// idempotent like
	err = svc.Like(ctx, 1, 10)
	require.NoError(t, err)
	assert.Equal(t, int64(1), repo.videos[1].LikeCount)

	err = svc.Unlike(ctx, 1, 10)
	require.NoError(t, err)
	assert.Equal(t, int64(0), repo.videos[1].LikeCount)
}

func TestVideo_Like_NotFound(t *testing.T) {
	svc := NewVideoService(newMockVideoRepo(), nil)
	err := svc.Like(context.Background(), 999, 10)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "video not found")
}

func TestVideo_RecordClick(t *testing.T) {
	repo := newMockVideoRepo()
	svc := NewVideoService(repo, nil)
	ctx := context.Background()

	customerID := int64(10)
	err := svc.RecordClick(ctx, 1, &customerID, "1.2.3.4")
	require.NoError(t, err)
	assert.Equal(t, int64(1), repo.videos[1].TotalClicks)
	assert.Len(t, repo.playRecords, 1)
	assert.Equal(t, int16(1), repo.playRecords[0].PlayType)
}

func TestVideo_ConcurrentLike(t *testing.T) {
	repo := newMockVideoRepo()
	svc := NewVideoService(repo, nil)
	ctx := context.Background()

	const n = 10
	var wg sync.WaitGroup
	wg.Add(n)

	for range n {
		go func() {
			defer wg.Done()
			_ = svc.Like(ctx, 1, 10) // same user, same video
		}()
	}

	wg.Wait()
	assert.Equal(t, int64(1), repo.videos[1].LikeCount, "concurrent likes from same user should only increment once")
}

func TestVideo_RecordValidPlay(t *testing.T) {
	repo := newMockVideoRepo()
	svc := NewVideoService(repo, nil)
	ctx := context.Background()

	err := svc.RecordValidPlay(ctx, 1, nil, "1.2.3.4")
	require.NoError(t, err)
	assert.Equal(t, int64(1), repo.videos[1].ValidClicks)
	assert.Len(t, repo.playRecords, 1)
	assert.Equal(t, int16(2), repo.playRecords[0].PlayType)
}

func TestVideo_Like_NotApproved(t *testing.T) {
	svc := NewVideoService(newMockVideoRepo(), nil)
	err := svc.Like(context.Background(), 2, 10) // video 2 is pending (status=3)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "video not approved")
}

func TestVideo_GetVideo_OwnerCanViewUnapproved(t *testing.T) {
	svc := NewVideoService(newMockVideoRepo(), nil)
	ownerID := int64(1) // video 2 belongs to customer 1
	detail, err := svc.GetVideo(context.Background(), 2, &ownerID)
	require.NoError(t, err)
	assert.Equal(t, int64(2), detail.ID)
	assert.Equal(t, int16(3), detail.Status) // pending status visible to owner
}

func TestVideo_GetVideo_NonOwnerCannotViewUnapproved(t *testing.T) {
	svc := NewVideoService(newMockVideoRepo(), nil)
	otherID := int64(99)
	_, err := svc.GetVideo(context.Background(), 2, &otherID)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "video not approved")
}
