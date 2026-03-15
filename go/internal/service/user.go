package service

import (
	"context"
	"fmt"
	"path/filepath"
	"strings"
	"time"

	"github.com/google/uuid"

	"gabon-go/internal/model"
	"gabon-go/internal/repository"
)

type UserRepo interface {
	GetCustomerProfile(ctx context.Context, id int64) (repository.GetCustomerProfileRow, error)
	UpdateCustomerProfile(ctx context.Context, arg repository.UpdateCustomerProfileParams) (repository.Customer, error)
	GetCustomerByID(ctx context.Context, id int64) (repository.Customer, error)
	FollowUser(ctx context.Context, arg repository.FollowUserParams) error
	UnfollowUser(ctx context.Context, arg repository.UnfollowUserParams) error
	IsFollowing(ctx context.Context, arg repository.IsFollowingParams) (bool, error)
	GetFollowingList(ctx context.Context, arg repository.GetFollowingListParams) ([]repository.GetFollowingListRow, error)
	CountFollowing(ctx context.Context, followerID int64) (int64, error)
	GetFollowersList(ctx context.Context, arg repository.GetFollowersListParams) ([]repository.GetFollowersListRow, error)
	CountFollowers(ctx context.Context, followedID int64) (int64, error)
}

type UserService struct {
	repo    UserRepo
	storage *StorageService
}

func NewUserService(repo UserRepo, storage *StorageService) *UserService {
	return &UserService{repo: repo, storage: storage}
}

func (s *UserService) avatarBucket() string {
	return s.storage.BucketAvatars()
}

// AvatarPresignResult is returned when generating a presigned avatar upload URL.
type AvatarPresignResult struct {
	UploadURL string `json:"uploadUrl"`
	AvatarURL string `json:"avatarUrl"`
}

// GenerateAvatarUploadURL generates a presigned PUT URL for avatar upload.
func (s *UserService) GenerateAvatarUploadURL(ctx context.Context, customerID int64, fileName, contentType string) (*AvatarPresignResult, error) {
	ext := filepath.Ext(fileName)
	if ext == "" {
		ext = ".jpg"
	}
	key := fmt.Sprintf("%d/%s%s", customerID, uuid.New().String(), ext)

	uploadURL, err := s.storage.GeneratePresignedUploadURL(ctx, s.avatarBucket(), key, contentType, 60)
	if err != nil {
		return nil, err
	}

	avatarURL := s.storage.BuildPublicURL(s.avatarBucket(), key)

	return &AvatarPresignResult{
		UploadURL: uploadURL,
		AvatarURL: avatarURL,
	}, nil
}

// ConfirmAvatarUpload updates the customer's avatar URL after client has uploaded to S3.
func (s *UserService) ConfirmAvatarUpload(ctx context.Context, customerID int64, avatarURL string) error {
	_, err := s.repo.UpdateCustomerProfile(ctx, repository.UpdateCustomerProfileParams{
		AvatarUrl: avatarURL,
		ID:        customerID,
	})
	if err != nil {
		return model.WrapError(model.ErrInternal, "failed to update avatar", err)
	}
	return nil
}

type ProfileResponse struct {
	ID             int64   `json:"id"`
	Username       string  `json:"username"`
	Name           string  `json:"name"`
	Phone          string  `json:"phone"`
	Email          string  `json:"email"`
	AvatarURL      string  `json:"avatar_url"`
	Signature      string  `json:"signature"`
	IsVip          bool    `json:"is_vip"`
	DiamondBalance int64   `json:"diamond_balance"`
	LastLoginAt    *string `json:"last_login_at"`
	CreatedAt      string  `json:"created_at"`
}

func (s *UserService) GetProfile(ctx context.Context, customerID int64) (*ProfileResponse, error) {
	p, err := s.repo.GetCustomerProfile(ctx, customerID)
	if err != nil {
		return nil, model.NewAppError(model.ErrNotFound, "customer not found")
	}

	var lastLogin *string
	if p.LastLoginAt != nil {
		t := p.LastLoginAt.Format(time.RFC3339)
		lastLogin = &t
	}

	return &ProfileResponse{
		ID:             p.ID,
		Username:       p.Username,
		Name:           p.Name.String,
		Phone:          p.Phone.String,
		Email:          p.Email.String,
		AvatarURL:      p.AvatarUrl.String,
		Signature:      p.Signature.String,
		IsVip:          p.IsVip,
		DiamondBalance: p.DiamondBalance,
		LastLoginAt:    lastLogin,
		CreatedAt:      p.CreatedAt.Format(time.RFC3339),
	}, nil
}

func (s *UserService) UpdateProfile(ctx context.Context, customerID int64, name, phone, email, avatarURL, signature string) (*ProfileResponse, error) {
	_, err := s.repo.UpdateCustomerProfile(ctx, repository.UpdateCustomerProfileParams{
		Name:      name,
		Phone:     phone,
		Email:     email,
		AvatarUrl: avatarURL,
		Signature: signature,
		ID:        customerID,
	})
	if err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to update profile", err)
	}

	return s.GetProfile(ctx, customerID)
}

func (s *UserService) Follow(ctx context.Context, followerID, followedID int64) error {
	if followerID == followedID {
		return model.NewAppError(model.ErrCannotFollowSelf, "cannot follow yourself")
	}

	if _, err := s.repo.GetCustomerByID(ctx, followedID); err != nil {
		return model.NewAppError(model.ErrNotFound, "user not found")
	}

	already, _ := s.repo.IsFollowing(ctx, repository.IsFollowingParams{
		FollowerID: followerID,
		FollowedID: followedID,
	})
	if already {
		return model.NewAppError(model.ErrAlreadyFollowing, "already following this user")
	}

	err := s.repo.FollowUser(ctx, repository.FollowUserParams{
		FollowerID: followerID,
		FollowedID: followedID,
	})
	if err != nil {
		if strings.Contains(err.Error(), "violates check constraint") {
			return model.NewAppError(model.ErrCannotFollowSelf, "cannot follow yourself")
		}
		return model.WrapError(model.ErrInternal, "failed to follow user", err)
	}

	return nil
}

func (s *UserService) Unfollow(ctx context.Context, followerID, followedID int64) error {
	if followerID == followedID {
		return model.NewAppError(model.ErrCannotFollowSelf, "cannot unfollow yourself")
	}

	if _, err := s.repo.GetCustomerByID(ctx, followedID); err != nil {
		return model.NewAppError(model.ErrNotFound, "user not found")
	}

	following, _ := s.repo.IsFollowing(ctx, repository.IsFollowingParams{
		FollowerID: followerID,
		FollowedID: followedID,
	})
	if !following {
		return model.NewAppError(model.ErrNotFollowing, "not following this user")
	}

	return s.repo.UnfollowUser(ctx, repository.UnfollowUserParams{
		FollowerID: followerID,
		FollowedID: followedID,
	})
}

type PublicProfileResponse struct {
	ID             int64  `json:"id"`
	Username       string `json:"username"`
	Name           string `json:"name"`
	AvatarURL      string `json:"avatar_url"`
	Signature      string `json:"signature"`
	IsVip          bool   `json:"is_vip"`
	FollowingCount int64  `json:"following_count"`
	FollowerCount  int64  `json:"follower_count"`
	FollowStatus   int    `json:"follow_status"` // 0=未关注 1=已关注 2=互关
	CreatedAt      string `json:"created_at"`
}

func (s *UserService) GetPublicProfile(ctx context.Context, targetID int64, viewerID *int64) (*PublicProfileResponse, error) {
	p, err := s.repo.GetCustomerProfile(ctx, targetID)
	if err != nil {
		return nil, model.NewAppError(model.ErrNotFound, "user not found")
	}

	followingCount, _ := s.repo.CountFollowing(ctx, targetID)
	followerCount, _ := s.repo.CountFollowers(ctx, targetID)

	followStatus := computeFollowStatus(ctx, s.repo, viewerID, targetID)

	return &PublicProfileResponse{
		ID:             p.ID,
		Username:       p.Username,
		Name:           p.Name.String,
		AvatarURL:      p.AvatarUrl.String,
		Signature:      p.Signature.String,
		IsVip:          p.IsVip,
		FollowingCount: followingCount,
		FollowerCount:  followerCount,
		FollowStatus:   followStatus,
		CreatedAt:      p.CreatedAt.Format(time.RFC3339),
	}, nil
}

func computeFollowStatus(ctx context.Context, repo UserRepo, viewerID *int64, targetID int64) int {
	if viewerID == nil || *viewerID == targetID {
		return 0
	}
	iFollow, _ := repo.IsFollowing(ctx, repository.IsFollowingParams{
		FollowerID: *viewerID,
		FollowedID: targetID,
	})
	if !iFollow {
		return 0
	}
	theyFollowMe, _ := repo.IsFollowing(ctx, repository.IsFollowingParams{
		FollowerID: targetID,
		FollowedID: *viewerID,
	})
	if theyFollowMe {
		return 2
	}
	return 1
}

type FollowUserItem struct {
	UserID       int64  `json:"user_id"`
	Username     string `json:"username"`
	Name         string `json:"name"`
	AvatarURL    string `json:"avatar_url"`
	FollowStatus int    `json:"follow_status"` // 0=未关注 1=已关注 2=互关
}

func (s *UserService) GetFollowing(ctx context.Context, userID int64, page, pageSize int, viewerID *int64) ([]FollowUserItem, int64, error) {
	if _, err := s.repo.GetCustomerByID(ctx, userID); err != nil {
		return nil, 0, model.NewAppError(model.ErrNotFound, "user not found")
	}

	offset := (page - 1) * pageSize

	total, err := s.repo.CountFollowing(ctx, userID)
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to count following", err)
	}

	rows, err := s.repo.GetFollowingList(ctx, repository.GetFollowingListParams{
		ViewerID:   toPgInt8(viewerID),
		FollowerID: userID,
		OffsetVal:  int32(offset),
		LimitVal:   int32(pageSize),
	})
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to get following list", err)
	}

	items := make([]FollowUserItem, len(rows))
	for i, r := range rows {
		items[i] = FollowUserItem{
			UserID:       r.UserID,
			Username:     r.Username,
			Name:         r.Name.String,
			AvatarURL:    r.AvatarUrl.String,
			FollowStatus: toFollowStatus(r.ViewerIsFollowing, r.ViewerIsFollowedBy),
		}
	}

	return items, total, nil
}

func (s *UserService) GetFollowers(ctx context.Context, userID int64, page, pageSize int, viewerID *int64) ([]FollowUserItem, int64, error) {
	if _, err := s.repo.GetCustomerByID(ctx, userID); err != nil {
		return nil, 0, model.NewAppError(model.ErrNotFound, "user not found")
	}

	offset := (page - 1) * pageSize

	total, err := s.repo.CountFollowers(ctx, userID)
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to count followers", err)
	}

	rows, err := s.repo.GetFollowersList(ctx, repository.GetFollowersListParams{
		ViewerID:   toPgInt8(viewerID),
		FollowedID: userID,
		OffsetVal:  int32(offset),
		LimitVal:   int32(pageSize),
	})
	if err != nil {
		return nil, 0, model.WrapError(model.ErrInternal, "failed to get followers list", err)
	}

	items := make([]FollowUserItem, len(rows))
	for i, r := range rows {
		items[i] = FollowUserItem{
			UserID:       r.UserID,
			Username:     r.Username,
			Name:         r.Name.String,
			AvatarURL:    r.AvatarUrl.String,
			FollowStatus: toFollowStatus(r.ViewerIsFollowing, r.ViewerIsFollowedBy),
		}
	}

	return items, total, nil
}

func toFollowStatus(viewerIsFollowing, viewerIsFollowedBy bool) int {
	if !viewerIsFollowing {
		return 0
	}
	if viewerIsFollowedBy {
		return 2
	}
	return 1
}
