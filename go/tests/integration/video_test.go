//go:build integration

package integration

import (
	"fmt"
	"net/http"
	"sync"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// --- CI Gate: Concurrent Like ---

func TestConcurrentLike(t *testing.T) {
	// Create video owner
	env.RegisterUser(t, "like_owner", "password123")
	ownerID := env.GetCustomerID(t, "like_owner")
	videoID := env.InsertVideo(t, ownerID, "concurrent like test")

	// Create 10 likers
	const n = 10
	likerTokens := make([]AuthTokens, n)
	for i := 0; i < n; i++ {
		likerTokens[i] = env.RegisterUser(t, fmt.Sprintf("liker_%02d", i), "password123")
	}

	// Concurrent likes
	type result struct {
		status int
		err    error
	}
	ch := make(chan result, n)
	var wg sync.WaitGroup
	for i := 0; i < n; i++ {
		wg.Add(1)
		go func(token string) {
			defer wg.Done()
			resp, err := env.RequestRaw(http.MethodPost, fmt.Sprintf("/api/v1/videos/%d/like", videoID), "", token)
			if err != nil {
				ch <- result{err: err}
				return
			}
			defer resp.Body.Close()
			ch <- result{status: resp.StatusCode}
		}(likerTokens[i].AccessToken)
	}
	wg.Wait()
	close(ch)

	for r := range ch {
		require.NoError(t, r.err, "HTTP request failed")
		assert.Equal(t, http.StatusOK, r.status, "like should succeed for unique user")
	}

	// Verify like_count
	count := env.GetVideoLikeCount(t, videoID)
	assert.Equal(t, int64(n), count, "like_count should equal number of unique likers")
}

// --- Critical Path: Video List, Detail, Like/Unlike ---

func TestVideoListAndLike(t *testing.T) {
	tokens := env.RegisterUser(t, "video_list_user", "password123")
	customerID := env.GetCustomerID(t, "video_list_user")
	videoID := env.InsertVideo(t, customerID, "test video for list")

	// List videos
	resp := env.Request(t, http.MethodGet, "/api/v1/videos?page=1&page_size=20", "", "")
	body := env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	code, _ := ParseResponse[map[string]any](t, body)
	assert.Equal(t, "OK", code)

	// Get video detail
	resp = env.Request(t, http.MethodGet, fmt.Sprintf("/api/v1/videos/%d", videoID), "", tokens.AccessToken)
	body = env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	_, detail := ParseResponse[map[string]any](t, body)
	assert.Equal(t, false, detail["is_liked"])

	// Like
	resp = env.Request(t, http.MethodPost, fmt.Sprintf("/api/v1/videos/%d/like", videoID), "", tokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// Verify is_liked = true
	resp = env.Request(t, http.MethodGet, fmt.Sprintf("/api/v1/videos/%d", videoID), "", tokens.AccessToken)
	body = env.ReadBody(t, resp)
	_, detail = ParseResponse[map[string]any](t, body)
	assert.Equal(t, true, detail["is_liked"])

	// Unlike
	resp = env.Request(t, http.MethodDelete, fmt.Sprintf("/api/v1/videos/%d/like", videoID), "", tokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// Verify is_liked = false
	resp = env.Request(t, http.MethodGet, fmt.Sprintf("/api/v1/videos/%d", videoID), "", tokens.AccessToken)
	body = env.ReadBody(t, resp)
	_, detail = ParseResponse[map[string]any](t, body)
	assert.Equal(t, false, detail["is_liked"])
}

// --- Critical Path: Play Records ---

func TestPlayRecords(t *testing.T) {
	tokens := env.RegisterUser(t, "play_records_user", "password123")
	customerID := env.GetCustomerID(t, "play_records_user")
	videoID := env.InsertVideo(t, customerID, "play records test")

	// Play click
	resp := env.Request(t, http.MethodPost, fmt.Sprintf("/api/v1/videos/%d/play-click", videoID), "", tokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// Valid play
	resp = env.Request(t, http.MethodPost, fmt.Sprintf("/api/v1/videos/%d/play-valid", videoID), "", tokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// Verify counts via detail endpoint
	resp = env.Request(t, http.MethodGet, fmt.Sprintf("/api/v1/videos/%d", videoID), "", tokens.AccessToken)
	body := env.ReadBody(t, resp)
	_, detail := ParseResponse[map[string]any](t, body)

	// total_clicks and valid_clicks should be at least 1
	totalClicks, ok := detail["total_clicks"].(float64)
	require.True(t, ok, "total_clicks field missing or wrong type")
	assert.GreaterOrEqual(t, totalClicks, float64(1))

	validClicks, ok := detail["valid_clicks"].(float64)
	require.True(t, ok, "valid_clicks field missing or wrong type")
	assert.GreaterOrEqual(t, validClicks, float64(1))
}
