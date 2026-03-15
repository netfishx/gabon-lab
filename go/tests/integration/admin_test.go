//go:build integration

package integration

import (
	"fmt"
	"net/http"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// --- Admin Auth Lifecycle ---

func TestAdminAuthLifecycle(t *testing.T) {
	env.InsertAdminUser(t, "admin_lifecycle", "password123", 1)
	tokens := env.AdminLogin(t, "admin_lifecycle", "password123")

	// GET /admin/v1/auth/me
	resp := env.Request(t, http.MethodGet, "/admin/v1/auth/me", "", tokens.AccessToken)
	body := env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	code, profile := ParseResponse[map[string]any](t, body)
	assert.Equal(t, "OK", code)
	assert.Equal(t, "admin_lifecycle", profile["username"])

	// Refresh
	refreshBody := fmt.Sprintf(`{"refresh_token":"%s"}`, tokens.RefreshToken)
	resp = env.Request(t, http.MethodPost, "/admin/v1/auth/refresh", refreshBody, "")
	body = env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	_, newTokens := ParseResponse[AuthTokens](t, body)
	assert.NotEmpty(t, newTokens.AccessToken)

	// Logout with new access token
	resp = env.Request(t, http.MethodPost, "/admin/v1/auth/logout", "", newTokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// Logged-out token should be rejected (JTI blacklisted)
	resp = env.Request(t, http.MethodGet, "/admin/v1/auth/me", "", newTokens.AccessToken)
	assert.Equal(t, http.StatusUnauthorized, resp.StatusCode, "logged-out token should be blacklisted")
	resp.Body.Close()

	// Original token should still work (different JTI, not blacklisted)
	resp = env.Request(t, http.MethodGet, "/admin/v1/auth/me", "", tokens.AccessToken)
	assert.Equal(t, http.StatusOK, resp.StatusCode, "original token should remain valid")
	resp.Body.Close()
}

// --- Auth Domain Isolation ---

func TestAdminAuthDomainIsolation(t *testing.T) {
	// Customer token cannot access admin routes
	customerTokens := env.RegisterUser(t, "domain_iso_customer", "password123")
	resp := env.Request(t, http.MethodGet, "/admin/v1/auth/me", "", customerTokens.AccessToken)
	assert.Equal(t, http.StatusUnauthorized, resp.StatusCode)
	resp.Body.Close()

	// Admin token cannot access customer routes
	env.InsertAdminUser(t, "domain_iso_admin", "password123", 2)
	adminTokens := env.AdminLogin(t, "domain_iso_admin", "password123")
	resp = env.Request(t, http.MethodGet, "/api/v1/auth/me", "", adminTokens.AccessToken)
	assert.Equal(t, http.StatusUnauthorized, resp.StatusCode)
	resp.Body.Close()
}

// --- Admin CRUD (superadmin) ---

func TestAdminCRUD(t *testing.T) {
	env.InsertAdminUser(t, "crud_superadmin", "password123", 1)
	tokens := env.AdminLogin(t, "crud_superadmin", "password123")

	// Create admin
	createBody := `{"username":"crud_new_admin","password":"password123","role":2,"full_name":"New Admin","phone":"1234567890"}`
	resp := env.Request(t, http.MethodPost, "/admin/v1/admin-users", createBody, tokens.AccessToken)
	body := env.ReadBody(t, resp)
	require.Equal(t, http.StatusCreated, resp.StatusCode)
	code, created := ParseResponse[map[string]any](t, body)
	assert.Equal(t, "OK", code)
	newAdminID := int64(created["id"].(float64))
	assert.Equal(t, "crud_new_admin", created["username"])
	assert.Equal(t, float64(2), created["role"])

	// List admins — should find the new admin
	resp = env.Request(t, http.MethodGet, "/admin/v1/admin-users?page=1&page_size=50", "", tokens.AccessToken)
	body = env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	code, listData := ParseResponse[map[string]any](t, body)
	assert.Equal(t, "OK", code)

	items, ok := listData["items"].([]any)
	require.True(t, ok, "items should be an array")
	foundNewAdmin := false
	for _, item := range items {
		m := item.(map[string]any)
		if int64(m["id"].(float64)) == newAdminID {
			foundNewAdmin = true
			break
		}
	}
	assert.True(t, foundNewAdmin, "new admin should appear in list")

	// Get admin detail
	resp = env.Request(t, http.MethodGet, fmt.Sprintf("/admin/v1/admin-users/%d", newAdminID), "", tokens.AccessToken)
	body = env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	_, detail := ParseResponse[map[string]any](t, body)
	assert.Equal(t, "New Admin", detail["full_name"])

	// Update admin
	updateBody := `{"full_name":"Updated Admin","phone":"9876543210"}`
	resp = env.Request(t, http.MethodPut, fmt.Sprintf("/admin/v1/admin-users/%d", newAdminID), updateBody, tokens.AccessToken)
	body = env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	_, updated := ParseResponse[map[string]any](t, body)
	assert.Equal(t, "Updated Admin", updated["full_name"])

	// Delete admin (soft delete)
	resp = env.Request(t, http.MethodDelete, fmt.Sprintf("/admin/v1/admin-users/%d", newAdminID), "", tokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// Deleted admin should not be found
	resp = env.Request(t, http.MethodGet, fmt.Sprintf("/admin/v1/admin-users/%d", newAdminID), "", tokens.AccessToken)
	assert.Equal(t, http.StatusNotFound, resp.StatusCode)
	resp.Body.Close()
}

// --- Admin RBAC ---

func TestAdminRBAC(t *testing.T) {
	env.InsertAdminUser(t, "rbac_superadmin", "password123", 1)
	superTokens := env.AdminLogin(t, "rbac_superadmin", "password123")

	env.InsertAdminUser(t, "rbac_regular", "password123", 2)
	regularTokens := env.AdminLogin(t, "rbac_regular", "password123")

	// Regular admin cannot create admin → 403
	createBody := `{"username":"rbac_attempt","password":"password123","role":2,"full_name":"Attempt","phone":"0000000000"}`
	resp := env.Request(t, http.MethodPost, "/admin/v1/admin-users", createBody, regularTokens.AccessToken)
	body := env.ReadBody(t, resp)
	assert.Equal(t, http.StatusForbidden, resp.StatusCode)
	code, _ := ParseResponse[any](t, body)
	assert.Equal(t, "FORBIDDEN", code)

	// Regular admin cannot delete another admin → 403
	superAdminID := env.GetAdminID(t, "rbac_superadmin")
	resp = env.Request(t, http.MethodDelete, fmt.Sprintf("/admin/v1/admin-users/%d", superAdminID), "", regularTokens.AccessToken)
	body = env.ReadBody(t, resp)
	assert.Equal(t, http.StatusForbidden, resp.StatusCode)
	code, _ = ParseResponse[any](t, body)
	assert.Equal(t, "FORBIDDEN", code)

	// Regular admin CAN list admins → 200
	resp = env.Request(t, http.MethodGet, "/admin/v1/admin-users?page=1&page_size=20", "", regularTokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// Regular admin cannot change another admin's password → 403
	changePwdBody := `{"new_password":"hacked123"}`
	resp = env.Request(t, http.MethodPut, fmt.Sprintf("/admin/v1/admin-users/%d/password", superAdminID), changePwdBody, regularTokens.AccessToken)
	body = env.ReadBody(t, resp)
	assert.Equal(t, http.StatusForbidden, resp.StatusCode)
	code, _ = ParseResponse[any](t, body)
	assert.Equal(t, "FORBIDDEN", code)

	// Superadmin CAN change another admin's password → 200
	resp = env.Request(t, http.MethodPut, fmt.Sprintf("/admin/v1/admin-users/%d/password", env.GetAdminID(t, "rbac_regular")),
		`{"new_password":"newpass123"}`, superTokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()
}

// --- Video Review ---

func TestVideoReview(t *testing.T) {
	env.InsertAdminUser(t, "review_admin", "password123", 2)
	adminTokens := env.AdminLogin(t, "review_admin", "password123")

	// Create a customer with pending videos
	env.RegisterUser(t, "review_customer", "password123")
	customerID := env.GetCustomerID(t, "review_customer")
	approveVideoID := env.InsertPendingVideo(t, customerID, "video to approve")
	rejectVideoID := env.InsertPendingVideo(t, customerID, "video to reject")

	// Approve video
	resp := env.Request(t, http.MethodPost, fmt.Sprintf("/admin/v1/videos/%d/review", approveVideoID),
		`{"status":4,"review_notes":"looks good"}`, adminTokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	assert.Equal(t, int16(4), env.GetVideoStatus(t, approveVideoID))

	// Reject video
	resp = env.Request(t, http.MethodPost, fmt.Sprintf("/admin/v1/videos/%d/review", rejectVideoID),
		`{"status":5,"review_notes":"inappropriate content"}`, adminTokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	assert.Equal(t, int16(5), env.GetVideoStatus(t, rejectVideoID))

	// Admin list videos — both should appear
	resp = env.Request(t, http.MethodGet, "/admin/v1/videos?page=1&page_size=50", "", adminTokens.AccessToken)
	body := env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	code, listData := ParseResponse[map[string]any](t, body)
	assert.Equal(t, "OK", code)

	items, ok := listData["items"].([]any)
	require.True(t, ok, "items should be an array")
	foundIDs := map[int64]bool{}
	for _, item := range items {
		m := item.(map[string]any)
		foundIDs[int64(m["id"].(float64))] = true
	}
	assert.True(t, foundIDs[approveVideoID], "approved video should appear in list")
	assert.True(t, foundIDs[rejectVideoID], "rejected video should appear in list")

	// Admin get video detail — verify review info
	resp = env.Request(t, http.MethodGet, fmt.Sprintf("/admin/v1/videos/%d", approveVideoID), "", adminTokens.AccessToken)
	body = env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	_, detail := ParseResponse[map[string]any](t, body)
	assert.Equal(t, float64(4), detail["status"])
	assert.Equal(t, "looks good", detail["review_notes"])
}

// --- Customer Password Reset ---

func TestCustomerPasswordReset(t *testing.T) {
	env.InsertAdminUser(t, "reset_admin", "password123", 2)
	adminTokens := env.AdminLogin(t, "reset_admin", "password123")

	env.RegisterUser(t, "reset_customer", "oldpassword")
	customerID := env.GetCustomerID(t, "reset_customer")

	// Admin resets customer password
	resp := env.Request(t, http.MethodPut, fmt.Sprintf("/admin/v1/customers/%d/password", customerID),
		`{"new_password":"newpassword"}`, adminTokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// Customer login with old password → fail
	resp = env.Request(t, http.MethodPost, "/api/v1/auth/login",
		`{"username":"reset_customer","password":"oldpassword"}`, "")
	assert.Equal(t, http.StatusUnauthorized, resp.StatusCode)
	resp.Body.Close()

	// Customer login with new password → success
	newTokens := env.LoginUser(t, "reset_customer", "newpassword")
	assert.NotEmpty(t, newTokens.AccessToken)
}

