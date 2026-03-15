package model

import "net/http"

type ErrorCode string

const (
	ErrOK                 ErrorCode = "OK"
	ErrInternal           ErrorCode = "INTERNAL_ERROR"
	ErrBadRequest         ErrorCode = "BAD_REQUEST"
	ErrNotFound           ErrorCode = "NOT_FOUND"
	ErrUnauthorized       ErrorCode = "UNAUTHORIZED"
	ErrForbidden          ErrorCode = "FORBIDDEN"
	ErrInvalidCredentials ErrorCode = "AUTH_INVALID_CREDENTIALS"
	ErrTokenExpired       ErrorCode = "AUTH_TOKEN_EXPIRED"
	ErrTokenInvalid       ErrorCode = "AUTH_TOKEN_INVALID"
	ErrUsernameExists     ErrorCode = "AUTH_USERNAME_EXISTS"
	ErrPasswordMismatch   ErrorCode = "AUTH_PASSWORD_MISMATCH"
	ErrVideoNotFound      ErrorCode = "VIDEO_NOT_FOUND"
	ErrVideoNotApproved   ErrorCode = "VIDEO_NOT_APPROVED"
	ErrAlreadyLiked       ErrorCode = "VIDEO_ALREADY_LIKED"
	ErrAlreadyFollowing   ErrorCode = "USER_ALREADY_FOLLOWING"
	ErrCannotFollowSelf   ErrorCode = "USER_CANNOT_FOLLOW_SELF"
	ErrNotFollowing       ErrorCode = "USER_NOT_FOLLOWING"
	ErrTaskNotClaimable   ErrorCode = "TASK_NOT_CLAIMABLE"
	ErrAlreadySignedIn    ErrorCode = "ALREADY_SIGNED_IN"
	ErrRateLimited        ErrorCode = "RATE_LIMITED"
)

var statusMap = map[ErrorCode]int{
	ErrInternal:           http.StatusInternalServerError,
	ErrBadRequest:         http.StatusBadRequest,
	ErrNotFound:           http.StatusNotFound,
	ErrUnauthorized:       http.StatusUnauthorized,
	ErrForbidden:          http.StatusForbidden,
	ErrInvalidCredentials: http.StatusUnauthorized,
	ErrTokenExpired:       http.StatusUnauthorized,
	ErrTokenInvalid:       http.StatusUnauthorized,
	ErrUsernameExists:     http.StatusConflict,
	ErrPasswordMismatch:   http.StatusBadRequest,
	ErrVideoNotFound:      http.StatusNotFound,
	ErrVideoNotApproved:   http.StatusForbidden,
	ErrAlreadyLiked:       http.StatusConflict,
	ErrAlreadyFollowing:   http.StatusConflict,
	ErrCannotFollowSelf:   http.StatusBadRequest,
	ErrNotFollowing:       http.StatusBadRequest,
	ErrTaskNotClaimable:   http.StatusBadRequest,
	ErrAlreadySignedIn:    http.StatusConflict,
	ErrRateLimited:        http.StatusTooManyRequests,
}

type AppError struct {
	Code    ErrorCode
	Message string
	Status  int
	Err     error
}

func (e *AppError) Error() string {
	return string(e.Code) + ": " + e.Message
}

func (e *AppError) Unwrap() error {
	return e.Err
}

func (e *AppError) GetStatus() int {
	if e.Status != 0 {
		return e.Status
	}
	return http.StatusInternalServerError
}

func NewAppError(code ErrorCode, message string) *AppError {
	return &AppError{
		Code:    code,
		Message: message,
		Status:  statusMap[code],
	}
}

func WrapError(code ErrorCode, message string, err error) *AppError {
	return &AppError{
		Code:    code,
		Message: message,
		Status:  statusMap[code],
		Err:     err,
	}
}
