package transport

import (
	"context"
	"fmt"
	"net/http"
	"time"

	"github.com/danielgtaylor/huma/v2"

	"gabon-go/internal/model"
	"gabon-go/internal/service"
	"gabon-go/internal/transport/middleware"
)

type ReportHandler struct {
	svc *service.ReportService
}

func NewReportHandler(svc *service.ReportService) *ReportHandler {
	return &ReportHandler{svc: svc}
}

// --- Input types ---

type DateRangeInput struct {
	StartDate string `query:"start_date" doc:"开始日期 (YYYY-MM-DD)"`
	EndDate   string `query:"end_date" doc:"结束日期 (YYYY-MM-DD)"`
	PaginationParams
}

type DateRangeOnlyInput struct {
	StartDate string `query:"start_date" doc:"开始日期 (YYYY-MM-DD)"`
	EndDate   string `query:"end_date" doc:"结束日期 (YYYY-MM-DD)"`
}

// parseDateRange converts date strings to time.Time with sensible defaults.
func parseDateRange(startStr, endStr string) (start, end time.Time, err error) {
	if startStr == "" || endStr == "" {
		end = time.Now().Truncate(24 * time.Hour).Add(24 * time.Hour)
		start = end.Add(-30 * 24 * time.Hour)
		return start, end, nil
	}

	start, err = time.Parse("2006-01-02", startStr)
	if err != nil {
		return time.Time{}, time.Time{}, model.NewAppError(model.ErrBadRequest,
			fmt.Sprintf("invalid start_date format, expected YYYY-MM-DD: %s", startStr))
	}

	end, err = time.Parse("2006-01-02", endStr)
	if err != nil {
		return time.Time{}, time.Time{}, model.NewAppError(model.ErrBadRequest,
			fmt.Sprintf("invalid end_date format, expected YYYY-MM-DD: %s", endStr))
	}

	end = end.Add(24 * time.Hour) // exclusive end
	return start, end, nil
}

// --- Handlers ---

func (h *ReportHandler) Revenue(ctx context.Context, input *DateRangeInput) (*OkResponse[PagedData[service.RevenueItem]], error) {
	start, end, err := parseDateRange(input.StartDate, input.EndDate)
	if err != nil {
		return nil, err
	}
	items, total, err := h.svc.Revenue(ctx, start, end, input.Page, input.PageSize)
	if err != nil {
		return nil, err
	}
	return pagedSuccess(items, total, input.Page, input.PageSize), nil
}

func (h *ReportHandler) VideosDaily(ctx context.Context, input *DateRangeInput) (*OkResponse[PagedData[service.VideoDailyItem]], error) {
	start, end, err := parseDateRange(input.StartDate, input.EndDate)
	if err != nil {
		return nil, err
	}
	items, total, err := h.svc.VideosDaily(ctx, start, end, input.Page, input.PageSize)
	if err != nil {
		return nil, err
	}
	return pagedSuccess(items, total, input.Page, input.PageSize), nil
}

func (h *ReportHandler) VideoSummary(ctx context.Context, input *DateRangeOnlyInput) (*OkResponse[*service.VideoSummary], error) {
	start, end, err := parseDateRange(input.StartDate, input.EndDate)
	if err != nil {
		return nil, err
	}
	summary, err := h.svc.VideoSummary(ctx, start, end)
	if err != nil {
		return nil, err
	}
	return success(summary), nil
}

// --- Route registration ---

func (h *ReportHandler) RegisterRoutes(api huma.API, authCfg middleware.AuthConfig, rlAdmin middleware.RateLimitConfig) {
	adminRL := huma.Middlewares{middleware.RequireAdminAuth(authCfg), middleware.RateLimit(rlAdmin)}

	huma.Register(api, huma.Operation{
		OperationID: "report-revenue",
		Method:      http.MethodGet,
		Path:        "/admin/v1/reports/revenue",
		Summary:     "收入报表",
		Tags:        []string{"Admin Report"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: adminRL,
	}, h.Revenue)

	huma.Register(api, huma.Operation{
		OperationID: "report-videos-daily",
		Method:      http.MethodGet,
		Path:        "/admin/v1/reports/video/daily",
		Summary:     "视频日报",
		Tags:        []string{"Admin Report"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: adminRL,
	}, h.VideosDaily)

	huma.Register(api, huma.Operation{
		OperationID: "report-video-summary",
		Method:      http.MethodGet,
		Path:        "/admin/v1/reports/video/summary",
		Summary:     "视频汇总",
		Tags:        []string{"Admin Report"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: adminRL,
	}, h.VideoSummary)
}
