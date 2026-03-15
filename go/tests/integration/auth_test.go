//go:build integration

package integration

import (
	"fmt"
	"net/http"
	"os"
	"sync"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

var env *TestEnv

func TestMain(m *testing.M) {
	var cleanup func()
	env, cleanup = Setup()
	code := m.Run()
	cleanup()
	os.Exit(code)
}

// --- CI Gate: Concurrent Refresh ---

func TestConcurrentRefresh(t *testing.T) {
	tokens := env.RegisterUser(t, "conc_refresh_user", "password123")

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
			body := fmt.Sprintf(`{"refresh_token":"%s"}`, tokens.RefreshToken)
			resp, err := env.RequestRaw(http.MethodPost, "/api/v1/auth/refresh", body, "")
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
			assert.Equal(t, http.StatusUnauthorized, r.status)
		}
	}
	assert.Equal(t, 1, successCount, "exactly one refresh should succeed")
}

// --- CI Gate: Logout Then Refresh ---

func TestLogoutThenRefresh(t *testing.T) {
	tokens := env.RegisterUser(t, "logout_refresh_user", "password123")

	// Logout with access token
	resp := env.Request(t, http.MethodPost, "/api/v1/auth/logout", "", tokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// Try refresh — should fail
	body := fmt.Sprintf(`{"refresh_token":"%s"}`, tokens.RefreshToken)
	resp = env.Request(t, http.MethodPost, "/api/v1/auth/refresh", body, "")
	assert.Equal(t, http.StatusUnauthorized, resp.StatusCode)
	resp.Body.Close()
}

// --- Critical Path: Full Auth Lifecycle ---

func TestFullAuthLifecycle(t *testing.T) {
	username := "lifecycle_user"
	password := "password123"
	newPassword := "newpassword456"

	// Register
	tokens := env.RegisterUser(t, username, password)

	// Get me
	resp := env.Request(t, http.MethodGet, "/api/v1/auth/me", "", tokens.AccessToken)
	body := env.ReadBody(t, resp)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	code, _ := ParseResponse[map[string]any](t, body)
	assert.Equal(t, "OK", code)

	// Change password
	changePwdBody := fmt.Sprintf(`{"old_password":"%s","new_password":"%s"}`, password, newPassword)
	resp = env.Request(t, http.MethodPut, "/api/v1/auth/password", changePwdBody, tokens.AccessToken)
	require.Equal(t, http.StatusOK, resp.StatusCode)
	resp.Body.Close()

	// Login with new password
	newTokens := env.LoginUser(t, username, newPassword)
	assert.NotEmpty(t, newTokens.AccessToken)

	// Old password should fail
	resp = env.Request(t, http.MethodPost, "/api/v1/auth/login",
		fmt.Sprintf(`{"username":"%s","password":"%s"}`, username, password), "")
	assert.Equal(t, http.StatusUnauthorized, resp.StatusCode)
	resp.Body.Close()
}

// --- Critical Path: Duplicate Register ---

func TestDuplicateRegister(t *testing.T) {
	username := "dup_register_user"
	env.RegisterUser(t, username, "password123")

	// Second registration should fail
	body := fmt.Sprintf(`{"username":"%s","password":"password123"}`, username)
	resp := env.Request(t, http.MethodPost, "/api/v1/auth/register", body, "")
	assert.Equal(t, http.StatusConflict, resp.StatusCode)

	respBody := env.ReadBody(t, resp)
	code, _ := ParseResponse[any](t, respBody)
	assert.Equal(t, "AUTH_USERNAME_EXISTS", code)
}
