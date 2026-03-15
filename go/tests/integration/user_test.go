//go:build integration

package integration

import (
	"fmt"
	"net/http"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// --- Critical Path: Follow/Unfollow ---

func TestFollowUnfollow(t *testing.T) {
	tokensA := env.RegisterUser(t, "follow_user_a", "password123")
	tokensB := env.RegisterUser(t, "follow_user_b", "password123")
	userBID := env.GetCustomerID(t, "follow_user_b")

	// A follows B
	resp := env.Request(t, http.MethodPost, fmt.Sprintf("/api/v1/users/%d/follow", userBID), "", tokensA.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// A's following list should contain B
	resp = env.Request(t, http.MethodGet, "/api/v1/users/me/following?page=1&page_size=20", "", tokensA.AccessToken)
	body := env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	_, followingData := ParseResponse[map[string]any](t, body)
	items, ok := followingData["items"].([]any)
	require.True(t, ok, "items field should be an array")
	require.GreaterOrEqual(t, len(items), 1)
	assert.Equal(t, float64(userBID), items[0].(map[string]any)["user_id"], "following list should contain user B")

	// B's followers list should contain A
	userAID := env.GetCustomerID(t, "follow_user_a")
	resp = env.Request(t, http.MethodGet, fmt.Sprintf("/api/v1/users/%d/followers?page=1&page_size=20", userBID), "", tokensB.AccessToken)
	body = env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	_, followersData := ParseResponse[map[string]any](t, body)
	fItems, ok := followersData["items"].([]any)
	require.True(t, ok, "items field should be an array")
	require.GreaterOrEqual(t, len(fItems), 1)
	assert.Equal(t, float64(userAID), fItems[0].(map[string]any)["user_id"], "followers list should contain user A")

	// A unfollows B
	resp = env.Request(t, http.MethodDelete, fmt.Sprintf("/api/v1/users/%d/follow", userBID), "", tokensA.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// A's following list should be empty (for this pair)
	resp = env.Request(t, http.MethodGet, "/api/v1/users/me/following?page=1&page_size=20", "", tokensA.AccessToken)
	body = env.ReadBody(t, resp)
	_, followingAfter := ParseResponse[map[string]any](t, body)
	assert.Equal(t, float64(0), followingAfter["total"])
}

// --- Critical Path: Follow Self ---

func TestFollowSelf(t *testing.T) {
	tokens := env.RegisterUser(t, "follow_self_user", "password123")
	userID := env.GetCustomerID(t, "follow_self_user")

	resp := env.Request(t, http.MethodPost, fmt.Sprintf("/api/v1/users/%d/follow", userID), "", tokens.AccessToken)
	assert.Equal(t, http.StatusBadRequest, resp.StatusCode)

	body := env.ReadBody(t, resp)
	code, _ := ParseResponse[any](t, body)
	assert.Equal(t, "USER_CANNOT_FOLLOW_SELF", code)
}

// --- Critical Path: Update Profile ---

func TestUpdateProfile(t *testing.T) {
	tokens := env.RegisterUser(t, "profile_update_user", "password123")

	// Update profile
	updateBody := `{"name":"Test Name","phone":"1234567890","email":"test@example.com","avatar_url":"","signature":"Hello world"}`
	resp := env.Request(t, http.MethodPut, "/api/v1/users/me/profile", updateBody, tokens.AccessToken)
	body := env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)

	code, profile := ParseResponse[map[string]any](t, body)
	assert.Equal(t, "OK", code)
	assert.Equal(t, "Test Name", profile["name"])
	assert.Equal(t, "1234567890", profile["phone"])
	assert.Equal(t, "test@example.com", profile["email"])
	assert.Equal(t, "Hello world", profile["signature"])

	// Verify via GET
	resp = env.Request(t, http.MethodGet, "/api/v1/users/me/profile", "", tokens.AccessToken)
	body = env.ReadBody(t, resp)
	_, fetched := ParseResponse[map[string]any](t, body)
	assert.Equal(t, "Test Name", fetched["name"])
	assert.Equal(t, "Hello world", fetched["signature"])
}
