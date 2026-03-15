//go:build integration

package integration

import (
	"fmt"
	"net/http"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// --- Report Endpoints ---

func TestReportEndpoints(t *testing.T) {
	env.InsertAdminUser(t, "report_admin", "password123", 2)
	tokens := env.AdminLogin(t, "report_admin", "password123")

	// Seed data: a customer with an approved video + a claimed task
	env.RegisterUser(t, "report_customer", "password123")
	customerID := env.GetCustomerID(t, "report_customer")
	env.InsertVideo(t, customerID, "report video")

	taskDefID := env.InsertTaskDefinition(t, "RPT_DAILY", "Report Task", 1, 1, 1, 10)
	env.InsertTaskProgress(t, customerID, taskDefID, "2026-02-20", 1, 10, 3) // status=3 → claimed_at=NOW()

	// Dynamic date window: yesterday ~ tomorrow (avoids hard-coded time bomb)
	now := time.Now()
	startDate := now.AddDate(0, 0, -1).Format("2006-01-02")
	endDate := now.AddDate(0, 0, 2).Format("2006-01-02")

	t.Run("revenue", func(t *testing.T) {
		resp := env.Request(t, http.MethodGet,
			fmt.Sprintf("/admin/v1/reports/revenue?start_date=%s&end_date=%s&page=1&page_size=20", startDate, endDate),
			"", tokens.AccessToken)
		body := env.ReadBody(t, resp)
		require.Equal(t, http.StatusOK, resp.StatusCode)
		code, data := ParseResponse[map[string]any](t, body)
		assert.Equal(t, "OK", code)
		assert.Contains(t, data, "items")
		assert.Contains(t, data, "total")

		items, ok := data["items"].([]any)
		require.True(t, ok, "items should be an array")
		assert.NotEmpty(t, items, "should have at least one revenue record from seeded claimed task")

		first := items[0].(map[string]any)
		assert.Contains(t, first, "date")
		assert.Contains(t, first, "total_diamonds")
		assert.GreaterOrEqual(t, first["total_diamonds"].(float64), float64(10))
	})

	t.Run("video_daily", func(t *testing.T) {
		resp := env.Request(t, http.MethodGet,
			fmt.Sprintf("/admin/v1/reports/video/daily?start_date=%s&end_date=%s&page=1&page_size=20", startDate, endDate),
			"", tokens.AccessToken)
		body := env.ReadBody(t, resp)
		require.Equal(t, http.StatusOK, resp.StatusCode)
		code, data := ParseResponse[map[string]any](t, body)
		assert.Equal(t, "OK", code)
		assert.Contains(t, data, "items")
		assert.Contains(t, data, "total")

		items, ok := data["items"].([]any)
		require.True(t, ok, "items should be an array")
		assert.NotEmpty(t, items, "should have at least one daily record")

		first := items[0].(map[string]any)
		assert.Contains(t, first, "date")
		assert.Contains(t, first, "upload_count")
	})

	t.Run("video_summary", func(t *testing.T) {
		resp := env.Request(t, http.MethodGet,
			fmt.Sprintf("/admin/v1/reports/video/summary?start_date=%s&end_date=%s", startDate, endDate),
			"", tokens.AccessToken)
		body := env.ReadBody(t, resp)
		require.Equal(t, http.StatusOK, resp.StatusCode)
		code, data := ParseResponse[map[string]any](t, body)
		assert.Equal(t, "OK", code)

		totalVideos, ok := data["total_videos"].(float64)
		require.True(t, ok, "total_videos should be a number")
		assert.GreaterOrEqual(t, totalVideos, float64(1), "should count at least the seeded video")
		assert.Contains(t, data, "approved_count")
		assert.Contains(t, data, "pending_count")
		assert.Contains(t, data, "rejected_count")
	})
}
