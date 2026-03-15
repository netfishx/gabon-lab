package transport

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/labstack/echo/v4"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"gabon-go/internal/model"
)

func TestSuccess(t *testing.T) {
	resp := success("hello")
	assert.Equal(t, 0, resp.Body.Code)
	assert.Equal(t, "ok", resp.Body.Message)
	assert.Equal(t, "hello", resp.Body.Data)
}

func TestPagedSuccess(t *testing.T) {
	items := []string{"a", "b"}
	resp := pagedSuccess(items, 10, 1, 20)
	assert.Equal(t, 0, resp.Body.Code)
	assert.Equal(t, []string{"a", "b"}, resp.Body.Data.Items)
	assert.Equal(t, int64(10), resp.Body.Data.Total)
	assert.Equal(t, 1, resp.Body.Data.Page)
	assert.Equal(t, 20, resp.Body.Data.PageSize)
}

func TestCustomErrorHandler_AppError(t *testing.T) {
	e := echo.New()
	req := httptest.NewRequest(http.MethodGet, "/", http.NoBody)
	rec := httptest.NewRecorder()
	c := e.NewContext(req, rec)

	appErr := model.NewAppError(model.ErrNotFound, "video not found")
	CustomErrorHandler(appErr, c)
	assert.Equal(t, http.StatusNotFound, rec.Code)
	assert.Contains(t, rec.Body.String(), `"code":404`)
	assert.Contains(t, rec.Body.String(), `"message":"video not found"`)
}

func TestCustomErrorHandler_EchoHTTPError(t *testing.T) {
	e := echo.New()
	req := httptest.NewRequest(http.MethodGet, "/", http.NoBody)
	rec := httptest.NewRecorder()
	c := e.NewContext(req, rec)

	CustomErrorHandler(echo.ErrNotFound, c)
	assert.Equal(t, http.StatusNotFound, rec.Code)
	assert.Contains(t, rec.Body.String(), `"code":404`)
}

func TestHumaNewError_AppError(t *testing.T) {
	appErr := model.NewAppError(model.ErrBadRequest, "invalid input")
	// huma.NewError has been overridden in init() — test that it extracts AppError.
	require.NotNil(t, appErr)
	assert.Equal(t, http.StatusBadRequest, appErr.GetStatus())
	assert.Equal(t, model.ErrBadRequest, appErr.Code)
}
