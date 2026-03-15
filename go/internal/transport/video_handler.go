package transport

import (
	"context"
	"net/http"

	"github.com/danielgtaylor/huma/v2"

	"gabon-go/internal/model"
	"gabon-go/internal/service"
	"gabon-go/internal/transport/middleware"
)

type VideoHandler struct {
	svc     *service.VideoService
	taskSvc *service.TaskService
}

func NewVideoHandler(svc *service.VideoService, taskSvc *service.TaskService) *VideoHandler {
	return &VideoHandler{svc: svc, taskSvc: taskSvc}
}

// --- Input types ---

type VideoIDInput struct {
	ID int64 `path:"id" doc:"视频ID"`
}

type VideoIDPaginationInput struct {
	ID int64 `path:"id" doc:"用户ID"`
	PaginationParams
}

type VideoListInput struct {
	PaginationParams
	Keyword string `query:"keyword" doc:"标题关键词搜索"`
}

type MyVideoListInput struct {
	PaginationParams
	Status int16 `query:"status" doc:"视频状态过滤"`
}

// --- Input types for presigned URL upload ---

type VideoUploadURLInput struct {
	Body struct {
		FileName    string `json:"fileName" doc:"文件名"`
		ContentType string `json:"contentType" doc:"MIME类型"`
	}
}

type ConfirmVideoUploadInput struct {
	Body service.ConfirmVideoUploadRequest
}

// --- Handlers ---

func (h *VideoHandler) GetVideoUploadURL(ctx context.Context, input *VideoUploadURLInput) (*OkResponse[*service.PresignedUploadResult], error) {
	customerID, ok := ctx.Value(middleware.CustomerIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing customer identity")
	}

	result, err := h.svc.GenerateVideoUploadURL(ctx, customerID, input.Body.FileName, input.Body.ContentType)
	if err != nil {
		return nil, err
	}
	return success(result), nil
}

func (h *VideoHandler) ConfirmVideoUpload(ctx context.Context, input *ConfirmVideoUploadInput) (*OkResponse[any], error) {
	customerID, ok := ctx.Value(middleware.CustomerIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing customer identity")
	}

	video, err := h.svc.ConfirmVideoUpload(ctx, customerID, &input.Body)
	if err != nil {
		return nil, err
	}
	return success[any](video), nil
}

func (h *VideoHandler) ListVideos(ctx context.Context, input *VideoListInput) (*OkResponse[PagedData[service.VideoDetail]], error) {
	var keyword *string
	if input.Keyword != "" {
		keyword = &input.Keyword
	}
	items, total, err := h.svc.ListVideos(ctx, input.Page, input.PageSize, keyword)
	if err != nil {
		return nil, err
	}
	return pagedSuccess(items, total, input.Page, input.PageSize), nil
}

func (h *VideoHandler) ListFeatured(ctx context.Context, input *VideoListInput) (*OkResponse[PagedData[service.VideoDetail]], error) {
	var keyword *string
	if input.Keyword != "" {
		keyword = &input.Keyword
	}
	items, total, err := h.svc.ListFeatured(ctx, input.Page, input.PageSize, keyword)
	if err != nil {
		return nil, err
	}
	return pagedSuccess(items, total, input.Page, input.PageSize), nil
}

func (h *VideoHandler) GetVideo(ctx context.Context, input *VideoIDInput) (*OkResponse[*service.VideoDetail], error) {
	var customerID *int64
	if id, ok := ctx.Value(middleware.CustomerIDKey).(int64); ok {
		customerID = &id
	}
	detail, err := h.svc.GetVideo(ctx, input.ID, customerID)
	if err != nil {
		return nil, err
	}
	return success(detail), nil
}

func (h *VideoHandler) ListMyVideos(ctx context.Context, input *MyVideoListInput) (*OkResponse[PagedData[service.VideoDetail]], error) {
	customerID, ok := ctx.Value(middleware.CustomerIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing customer identity")
	}
	var status *int16
	if input.Status != 0 {
		status = &input.Status
	}
	items, total, err := h.svc.ListMyVideos(ctx, customerID, input.Page, input.PageSize, status)
	if err != nil {
		return nil, err
	}
	return pagedSuccess(items, total, input.Page, input.PageSize), nil
}

func (h *VideoHandler) ListUserVideos(ctx context.Context, input *VideoIDPaginationInput) (*OkResponse[PagedData[service.VideoDetail]], error) {
	items, total, err := h.svc.ListUserVideos(ctx, input.ID, input.Page, input.PageSize)
	if err != nil {
		return nil, err
	}
	return pagedSuccess(items, total, input.Page, input.PageSize), nil
}

func (h *VideoHandler) DeleteVideo(ctx context.Context, input *VideoIDInput) (*OkResponse[any], error) {
	customerID, ok := ctx.Value(middleware.CustomerIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing customer identity")
	}
	if err := h.svc.DeleteVideo(ctx, input.ID, customerID); err != nil {
		return nil, err
	}
	return success[any](nil), nil
}

func (h *VideoHandler) Like(ctx context.Context, input *VideoIDInput) (*OkResponse[any], error) {
	customerID, ok := ctx.Value(middleware.CustomerIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing customer identity")
	}
	if err := h.svc.Like(ctx, input.ID, customerID); err != nil {
		return nil, err
	}
	return success[any](nil), nil
}

func (h *VideoHandler) Unlike(ctx context.Context, input *VideoIDInput) (*OkResponse[any], error) {
	customerID, ok := ctx.Value(middleware.CustomerIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing customer identity")
	}
	if err := h.svc.Unlike(ctx, input.ID, customerID); err != nil {
		return nil, err
	}
	return success[any](nil), nil
}

func (h *VideoHandler) PlayClick(ctx context.Context, input *VideoIDInput) (*OkResponse[any], error) {
	var customerID *int64
	if id, ok := ctx.Value(middleware.CustomerIDKey).(int64); ok {
		customerID = &id
	}
	ip, _ := ctx.Value(middleware.RealIPKey).(string)
	if err := h.svc.RecordClick(ctx, input.ID, customerID, ip); err != nil {
		return nil, err
	}
	return success[any](nil), nil
}

func (h *VideoHandler) PlayValid(ctx context.Context, input *VideoIDInput) (*OkResponse[any], error) {
	var customerID *int64
	if id, ok := ctx.Value(middleware.CustomerIDKey).(int64); ok {
		customerID = &id
	}
	ip, _ := ctx.Value(middleware.RealIPKey).(string)
	if err := h.svc.RecordValidPlay(ctx, input.ID, customerID, ip); err != nil {
		return nil, err
	}
	if customerID != nil && h.taskSvc != nil {
		h.taskSvc.OnValidPlay(ctx, *customerID)
	}
	return success[any](nil), nil
}

// --- Route registration ---

func (h *VideoHandler) RegisterRoutes(api huma.API, authCfg middleware.AuthConfig, rlPub, rlUser middleware.RateLimitConfig) {
	pubRL := huma.Middlewares{middleware.RateLimit(rlPub)}
	pubOpt := huma.Middlewares{middleware.OptionalCustomerAuth(authCfg), middleware.RateLimit(rlPub)}
	userAuth := huma.Middlewares{middleware.RequireCustomerAuth(authCfg), middleware.RateLimit(rlUser)}

	huma.Register(api, huma.Operation{
		OperationID: "list-videos",
		Method:      http.MethodGet,
		Path:        "/api/v1/videos",
		Summary:     "视频列表",
		Tags:        []string{"Video"},
		Middlewares: pubRL,
	}, h.ListVideos)

	huma.Register(api, huma.Operation{
		OperationID: "list-featured-videos",
		Method:      http.MethodGet,
		Path:        "/api/v1/videos/featured",
		Summary:     "推荐视频列表",
		Tags:        []string{"Video"},
		Middlewares: pubRL,
	}, h.ListFeatured)

	huma.Register(api, huma.Operation{
		OperationID: "get-video",
		Method:      http.MethodGet,
		Path:        "/api/v1/videos/{id}",
		Summary:     "获取视频详情",
		Tags:        []string{"Video"},
		Middlewares: pubOpt,
	}, h.GetVideo)

	huma.Register(api, huma.Operation{
		OperationID: "get-video-upload-url",
		Method:      http.MethodPost,
		Path:        "/api/v1/videos/upload-url",
		Summary:     "获取视频上传预签名URL",
		Tags:        []string{"Video"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: userAuth,
	}, h.GetVideoUploadURL)

	huma.Register(api, huma.Operation{
		OperationID:   "confirm-video-upload",
		Method:        http.MethodPost,
		Path:          "/api/v1/videos/confirm-upload",
		Summary:       "确认视频上传",
		Tags:          []string{"Video"},
		Security:      []map[string][]string{{"bearerAuth": {}}},
		DefaultStatus: http.StatusCreated,
		Middlewares:   userAuth,
	}, h.ConfirmVideoUpload)

	huma.Register(api, huma.Operation{
		OperationID: "list-my-videos",
		Method:      http.MethodGet,
		Path:        "/api/v1/videos/me",
		Summary:     "我的视频列表",
		Tags:        []string{"Video"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: userAuth,
	}, h.ListMyVideos)

	huma.Register(api, huma.Operation{
		OperationID: "delete-video",
		Method:      http.MethodDelete,
		Path:        "/api/v1/videos/{id}",
		Summary:     "删除视频",
		Tags:        []string{"Video"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: userAuth,
	}, h.DeleteVideo)

	huma.Register(api, huma.Operation{
		OperationID: "like-video",
		Method:      http.MethodPost,
		Path:        "/api/v1/videos/{id}/like",
		Summary:     "点赞视频",
		Tags:        []string{"Video"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: userAuth,
	}, h.Like)

	huma.Register(api, huma.Operation{
		OperationID: "unlike-video",
		Method:      http.MethodDelete,
		Path:        "/api/v1/videos/{id}/like",
		Summary:     "取消点赞",
		Tags:        []string{"Video"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: userAuth,
	}, h.Unlike)

	huma.Register(api, huma.Operation{
		OperationID: "play-click",
		Method:      http.MethodPost,
		Path:        "/api/v1/videos/{id}/play-click",
		Summary:     "记录点击播放",
		Tags:        []string{"Video"},
		Middlewares: pubOpt,
	}, h.PlayClick)

	huma.Register(api, huma.Operation{
		OperationID: "play-valid",
		Method:      http.MethodPost,
		Path:        "/api/v1/videos/{id}/play-valid",
		Summary:     "记录有效播放",
		Tags:        []string{"Video"},
		Middlewares: pubOpt,
	}, h.PlayValid)

	// User videos (under /users/:id/videos)
	huma.Register(api, huma.Operation{
		OperationID: "list-user-videos",
		Method:      http.MethodGet,
		Path:        "/api/v1/users/{id}/videos",
		Summary:     "获取用户视频列表",
		Tags:        []string{"Video"},
		Middlewares: pubOpt,
	}, h.ListUserVideos)
}
