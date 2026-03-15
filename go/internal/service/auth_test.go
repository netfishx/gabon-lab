package service

import (
	"context"
	"errors"
	"sync"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"golang.org/x/crypto/bcrypt"

	"gabon-go/internal/repository"
)

// --- Mock AuthRepo ---

type mockAuthRepo struct {
	customers map[int64]repository.Customer
	byName    map[string]int64
	nextID    int64
	mu        sync.Mutex
}

func newMockAuthRepo() *mockAuthRepo {
	return &mockAuthRepo{
		customers: make(map[int64]repository.Customer),
		byName:    make(map[string]int64),
		nextID:    1,
	}
}

func (m *mockAuthRepo) GetCustomerByUsername(_ context.Context, username string) (repository.Customer, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	id, ok := m.byName[username]
	if !ok {
		return repository.Customer{}, errors.New("not found")
	}
	return m.customers[id], nil
}

func (m *mockAuthRepo) GetCustomerByID(_ context.Context, id int64) (repository.Customer, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	c, ok := m.customers[id]
	if !ok {
		return repository.Customer{}, errors.New("not found")
	}
	return c, nil
}

func (m *mockAuthRepo) CreateCustomer(_ context.Context, arg repository.CreateCustomerParams) (repository.Customer, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if _, exists := m.byName[arg.Username]; exists {
		return repository.Customer{}, &pgconn.PgError{Code: "23505"}
	}
	id := m.nextID
	m.nextID++
	c := repository.Customer{
		ID:           id,
		Username:     arg.Username,
		PasswordHash: arg.PasswordHash,
		Name:         pgtype.Text{String: arg.Username, Valid: true},
	}
	m.customers[id] = c
	m.byName[arg.Username] = id
	return c, nil
}

func (m *mockAuthRepo) UpdateCustomerLastLogin(_ context.Context, _ int64) error {
	return nil
}

func (m *mockAuthRepo) UpdateCustomerPassword(_ context.Context, arg repository.UpdateCustomerPasswordParams) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	c, ok := m.customers[arg.ID]
	if !ok {
		return errors.New("not found")
	}
	c.PasswordHash = arg.PasswordHash
	m.customers[arg.ID] = c
	return nil
}

// --- Mock TokenStore ---

type mockTokenStore struct {
	blacklist map[string]bool
	families  map[string]familyData
	mu        sync.Mutex
}

func newMockTokenStore() *mockTokenStore {
	return &mockTokenStore{
		blacklist: make(map[string]bool),
		families:  make(map[string]familyData),
	}
}

func (m *mockTokenStore) SetBlacklist(_ context.Context, jti string, _ time.Duration) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.blacklist[jti] = true
	return nil
}

func (m *mockTokenStore) IsBlacklisted(_ context.Context, jti string) (bool, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.blacklist[jti], nil
}

func (m *mockTokenStore) SetFamily(_ context.Context, familyID string, customerID int64, currentJTI string, _ time.Duration) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.families[familyID] = familyData{CurrentJTI: currentJTI, CustomerID: customerID}
	return nil
}

func (m *mockTokenStore) CASFamily(_ context.Context, familyID, expectedJTI, newJTI string) (int64, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	data, ok := m.families[familyID]
	if !ok {
		return -1, nil
	}
	if data.CurrentJTI != expectedJTI {
		delete(m.families, familyID)
		return -2, nil
	}
	data.CurrentJTI = newJTI
	m.families[familyID] = data
	return 0, nil
}

func (m *mockTokenStore) DeleteFamily(_ context.Context, familyID string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	delete(m.families, familyID)
	return nil
}

// --- Helper ---

func newTestAuthService() (*AuthService, *mockAuthRepo, *mockTokenStore) {
	repo := newMockAuthRepo()
	tokens := newMockTokenStore()
	jwt := NewJWTService(newTestJWTConfig())
	svc := NewAuthService(repo, tokens, jwt, 7*24*time.Hour)
	return svc, repo, tokens
}

// --- Tests ---

func TestAuth_Register(t *testing.T) {
	svc, _, _ := newTestAuthService()
	ctx := context.Background()

	resp, err := svc.Register(ctx, "testuser", "Password123")
	require.NoError(t, err)
	assert.NotEmpty(t, resp.AccessToken)
	assert.NotEmpty(t, resp.RefreshToken)
}

func TestAuth_Register_DuplicateUsername(t *testing.T) {
	svc, _, _ := newTestAuthService()
	ctx := context.Background()

	_, err := svc.Register(ctx, "testuser", "Password123")
	require.NoError(t, err)

	_, err = svc.Register(ctx, "testuser", "Password456")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "username already exists")
}

func TestAuth_Login(t *testing.T) {
	svc, repo, _ := newTestAuthService()
	ctx := context.Background()

	hash, _ := bcrypt.GenerateFromPassword([]byte("Password123"), bcrypt.DefaultCost)
	_, _ = repo.CreateCustomer(ctx, repository.CreateCustomerParams{
		Username:     "loginuser",
		PasswordHash: string(hash),
	})

	resp, err := svc.Login(ctx, "loginuser", "Password123")
	require.NoError(t, err)
	assert.NotEmpty(t, resp.AccessToken)
}

func TestAuth_Login_WrongPassword(t *testing.T) {
	svc, repo, _ := newTestAuthService()
	ctx := context.Background()

	hash, _ := bcrypt.GenerateFromPassword([]byte("Password123"), bcrypt.DefaultCost)
	_, _ = repo.CreateCustomer(ctx, repository.CreateCustomerParams{
		Username:     "loginuser",
		PasswordHash: string(hash),
	})

	_, err := svc.Login(ctx, "loginuser", "WrongPassword")
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "invalid username or password")
}

func TestAuth_Login_NonexistentUser(t *testing.T) {
	svc, _, _ := newTestAuthService()
	ctx := context.Background()

	_, err := svc.Login(ctx, "nouser", "Password123")
	assert.Error(t, err)
}

func TestAuth_Refresh(t *testing.T) {
	svc, _, _ := newTestAuthService()
	ctx := context.Background()

	reg, err := svc.Register(ctx, "refreshuser", "Password123")
	require.NoError(t, err)

	resp, err := svc.Refresh(ctx, reg.RefreshToken)
	require.NoError(t, err)
	assert.NotEmpty(t, resp.AccessToken)
	assert.NotEqual(t, reg.AccessToken, resp.AccessToken)
}

func TestAuth_Refresh_ReplayDetection(t *testing.T) {
	svc, _, _ := newTestAuthService()
	ctx := context.Background()

	reg, err := svc.Register(ctx, "replayuser", "Password123")
	require.NoError(t, err)

	// First refresh succeeds
	_, err = svc.Refresh(ctx, reg.RefreshToken)
	require.NoError(t, err)

	// Second refresh with same old token fails (replay)
	_, err = svc.Refresh(ctx, reg.RefreshToken)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "reuse detected")
}

func TestAuth_Logout_InvalidatesRefresh(t *testing.T) {
	svc, _, _ := newTestAuthService()
	ctx := context.Background()

	reg, err := svc.Register(ctx, "logoutuser", "Password123")
	require.NoError(t, err)

	accessClaims, err := svc.jwt.ParseCustomerToken(reg.AccessToken)
	require.NoError(t, err)

	err = svc.Logout(ctx, accessClaims)
	require.NoError(t, err)

	// Refresh after logout should fail (family deleted)
	_, err = svc.Refresh(ctx, reg.RefreshToken)
	assert.Error(t, err)
}

func TestAuth_GetMe(t *testing.T) {
	svc, _, _ := newTestAuthService()
	ctx := context.Background()

	_, err := svc.Register(ctx, "meuser", "Password123")
	require.NoError(t, err)

	info, err := svc.GetMe(ctx, 1)
	require.NoError(t, err)
	assert.Equal(t, "meuser", info.Username)
}

func TestAuth_ConcurrentRefresh(t *testing.T) {
	svc, _, _ := newTestAuthService()
	ctx := context.Background()

	reg, err := svc.Register(ctx, "concurrentuser", "Password123")
	require.NoError(t, err)

	const n = 10
	results := make(chan error, n)
	var wg sync.WaitGroup
	wg.Add(n)

	for range n {
		go func() {
			defer wg.Done()
			_, err := svc.Refresh(ctx, reg.RefreshToken)
			results <- err
		}()
	}

	wg.Wait()
	close(results)

	var successes, failures int
	for err := range results {
		if err == nil {
			successes++
		} else {
			failures++
		}
	}

	assert.Equal(t, 1, successes, "exactly one concurrent refresh should succeed")
	assert.Equal(t, n-1, failures, "all other concurrent refreshes should fail")
}

func TestAuth_ChangePassword(t *testing.T) {
	svc, _, _ := newTestAuthService()
	ctx := context.Background()

	_, err := svc.Register(ctx, "pwduser", "OldPassword1")
	require.NoError(t, err)

	err = svc.ChangePassword(ctx, 1, "OldPassword1", "NewPassword2")
	require.NoError(t, err)

	// Login with new password
	_, err = svc.Login(ctx, "pwduser", "NewPassword2")
	require.NoError(t, err)

	// Login with old password fails
	_, err = svc.Login(ctx, "pwduser", "OldPassword1")
	assert.Error(t, err)
}
