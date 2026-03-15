package middleware

import (
	"fmt"
	"log/slog"
	"net/http"
	"runtime"

	"github.com/labstack/echo/v4"

	"gabon-go/internal/model"
)

func Recovery() echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c echo.Context) (returnErr error) {
			defer func() {
				if r := recover(); r != nil {
					buf := make([]byte, 4096)
					n := runtime.Stack(buf, false)
					slog.Error("panic recovered",
						"error", fmt.Sprintf("%v", r),
						"stack", string(buf[:n]),
						"request_id", c.Get("request_id"),
					)
					returnErr = c.JSON(http.StatusInternalServerError, map[string]any{
						"code":    string(model.ErrInternal),
						"message": "internal server error",
						"data":    nil,
					})
				}
			}()
			return next(c)
		}
	}
}
