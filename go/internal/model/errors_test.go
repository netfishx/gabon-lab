package model

import (
	"errors"
	"net/http"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestAppError_Error(t *testing.T) {
	err := NewAppError(ErrUnauthorized, "not logged in")
	assert.Equal(t, "UNAUTHORIZED: not logged in", err.Error())
	assert.Equal(t, http.StatusUnauthorized, err.Status)
}

func TestAppError_Unwrap(t *testing.T) {
	inner := errors.New("db connection failed")
	err := WrapError(ErrInternal, "something broke", inner)
	assert.True(t, errors.Is(err, inner))
}

func TestAppError_Is(t *testing.T) {
	err := NewAppError(ErrNotFound, "video not found")
	var appErr *AppError
	assert.True(t, errors.As(err, &appErr))
	assert.Equal(t, ErrNotFound, appErr.Code)
}
