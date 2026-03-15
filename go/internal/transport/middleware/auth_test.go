package middleware_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/danielgtaylor/huma/v2"
	"github.com/danielgtaylor/huma/v2/adapters/humaecho"
	"github.com/labstack/echo/v4"
	"github.com/stretchr/testify/assert"

	"gabon-go/internal/config"
	"gabon-go/internal/service"
	"gabon-go/internal/transport/middleware"
)

// --- mock TokenStore ---

type mockTokenStore struct {
	blacklisted map[string]bool
}

func newMockTokenStore() *mockTokenStore {
	return &mockTokenStore{blacklisted: make(map[string]bool)}
}

func (m *mockTokenStore) SetBlacklist(_ context.Context, jti string, _ time.Duration) error {
	m.blacklisted[jti] = true
	return nil
}

func (m *mockTokenStore) IsBlacklisted(_ context.Context, jti string) (bool, error) {
	return m.blacklisted[jti], nil
}

func (m *mockTokenStore) SetFamily(context.Context, string, int64, string, time.Duration) error {
	return nil
}

func (m *mockTokenStore) CASFamily(context.Context, string, string, string) (int64, error) {
	return 0, nil
}

func (m *mockTokenStore) DeleteFamily(context.Context, string) error { return nil }

// --- helpers ---

func testJWTService() *service.JWTService {
	return service.NewJWTService(&config.JWTConfig{
		CustomerSecret:     "test-customer-secret-key-32bytes!",
		AdminSecret:        "test-admin-secret-key-32bytes!!",
		CustomerAccessTTL:  time.Hour,
		CustomerRefreshTTL: 24 * time.Hour,
		AdminAccessTTL:     time.Hour,
		AdminRefreshTTL:    24 * time.Hour,
		CurrentKID:         "test-kid",
	})
}

func testAuthCfg() middleware.AuthConfig {
	return middleware.AuthConfig{
		JWT:        testJWTService(),
		TokenStore: newMockTokenStore(),
	}
}

func setupTestAPI(t *testing.T, mw func(huma.Context, func(huma.Context))) (*echo.Echo, huma.API) {
	t.Helper()
	e := echo.New()
	api := humaecho.New(e, huma.DefaultConfig("test", "0.0.0"))
	huma.Register(api, huma.Operation{
		OperationID: "test-op",
		Method:      http.MethodGet,
		Path:        "/test",
		Middlewares: huma.Middlewares{mw},
	}, func(_ context.Context, _ *struct{}) (*struct{ Body struct{ OK bool } }, error) {
		return &struct{ Body struct{ OK bool } }{Body: struct{ OK bool }{OK: true}}, nil
	})
	return e, api
}

// --- RequireCustomerAuth tests ---

func TestRequireCustomerAuth_MissingToken(t *testing.T) {
	e, _ := setupTestAPI(t, middleware.RequireCustomerAuth(testAuthCfg()))
	req := httptest.NewRequest(http.MethodGet, "/test", http.NoBody)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusUnauthorized, rec.Code)
	assert.Contains(t, rec.Body.String(), "UNAUTHORIZED")
}

func TestRequireCustomerAuth_InvalidToken(t *testing.T) {
	e, _ := setupTestAPI(t, middleware.RequireCustomerAuth(testAuthCfg()))
	req := httptest.NewRequest(http.MethodGet, "/test", http.NoBody)
	req.Header.Set("Authorization", "Bearer invalid-token")
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusUnauthorized, rec.Code)
	assert.Contains(t, rec.Body.String(), "TOKEN_INVALID")
}

func TestRequireCustomerAuth_ValidToken(t *testing.T) {
	cfg := testAuthCfg()
	e, _ := setupTestAPI(t, middleware.RequireCustomerAuth(cfg))

	tokens, err := cfg.JWT.GenerateCustomerTokens(42)
	assert.NoError(t, err)

	req := httptest.NewRequest(http.MethodGet, "/test", http.NoBody)
	req.Header.Set("Authorization", "Bearer "+tokens.AccessToken)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
}

func TestRequireCustomerAuth_BlacklistedToken(t *testing.T) {
	store := newMockTokenStore()
	jwtSvc := testJWTService()
	cfg := middleware.AuthConfig{JWT: jwtSvc, TokenStore: store}

	tokens, err := jwtSvc.GenerateCustomerTokens(42)
	assert.NoError(t, err)

	// Parse to get the JTI, then blacklist it.
	claims, err := jwtSvc.ParseCustomerToken(tokens.AccessToken)
	assert.NoError(t, err)
	_ = store.SetBlacklist(context.Background(), claims.JTI, time.Hour)

	e, _ := setupTestAPI(t, middleware.RequireCustomerAuth(cfg))
	req := httptest.NewRequest(http.MethodGet, "/test", http.NoBody)
	req.Header.Set("Authorization", "Bearer "+tokens.AccessToken)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusUnauthorized, rec.Code)
	assert.Contains(t, rec.Body.String(), "revoked")
}

func TestRequireCustomerAuth_RefreshTokenRejected(t *testing.T) {
	cfg := testAuthCfg()
	tokens, err := cfg.JWT.GenerateCustomerTokens(42)
	assert.NoError(t, err)

	e, _ := setupTestAPI(t, middleware.RequireCustomerAuth(cfg))
	req := httptest.NewRequest(http.MethodGet, "/test", http.NoBody)
	req.Header.Set("Authorization", "Bearer "+tokens.RefreshToken)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusUnauthorized, rec.Code)
	assert.Contains(t, rec.Body.String(), "not an access token")
}

// --- OptionalCustomerAuth tests ---

func TestOptionalCustomerAuth_NoToken_Passes(t *testing.T) {
	e, _ := setupTestAPI(t, middleware.OptionalCustomerAuth(testAuthCfg()))
	req := httptest.NewRequest(http.MethodGet, "/test", http.NoBody)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
}

func TestOptionalCustomerAuth_InvalidToken_Passes(t *testing.T) {
	e, _ := setupTestAPI(t, middleware.OptionalCustomerAuth(testAuthCfg()))
	req := httptest.NewRequest(http.MethodGet, "/test", http.NoBody)
	req.Header.Set("Authorization", "Bearer bad-token")
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
}

// --- RequireAdminAuth tests ---

func TestRequireAdminAuth_MissingToken(t *testing.T) {
	e, _ := setupTestAPI(t, middleware.RequireAdminAuth(testAuthCfg()))
	req := httptest.NewRequest(http.MethodGet, "/test", http.NoBody)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusUnauthorized, rec.Code)
}

func TestRequireAdminAuth_CustomerTokenRejected(t *testing.T) {
	cfg := testAuthCfg()
	tokens, err := cfg.JWT.GenerateCustomerTokens(42)
	assert.NoError(t, err)

	e, _ := setupTestAPI(t, middleware.RequireAdminAuth(cfg))
	req := httptest.NewRequest(http.MethodGet, "/test", http.NoBody)
	req.Header.Set("Authorization", "Bearer "+tokens.AccessToken)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusUnauthorized, rec.Code)
}

func TestRequireAdminAuth_ValidAdminToken(t *testing.T) {
	cfg := testAuthCfg()
	tokens, err := cfg.JWT.GenerateAdminTokens(1, "superadmin")
	assert.NoError(t, err)

	e, _ := setupTestAPI(t, middleware.RequireAdminAuth(cfg))
	req := httptest.NewRequest(http.MethodGet, "/test", http.NoBody)
	req.Header.Set("Authorization", "Bearer "+tokens.AccessToken)
	rec := httptest.NewRecorder()
	e.ServeHTTP(rec, req)

	assert.Equal(t, http.StatusOK, rec.Code)
}
