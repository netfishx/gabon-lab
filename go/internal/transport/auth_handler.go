package transport

import (
	"context"
	"net/http"

	"github.com/danielgtaylor/huma/v2"

	"gabon-go/internal/model"
	"gabon-go/internal/service"
	"gabon-go/internal/transport/middleware"
)

type AuthHandler struct {
	svc *service.AuthService
}

func NewAuthHandler(svc *service.AuthService) *AuthHandler {
	return &AuthHandler{svc: svc}
}

// --- Input types ---

type RegisterInput struct {
	Body struct {
		Username string `json:"username" minLength:"3" maxLength:"100" doc:"用户名"`
		Password string `json:"password" minLength:"6" maxLength:"100" doc:"密码"`
	}
}

type LoginInput struct {
	Body struct {
		Username string `json:"username" minLength:"1" doc:"用户名"`
		Password string `json:"password" minLength:"1" doc:"密码"`
	}
}

type RefreshInput struct {
	Body struct {
		RefreshToken string `json:"refresh_token" minLength:"1" doc:"刷新令牌"`
	}
}

type ChangePasswordInput struct {
	Body struct {
		OldPassword string `json:"old_password" minLength:"1" doc:"旧密码"`
		NewPassword string `json:"new_password" minLength:"6" maxLength:"100" doc:"新密码"`
	}
}

// --- Handlers ---

func (h *AuthHandler) Register(ctx context.Context, input *RegisterInput) (*OkResponse[*service.AuthResponse], error) {
	resp, err := h.svc.Register(ctx, input.Body.Username, input.Body.Password)
	if err != nil {
		return nil, err
	}
	return success(resp), nil
}

func (h *AuthHandler) Login(ctx context.Context, input *LoginInput) (*OkResponse[*service.AuthResponse], error) {
	resp, err := h.svc.Login(ctx, input.Body.Username, input.Body.Password)
	if err != nil {
		return nil, err
	}
	return success(resp), nil
}

func (h *AuthHandler) Refresh(ctx context.Context, input *RefreshInput) (*OkResponse[*service.AuthResponse], error) {
	resp, err := h.svc.Refresh(ctx, input.Body.RefreshToken)
	if err != nil {
		return nil, err
	}
	return success(resp), nil
}

func (h *AuthHandler) Logout(ctx context.Context, _ *struct{}) (*OkResponse[any], error) {
	claims, ok := ctx.Value(middleware.ClaimsKey).(*service.TokenClaims)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing token claims")
	}
	if err := h.svc.Logout(ctx, claims); err != nil {
		return nil, err
	}
	return success[any](nil), nil
}

func (h *AuthHandler) GetMe(ctx context.Context, _ *struct{}) (*OkResponse[*service.CustomerInfo], error) {
	customerID, ok := ctx.Value(middleware.CustomerIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing customer identity")
	}
	info, err := h.svc.GetMe(ctx, customerID)
	if err != nil {
		return nil, err
	}
	return success(info), nil
}

func (h *AuthHandler) ChangePassword(ctx context.Context, input *ChangePasswordInput) (*OkResponse[any], error) {
	customerID, ok := ctx.Value(middleware.CustomerIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing customer identity")
	}
	if err := h.svc.ChangePassword(ctx, customerID, input.Body.OldPassword, input.Body.NewPassword); err != nil {
		return nil, err
	}
	return success[any](nil), nil
}

// --- Route registration ---

func (h *AuthHandler) RegisterRoutes(api huma.API, authCfg middleware.AuthConfig, rlAuth middleware.RateLimitConfig) {
	rl := huma.Middlewares{middleware.RateLimit(rlAuth)}
	authRL := huma.Middlewares{middleware.RequireCustomerAuth(authCfg), middleware.RateLimit(rlAuth)}

	huma.Register(api, huma.Operation{
		OperationID:   "register",
		Method:        http.MethodPost,
		Path:          "/api/v1/auth/register",
		Summary:       "注册",
		Tags:          []string{"Auth"},
		DefaultStatus: http.StatusCreated,
		Middlewares:   rl,
	}, h.Register)

	huma.Register(api, huma.Operation{
		OperationID: "login",
		Method:      http.MethodPost,
		Path:        "/api/v1/auth/login",
		Summary:     "登录",
		Tags:        []string{"Auth"},
		Middlewares: rl,
	}, h.Login)

	huma.Register(api, huma.Operation{
		OperationID: "refresh-token",
		Method:      http.MethodPost,
		Path:        "/api/v1/auth/refresh",
		Summary:     "刷新令牌",
		Tags:        []string{"Auth"},
		Middlewares: rl,
	}, h.Refresh)

	huma.Register(api, huma.Operation{
		OperationID: "logout",
		Method:      http.MethodPost,
		Path:        "/api/v1/auth/logout",
		Summary:     "登出",
		Tags:        []string{"Auth"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: authRL,
	}, h.Logout)

	huma.Register(api, huma.Operation{
		OperationID: "get-me",
		Method:      http.MethodGet,
		Path:        "/api/v1/auth/me",
		Summary:     "获取当前用户信息",
		Tags:        []string{"Auth"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: authRL,
	}, h.GetMe)

	huma.Register(api, huma.Operation{
		OperationID: "change-password",
		Method:      http.MethodPut,
		Path:        "/api/v1/auth/password",
		Summary:     "修改密码",
		Tags:        []string{"Auth"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: authRL,
	}, h.ChangePassword)
}
