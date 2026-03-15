package transport

import (
	"errors"
	"net/http"
	"strings"

	"github.com/danielgtaylor/huma/v2"
	"github.com/labstack/echo/v4"

	"gabon-go/internal/model"
)

// OkBody is the unified success response envelope.
type OkBody[T any] struct {
	Code    string `json:"code"`
	Message string `json:"message"`
	Data    T      `json:"data"`
}

// OkResponse wraps OkBody as a Huma output struct.
type OkResponse[T any] struct {
	Body OkBody[T]
}

// PagedData holds a paginated list with metadata.
type PagedData[T any] struct {
	Items    []T   `json:"items"`
	Total    int64 `json:"total"`
	Page     int   `json:"page"`
	PageSize int   `json:"page_size"`
}

func success[T any](data T) *OkResponse[T] {
	return &OkResponse[T]{
		Body: OkBody[T]{Code: "OK", Message: "ok", Data: data},
	}
}

func pagedSuccess[T any](items []T, total int64, page, pageSize int) *OkResponse[PagedData[T]] {
	return &OkResponse[PagedData[T]]{
		Body: OkBody[PagedData[T]]{
			Code:    "OK",
			Message: "ok",
			Data: PagedData[T]{
				Items: items, Total: total,
				Page: page, PageSize: pageSize,
			},
		},
	}
}

// apiError is the error response for Huma — matches {code, message, data: null}.
type apiError struct {
	status  int
	Code    string `json:"code"`
	Message string `json:"message"`
	Data    any    `json:"data"`
}

func (e *apiError) Error() string  { return e.Code + ": " + e.Message }
func (e *apiError) GetStatus() int { return e.status }

func httpStatusToCode(status int) model.ErrorCode {
	switch status {
	case http.StatusBadRequest, http.StatusUnprocessableEntity:
		return model.ErrBadRequest
	case http.StatusUnauthorized:
		return model.ErrUnauthorized
	case http.StatusForbidden:
		return model.ErrForbidden
	case http.StatusNotFound:
		return model.ErrNotFound
	case http.StatusTooManyRequests:
		return model.ErrRateLimited
	default:
		return model.ErrInternal
	}
}

func init() {
	huma.NewError = func(status int, msg string, errs ...error) huma.StatusError {
		// Preserve AppError code/message when available.
		for _, e := range errs {
			var appErr *model.AppError
			if errors.As(e, &appErr) {
				return &apiError{
					status:  appErr.GetStatus(),
					Code:    string(appErr.Code),
					Message: appErr.Message,
				}
			}
		}

		// Include Huma validation details in the message.
		if len(errs) > 0 {
			var parts []string
			for _, e := range errs {
				parts = append(parts, e.Error())
			}
			msg = strings.Join(parts, "; ")
		}

		return &apiError{
			status:  status,
			Code:    string(httpStatusToCode(status)),
			Message: msg,
		}
	}
}

// CustomErrorHandler handles Echo-level errors (non-Huma routes like /health).
func CustomErrorHandler(err error, c echo.Context) {
	if c.Response().Committed {
		return
	}
	var he *echo.HTTPError
	if errors.As(err, &he) {
		msg, _ := he.Message.(string)
		_ = c.JSON(he.Code, apiError{
			Code:    string(httpStatusToCode(he.Code)),
			Message: msg,
		})
		return
	}
	var appErr *model.AppError
	if errors.As(err, &appErr) {
		_ = c.JSON(appErr.GetStatus(), apiError{
			Code:    string(appErr.Code),
			Message: appErr.Message,
		})
		return
	}
	_ = c.JSON(http.StatusInternalServerError, apiError{
		Code:    string(model.ErrInternal),
		Message: "internal server error",
	})
}
