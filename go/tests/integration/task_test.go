//go:build integration

package integration

import (
	"fmt"
	"net/http"
	"sync"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"gabon-go/internal/service"
)

// --- CI Gate: Concurrent Claim ---

func TestConcurrentClaim(t *testing.T) {
	tokens := env.RegisterUser(t, "conc_claim_user", "password123")
	customerID := env.GetCustomerID(t, "conc_claim_user")

	// Insert a daily watch task: taskType=1, taskCategory=1, targetCount=1, reward=100
	taskDefID := env.InsertTaskDefinition(t, "CONC_CLAIM_TEST", "Concurrent Claim Test", 1, 1, 1, 100)
	periodKey := service.PeriodKey(1, time.Now())
	// Insert progress with status=2 (completed), ready to claim
	progressID := env.InsertTaskProgress(t, customerID, taskDefID, periodKey, 1, 100, 2)

	balanceBefore := env.GetDiamondBalance(t, customerID)

	const n = 10
	var wg sync.WaitGroup

	type result struct {
		status int
		err    error
	}
	ch := make(chan result, n)

	for i := 0; i < n; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			resp, err := env.RequestRaw(http.MethodPost,
				fmt.Sprintf("/api/v1/tasks/%d/claim", progressID), "", tokens.AccessToken)
			if err != nil {
				ch <- result{err: err}
				return
			}
			defer resp.Body.Close()
			ch <- result{status: resp.StatusCode}
		}()
	}
	wg.Wait()
	close(ch)

	successCount := 0
	for r := range ch {
		require.NoError(t, r.err, "HTTP request failed")
		if r.status == http.StatusOK {
			successCount++
		} else {
			assert.Equal(t, http.StatusBadRequest, r.status, "failed claim should return 400")
		}
	}
	assert.Equal(t, 1, successCount, "exactly one claim should succeed")

	balanceAfter := env.GetDiamondBalance(t, customerID)
	assert.Equal(t, balanceBefore+100, balanceAfter, "diamonds should increase by reward amount exactly once")
}

// --- Critical Path: Task Progress and Claim ---

func TestTaskProgressAndClaim(t *testing.T) {
	tokens := env.RegisterUser(t, "task_progress_user", "password123")
	customerID := env.GetCustomerID(t, "task_progress_user")

	// Insert a daily watch task (category=1) requiring 2 valid plays, reward 50 diamonds
	env.InsertTaskDefinition(t, "WATCH_2_PLAYS", "Watch 2 Videos", 1, 1, 2, 50)

	// Insert an approved video to play
	videoID := env.InsertVideo(t, customerID, "task progress video")

	// List tasks — this upserts progress rows for all active definitions
	resp := env.Request(t, http.MethodGet, "/api/v1/tasks", "", tokens.AccessToken)
	body := env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	code, _ := ParseResponse[any](t, body)
	assert.Equal(t, "OK", code)

	// Two valid plays to complete the task
	// play-valid uses OptionalCustomerAuth, so token is passed but optional
	for i := 0; i < 2; i++ {
		resp = env.Request(t, http.MethodPost,
			fmt.Sprintf("/api/v1/videos/%d/play-valid", videoID), "", tokens.AccessToken)
		require.Equal(t, http.StatusOK, resp.StatusCode)
		resp.Body.Close()
	}

	// Get progress ID from task list
	resp = env.Request(t, http.MethodGet, "/api/v1/tasks", "", tokens.AccessToken)
	body = env.ReadBody(t, resp)
	_, taskData := ParseResponse[[]map[string]any](t, body)

	var progressID int64
	var taskStatus float64
	for _, task := range taskData {
		if task["task_code"] == "WATCH_2_PLAYS" {
			progressID = int64(task["progress_id"].(float64))
			taskStatus = task["task_status"].(float64)
			break
		}
	}
	require.NotZero(t, progressID, "should find task progress for WATCH_2_PLAYS")
	require.Equal(t, float64(2), taskStatus, "task should be completed (status=2) after reaching target count")

	// Claim reward
	balanceBefore := env.GetDiamondBalance(t, customerID)
	resp = env.Request(t, http.MethodPost,
		fmt.Sprintf("/api/v1/tasks/%d/claim", progressID), "", tokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	balanceAfter := env.GetDiamondBalance(t, customerID)
	assert.Equal(t, balanceBefore+50, balanceAfter, "diamonds should increase by reward amount")

	// Claim again should fail (status is now 3=claimed, not 2=completed)
	resp = env.Request(t, http.MethodPost,
		fmt.Sprintf("/api/v1/tasks/%d/claim", progressID), "", tokens.AccessToken)
	claimBody := env.ReadBody(t, resp)
	assert.Equal(t, http.StatusBadRequest, resp.StatusCode, "duplicate claim should return 400")
	claimCode, _ := ParseResponse[any](t, claimBody)
	assert.Equal(t, "TASK_NOT_CLAIMABLE", claimCode, "duplicate claim should return TASK_NOT_CLAIMABLE")
}
