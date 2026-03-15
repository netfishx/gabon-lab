package service

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"gabon-go/internal/config"
)

func newTestJWTConfig() *config.JWTConfig {
	return &config.JWTConfig{
		CustomerSecret:     "test-customer-secret-min-32-chars!!",
		CustomerAccessTTL:  15 * time.Minute,
		CustomerRefreshTTL: 7 * 24 * time.Hour,
		AdminSecret:        "test-admin-secret-min-32-chars!!!!",
		AdminAccessTTL:     15 * time.Minute,
		AdminRefreshTTL:    7 * 24 * time.Hour,
		CurrentKID:         "test-key-1",
	}
}

func TestJWT_GenerateAndParse_CustomerAccess(t *testing.T) {
	svc := NewJWTService(newTestJWTConfig())
	pair, err := svc.GenerateCustomerTokens(123)
	require.NoError(t, err)
	assert.NotEmpty(t, pair.AccessToken)
	assert.NotEmpty(t, pair.RefreshToken)
	assert.NotEmpty(t, pair.FamilyID)

	claims, err := svc.ParseCustomerToken(pair.AccessToken)
	require.NoError(t, err)
	assert.Equal(t, int64(123), claims.UserID)
	assert.Equal(t, "access", claims.TokenType)
	assert.Equal(t, "gabon-service", claims.Issuer)
	assert.Equal(t, "customer", claims.Audience)
	assert.NotEmpty(t, claims.JTI)
	assert.NotEmpty(t, claims.FamilyID)
}

func TestJWT_GenerateAndParse_CustomerRefresh(t *testing.T) {
	svc := NewJWTService(newTestJWTConfig())
	pair, err := svc.GenerateCustomerTokens(456)
	require.NoError(t, err)

	claims, err := svc.ParseCustomerToken(pair.RefreshToken)
	require.NoError(t, err)
	assert.Equal(t, int64(456), claims.UserID)
	assert.Equal(t, "refresh", claims.TokenType)
	assert.Equal(t, pair.FamilyID, claims.FamilyID)
}

func TestJWT_RefreshPreservesFamilyID(t *testing.T) {
	svc := NewJWTService(newTestJWTConfig())
	original, err := svc.GenerateCustomerTokens(789)
	require.NoError(t, err)

	refreshed, err := svc.RefreshCustomerTokens(789, original.FamilyID)
	require.NoError(t, err)
	assert.Equal(t, original.FamilyID, refreshed.FamilyID)
	assert.NotEqual(t, original.AccessToken, refreshed.AccessToken)
}

func TestJWT_GenerateAndParse_AdminAccess(t *testing.T) {
	svc := NewJWTService(newTestJWTConfig())
	pair, err := svc.GenerateAdminTokens(1, "admin")
	require.NoError(t, err)

	claims, err := svc.ParseAdminToken(pair.AccessToken)
	require.NoError(t, err)
	assert.Equal(t, int64(1), claims.UserID)
	assert.Equal(t, "gabon-admin", claims.Issuer)
	assert.Equal(t, "admin", claims.Audience)
	assert.Equal(t, "admin", claims.Role)
}

func TestJWT_ParseCustomerToken_RejectsAdminToken(t *testing.T) {
	svc := NewJWTService(newTestJWTConfig())
	pair, err := svc.GenerateAdminTokens(1, "admin")
	require.NoError(t, err)

	_, err = svc.ParseCustomerToken(pair.AccessToken)
	assert.Error(t, err)
}

func TestJWT_ParseAdminToken_RejectsCustomerToken(t *testing.T) {
	svc := NewJWTService(newTestJWTConfig())
	pair, err := svc.GenerateCustomerTokens(123)
	require.NoError(t, err)

	_, err = svc.ParseAdminToken(pair.AccessToken)
	assert.Error(t, err)
}
