package transport

import (
	"context"
	"net/http"

	"github.com/danielgtaylor/huma/v2"

	"gabon-go/internal/model"
	"gabon-go/internal/service"
	"gabon-go/internal/transport/middleware"
)

type UserHandler struct {
	svc *service.UserService
}

func NewUserHandler(svc *service.UserService) *UserHandler {
	return &UserHandler{svc: svc}
}

// --- Input types ---

type PaginationParams struct {
	Page     int `query:"page" minimum:"1" default:"1" doc:"页码"`
	PageSize int `query:"page_size" minimum:"1" maximum:"100" default:"20" doc:"每页数量"`
}

type UpdateProfileInput struct {
	Body struct {
		Name      string `json:"name" doc:"昵称"`
		Phone     string `json:"phone" doc:"手机号"`
		Email     string `json:"email" doc:"邮箱"`
		AvatarURL string `json:"avatar_url" doc:"头像URL"`
		Signature string `json:"signature" doc:"签名"`
	}
}

type UserIDInput struct {
	ID int64 `path:"id" doc:"用户ID"`
}

type UserIDPaginationInput struct {
	ID int64 `path:"id" doc:"用户ID"`
	PaginationParams
}

type PaginationOnlyInput struct {
	PaginationParams
}

// --- Handlers ---

func (h *UserHandler) GetMyProfile(ctx context.Context, _ *struct{}) (*OkResponse[*service.ProfileResponse], error) {
	customerID, ok := ctx.Value(middleware.CustomerIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing customer identity")
	}
	p, err := h.svc.GetProfile(ctx, customerID)
	if err != nil {
		return nil, err
	}
	return success(p), nil
}

func (h *UserHandler) UpdateMyProfile(ctx context.Context, input *UpdateProfileInput) (*OkResponse[*service.ProfileResponse], error) {
	customerID, ok := ctx.Value(middleware.CustomerIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing customer identity")
	}
	p, err := h.svc.UpdateProfile(ctx, customerID,
		input.Body.Name, input.Body.Phone, input.Body.Email,
		input.Body.AvatarURL, input.Body.Signature)
	if err != nil {
		return nil, err
	}
	return success(p), nil
}

type AvatarUploadURLInput struct {
	Body struct {
		FileName    string `json:"fileName" doc:"文件名"`
		ContentType string `json:"contentType" doc:"MIME类型"`
	}
}

type AvatarConfirmInput struct {
	Body struct {
		AvatarURL string `json:"avatarUrl" doc:"头像URL"`
	}
}

func (h *UserHandler) GetAvatarUploadURL(ctx context.Context, input *AvatarUploadURLInput) (*OkResponse[*service.AvatarPresignResult], error) {
	customerID, ok := ctx.Value(middleware.CustomerIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing customer identity")
	}

	result, err := h.svc.GenerateAvatarUploadURL(ctx, customerID, input.Body.FileName, input.Body.ContentType)
	if err != nil {
		return nil, err
	}
	return success(result), nil
}

func (h *UserHandler) ConfirmAvatar(ctx context.Context, input *AvatarConfirmInput) (*OkResponse[any], error) {
	customerID, ok := ctx.Value(middleware.CustomerIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing customer identity")
	}

	if err := h.svc.ConfirmAvatarUpload(ctx, customerID, input.Body.AvatarURL); err != nil {
		return nil, err
	}
	return success[any](map[string]string{"avatarUrl": input.Body.AvatarURL}), nil
}

func (h *UserHandler) GetUserProfile(ctx context.Context, input *UserIDInput) (*OkResponse[*service.PublicProfileResponse], error) {
	var viewerID *int64
	if id, ok := ctx.Value(middleware.CustomerIDKey).(int64); ok {
		viewerID = &id
	}
	p, err := h.svc.GetPublicProfile(ctx, input.ID, viewerID)
	if err != nil {
		return nil, err
	}
	return success(p), nil
}

func (h *UserHandler) Follow(ctx context.Context, input *UserIDInput) (*OkResponse[any], error) {
	customerID, ok := ctx.Value(middleware.CustomerIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing customer identity")
	}
	if err := h.svc.Follow(ctx, customerID, input.ID); err != nil {
		return nil, err
	}
	return success[any](nil), nil
}

func (h *UserHandler) Unfollow(ctx context.Context, input *UserIDInput) (*OkResponse[any], error) {
	customerID, ok := ctx.Value(middleware.CustomerIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing customer identity")
	}
	if err := h.svc.Unfollow(ctx, customerID, input.ID); err != nil {
		return nil, err
	}
	return success[any](nil), nil
}

func (h *UserHandler) GetMyFollowing(ctx context.Context, input *PaginationOnlyInput) (*OkResponse[PagedData[service.FollowUserItem]], error) {
	customerID, ok := ctx.Value(middleware.CustomerIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing customer identity")
	}
	items, total, err := h.svc.GetFollowing(ctx, customerID, input.Page, input.PageSize, &customerID)
	if err != nil {
		return nil, err
	}
	return pagedSuccess(items, total, input.Page, input.PageSize), nil
}

func (h *UserHandler) GetMyFollowers(ctx context.Context, input *PaginationOnlyInput) (*OkResponse[PagedData[service.FollowUserItem]], error) {
	customerID, ok := ctx.Value(middleware.CustomerIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing customer identity")
	}
	items, total, err := h.svc.GetFollowers(ctx, customerID, input.Page, input.PageSize, &customerID)
	if err != nil {
		return nil, err
	}
	return pagedSuccess(items, total, input.Page, input.PageSize), nil
}

func (h *UserHandler) GetUserFollowing(ctx context.Context, input *UserIDPaginationInput) (*OkResponse[PagedData[service.FollowUserItem]], error) {
	var viewerID *int64
	if id, ok := ctx.Value(middleware.CustomerIDKey).(int64); ok {
		viewerID = &id
	}
	items, total, err := h.svc.GetFollowing(ctx, input.ID, input.Page, input.PageSize, viewerID)
	if err != nil {
		return nil, err
	}
	return pagedSuccess(items, total, input.Page, input.PageSize), nil
}

func (h *UserHandler) GetUserFollowers(ctx context.Context, input *UserIDPaginationInput) (*OkResponse[PagedData[service.FollowUserItem]], error) {
	var viewerID *int64
	if id, ok := ctx.Value(middleware.CustomerIDKey).(int64); ok {
		viewerID = &id
	}
	items, total, err := h.svc.GetFollowers(ctx, input.ID, input.Page, input.PageSize, viewerID)
	if err != nil {
		return nil, err
	}
	return pagedSuccess(items, total, input.Page, input.PageSize), nil
}

// --- Route registration ---

func (h *UserHandler) RegisterRoutes(api huma.API, authCfg middleware.AuthConfig, rlPub, rlUser middleware.RateLimitConfig) {
	meAuth := huma.Middlewares{middleware.RequireCustomerAuth(authCfg), middleware.RateLimit(rlUser)}
	pubOpt := huma.Middlewares{middleware.OptionalCustomerAuth(authCfg), middleware.RateLimit(rlPub)}
	userAuth := huma.Middlewares{middleware.RequireCustomerAuth(authCfg), middleware.RateLimit(rlUser)}

	// /me endpoints
	huma.Register(api, huma.Operation{
		OperationID: "get-my-profile",
		Method:      http.MethodGet,
		Path:        "/api/v1/users/me/profile",
		Summary:     "获取我的资料",
		Tags:        []string{"User"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: meAuth,
	}, h.GetMyProfile)

	huma.Register(api, huma.Operation{
		OperationID: "update-my-profile",
		Method:      http.MethodPut,
		Path:        "/api/v1/users/me/profile",
		Summary:     "更新我的资料",
		Tags:        []string{"User"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: meAuth,
	}, h.UpdateMyProfile)

	huma.Register(api, huma.Operation{
		OperationID: "get-avatar-upload-url",
		Method:      http.MethodPost,
		Path:        "/api/v1/users/me/avatar/upload-url",
		Summary:     "获取头像上传预签名URL",
		Tags:        []string{"User"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: meAuth,
	}, h.GetAvatarUploadURL)

	huma.Register(api, huma.Operation{
		OperationID: "confirm-avatar-upload",
		Method:      http.MethodPost,
		Path:        "/api/v1/users/me/avatar/confirm",
		Summary:     "确认头像上传",
		Tags:        []string{"User"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: meAuth,
	}, h.ConfirmAvatar)

	huma.Register(api, huma.Operation{
		OperationID: "get-my-following",
		Method:      http.MethodGet,
		Path:        "/api/v1/users/me/following",
		Summary:     "获取我的关注列表",
		Tags:        []string{"User"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: meAuth,
	}, h.GetMyFollowing)

	huma.Register(api, huma.Operation{
		OperationID: "get-my-followers",
		Method:      http.MethodGet,
		Path:        "/api/v1/users/me/followers",
		Summary:     "获取我的粉丝列表",
		Tags:        []string{"User"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: meAuth,
	}, h.GetMyFollowers)

	// Public user routes
	huma.Register(api, huma.Operation{
		OperationID: "get-user-profile",
		Method:      http.MethodGet,
		Path:        "/api/v1/users/{id}",
		Summary:     "获取用户资料",
		Tags:        []string{"User"},
		Middlewares: pubOpt,
	}, h.GetUserProfile)

	huma.Register(api, huma.Operation{
		OperationID: "get-user-following",
		Method:      http.MethodGet,
		Path:        "/api/v1/users/{id}/following",
		Summary:     "获取用户关注列表",
		Tags:        []string{"User"},
		Middlewares: pubOpt,
	}, h.GetUserFollowing)

	huma.Register(api, huma.Operation{
		OperationID: "get-user-followers",
		Method:      http.MethodGet,
		Path:        "/api/v1/users/{id}/followers",
		Summary:     "获取用户粉丝列表",
		Tags:        []string{"User"},
		Middlewares: pubOpt,
	}, h.GetUserFollowers)

	// Follow/unfollow
	huma.Register(api, huma.Operation{
		OperationID: "follow-user",
		Method:      http.MethodPost,
		Path:        "/api/v1/users/{id}/follow",
		Summary:     "关注用户",
		Tags:        []string{"User"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: userAuth,
	}, h.Follow)

	huma.Register(api, huma.Operation{
		OperationID: "unfollow-user",
		Method:      http.MethodDelete,
		Path:        "/api/v1/users/{id}/follow",
		Summary:     "取消关注",
		Tags:        []string{"User"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: userAuth,
	}, h.Unfollow)
}
