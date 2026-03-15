package service

import (
	"context"
	"errors"
	"fmt"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgtype"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"gabon-go/internal/repository"
)

// --- Mock UserRepo ---

type mockUserRepo struct {
	customers map[int64]repository.Customer
	follows   map[string]bool // "followerID:followedID"
	mu        sync.Mutex
}

func newMockUserRepo() *mockUserRepo {
	return &mockUserRepo{
		customers: map[int64]repository.Customer{
			1: {ID: 1, Username: "alice", Name: pgtype.Text{String: "Alice", Valid: true}},
			2: {ID: 2, Username: "bob", Name: pgtype.Text{String: "Bob", Valid: true}},
			3: {ID: 3, Username: "charlie", Name: pgtype.Text{String: "Charlie", Valid: true}},
		},
		follows: make(map[string]bool),
	}
}

func followKey(followerID, followedID int64) string {
	return fmt.Sprintf("%d:%d", followerID, followedID)
}

func parseFollowKey(key string) (followerID, followedID int64) {
	before, after, _ := strings.Cut(key, ":")
	followerID, _ = strconv.ParseInt(before, 10, 64)
	followedID, _ = strconv.ParseInt(after, 10, 64)
	return
}

func (m *mockUserRepo) GetCustomerProfile(_ context.Context, id int64) (repository.GetCustomerProfileRow, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	c, ok := m.customers[id]
	if !ok {
		return repository.GetCustomerProfileRow{}, errors.New("not found")
	}
	return repository.GetCustomerProfileRow{
		ID:             c.ID,
		Username:       c.Username,
		Name:           c.Name,
		IsVip:          c.IsVip,
		DiamondBalance: c.DiamondBalance,
		CreatedAt:      time.Now(),
	}, nil
}

func (m *mockUserRepo) UpdateCustomerProfile(_ context.Context, arg repository.UpdateCustomerProfileParams) (repository.Customer, error) { //nolint:gocritic // hugeParam acceptable in test mock
	m.mu.Lock()
	defer m.mu.Unlock()
	c, ok := m.customers[arg.ID]
	if !ok {
		return repository.Customer{}, errors.New("not found")
	}
	if name, ok := arg.Name.(string); ok && name != "" {
		c.Name = pgtype.Text{String: name, Valid: true}
	}
	m.customers[arg.ID] = c
	return c, nil
}

func (m *mockUserRepo) GetCustomerByID(_ context.Context, id int64) (repository.Customer, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	c, ok := m.customers[id]
	if !ok {
		return repository.Customer{}, errors.New("not found")
	}
	return c, nil
}

func (m *mockUserRepo) FollowUser(_ context.Context, arg repository.FollowUserParams) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	key := followKey(arg.FollowerID, arg.FollowedID)
	m.follows[key] = true // ON CONFLICT DO NOTHING
	return nil
}

func (m *mockUserRepo) UnfollowUser(_ context.Context, arg repository.UnfollowUserParams) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	key := followKey(arg.FollowerID, arg.FollowedID)
	delete(m.follows, key)
	return nil
}

func (m *mockUserRepo) IsFollowing(_ context.Context, arg repository.IsFollowingParams) (bool, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.follows[followKey(arg.FollowerID, arg.FollowedID)], nil
}

func (m *mockUserRepo) GetFollowingList(_ context.Context, arg repository.GetFollowingListParams) ([]repository.GetFollowingListRow, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var items []repository.GetFollowingListRow
	for key := range m.follows {
		fid, tid := parseFollowKey(key)
		if fid == arg.FollowerID {
			c := m.customers[tid]
			var viewerFollowing, viewerFollowedBy bool
			if arg.ViewerID.Valid {
				viewerFollowing = m.follows[followKey(arg.ViewerID.Int64, tid)]
				viewerFollowedBy = m.follows[followKey(tid, arg.ViewerID.Int64)]
			}
			items = append(items, repository.GetFollowingListRow{
				UserID:             tid,
				Username:           c.Username,
				Name:               c.Name,
				ViewerIsFollowing:  viewerFollowing,
				ViewerIsFollowedBy: viewerFollowedBy,
			})
		}
	}
	return items, nil
}

func (m *mockUserRepo) CountFollowing(_ context.Context, followerID int64) (int64, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var count int64
	for key := range m.follows {
		fid, _ := parseFollowKey(key)
		if fid == followerID {
			count++
		}
	}
	return count, nil
}

func (m *mockUserRepo) GetFollowersList(_ context.Context, arg repository.GetFollowersListParams) ([]repository.GetFollowersListRow, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var items []repository.GetFollowersListRow
	for key := range m.follows {
		fid, tid := parseFollowKey(key)
		if tid == arg.FollowedID {
			c := m.customers[fid]
			var viewerFollowing, viewerFollowedBy bool
			if arg.ViewerID.Valid {
				viewerFollowing = m.follows[followKey(arg.ViewerID.Int64, fid)]
				viewerFollowedBy = m.follows[followKey(fid, arg.ViewerID.Int64)]
			}
			items = append(items, repository.GetFollowersListRow{
				UserID:             fid,
				Username:           c.Username,
				Name:               c.Name,
				ViewerIsFollowing:  viewerFollowing,
				ViewerIsFollowedBy: viewerFollowedBy,
			})
		}
	}
	return items, nil
}

func (m *mockUserRepo) CountFollowers(_ context.Context, followedID int64) (int64, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var count int64
	for key := range m.follows {
		_, tid := parseFollowKey(key)
		if tid == followedID {
			count++
		}
	}
	return count, nil
}

// --- Tests ---

func TestUser_GetProfile(t *testing.T) {
	repo := newMockUserRepo()
	svc := NewUserService(repo, nil)
	ctx := context.Background()

	p, err := svc.GetProfile(ctx, 1)
	require.NoError(t, err)
	assert.Equal(t, "alice", p.Username)
	assert.Equal(t, "Alice", p.Name)
}

func TestUser_GetProfile_NotFound(t *testing.T) {
	repo := newMockUserRepo()
	svc := NewUserService(repo, nil)
	ctx := context.Background()

	_, err := svc.GetProfile(ctx, 999)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "customer not found")
}

func TestUser_UpdateProfile(t *testing.T) {
	repo := newMockUserRepo()
	svc := NewUserService(repo, nil)
	ctx := context.Background()

	p, err := svc.UpdateProfile(ctx, 1, "NewAlice", "", "", "", "")
	require.NoError(t, err)
	assert.Equal(t, "NewAlice", p.Name)
}

func TestUser_Follow(t *testing.T) {
	repo := newMockUserRepo()
	svc := NewUserService(repo, nil)
	ctx := context.Background()

	err := svc.Follow(ctx, 1, 2)
	require.NoError(t, err)

	items, total, err := svc.GetFollowing(ctx, 1, 1, 20, nil)
	require.NoError(t, err)
	assert.Equal(t, int64(1), total)
	assert.Equal(t, int64(2), items[0].UserID)
}

func TestUser_Follow_Self(t *testing.T) {
	repo := newMockUserRepo()
	svc := NewUserService(repo, nil)
	ctx := context.Background()

	err := svc.Follow(ctx, 1, 1)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "cannot follow yourself")
}

func TestUser_Follow_NotFound(t *testing.T) {
	repo := newMockUserRepo()
	svc := NewUserService(repo, nil)
	ctx := context.Background()

	err := svc.Follow(ctx, 1, 999)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "user not found")
}

func TestUser_Unfollow(t *testing.T) {
	repo := newMockUserRepo()
	svc := NewUserService(repo, nil)
	ctx := context.Background()

	_ = svc.Follow(ctx, 1, 2)
	err := svc.Unfollow(ctx, 1, 2)
	require.NoError(t, err)

	_, total, err := svc.GetFollowing(ctx, 1, 1, 20, nil)
	require.NoError(t, err)
	assert.Equal(t, int64(0), total)
}

func TestUser_MutualFollow(t *testing.T) {
	repo := newMockUserRepo()
	svc := NewUserService(repo, nil)
	ctx := context.Background()

	_ = svc.Follow(ctx, 1, 2)
	_ = svc.Follow(ctx, 2, 1)

	viewerID := int64(1)
	items, _, err := svc.GetFollowing(ctx, 1, 1, 20, &viewerID)
	require.NoError(t, err)
	assert.Len(t, items, 1)
	assert.Equal(t, 2, items[0].FollowStatus) // mutual
}

func TestUser_Followers(t *testing.T) {
	repo := newMockUserRepo()
	svc := NewUserService(repo, nil)
	ctx := context.Background()

	_ = svc.Follow(ctx, 1, 2)
	_ = svc.Follow(ctx, 3, 2)

	items, total, err := svc.GetFollowers(ctx, 2, 1, 20, nil)
	require.NoError(t, err)
	assert.Equal(t, int64(2), total)
	assert.Len(t, items, 2)
}

func TestUser_GetPublicProfile(t *testing.T) {
	repo := newMockUserRepo()
	svc := NewUserService(repo, nil)
	ctx := context.Background()

	p, err := svc.GetPublicProfile(ctx, 1, nil)
	require.NoError(t, err)
	assert.Equal(t, "alice", p.Username)
	assert.Equal(t, int64(0), p.FollowingCount)
	assert.Equal(t, 0, p.FollowStatus)
}

func TestUser_GetPublicProfile_WithFollowStatus(t *testing.T) {
	repo := newMockUserRepo()
	svc := NewUserService(repo, nil)
	ctx := context.Background()

	_ = svc.Follow(ctx, 2, 1) // bob follows alice
	viewerID := int64(2)      // viewer is bob
	p, err := svc.GetPublicProfile(ctx, 1, &viewerID)
	require.NoError(t, err)
	assert.Equal(t, 1, p.FollowStatus) // bob follows alice, not mutual

	_ = svc.Follow(ctx, 1, 2) // alice follows bob back
	p, err = svc.GetPublicProfile(ctx, 1, &viewerID)
	require.NoError(t, err)
	assert.Equal(t, 2, p.FollowStatus) // now mutual
}

func TestUser_GetPublicProfile_NotFound(t *testing.T) {
	repo := newMockUserRepo()
	svc := NewUserService(repo, nil)

	_, err := svc.GetPublicProfile(context.Background(), 999, nil)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "user not found")
}

func TestUser_FollowingList_ViewerStatus(t *testing.T) {
	repo := newMockUserRepo()
	svc := NewUserService(repo, nil)
	ctx := context.Background()

	_ = svc.Follow(ctx, 1, 2) // alice follows bob
	_ = svc.Follow(ctx, 1, 3) // alice follows charlie
	_ = svc.Follow(ctx, 3, 2) // charlie follows bob (viewer=charlie)

	viewerID := int64(3)
	items, _, err := svc.GetFollowing(ctx, 1, 1, 20, &viewerID)
	require.NoError(t, err)
	assert.Len(t, items, 2)

	for _, item := range items {
		if item.UserID == 2 {
			assert.Equal(t, 1, item.FollowStatus) // charlie follows bob
		}
		if item.UserID == 3 {
			assert.Equal(t, 0, item.FollowStatus) // charlie doesn't follow charlie (self)
		}
	}
}

func TestUser_Follow_AlreadyFollowing(t *testing.T) {
	svc := NewUserService(newMockUserRepo(), nil)
	ctx := context.Background()

	require.NoError(t, svc.Follow(ctx, 1, 2))
	err := svc.Follow(ctx, 1, 2)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "already following")
}

func TestUser_Unfollow_NotFollowing(t *testing.T) {
	svc := NewUserService(newMockUserRepo(), nil)
	err := svc.Unfollow(context.Background(), 1, 2)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "not following")
}

func TestUser_Unfollow_Self(t *testing.T) {
	svc := NewUserService(newMockUserRepo(), nil)
	err := svc.Unfollow(context.Background(), 1, 1)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "cannot unfollow yourself")
}

func TestUser_Unfollow_UserNotFound(t *testing.T) {
	svc := NewUserService(newMockUserRepo(), nil)
	err := svc.Unfollow(context.Background(), 1, 999)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "user not found")
}

func TestUser_GetFollowing_UserNotFound(t *testing.T) {
	svc := NewUserService(newMockUserRepo(), nil)
	_, _, err := svc.GetFollowing(context.Background(), 999, 1, 20, nil)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "user not found")
}

func TestUser_GetFollowers_UserNotFound(t *testing.T) {
	svc := NewUserService(newMockUserRepo(), nil)
	_, _, err := svc.GetFollowers(context.Background(), 999, 1, 20, nil)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "user not found")
}
