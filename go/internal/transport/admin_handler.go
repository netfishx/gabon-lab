package transport

import (
	"context"
	"net/http"
	"time"

	"github.com/danielgtaylor/huma/v2"

	"gabon-go/internal/model"
	"gabon-go/internal/service"
	"gabon-go/internal/transport/middleware"
)

type AdminHandler struct {
	svc *service.AdminService
}

func NewAdminHandler(svc *service.AdminService) *AdminHandler {
	return &AdminHandler{svc: svc}
}

// --- Input types ---

type AdminLoginInput struct {
	Body struct {
		Username string `json:"username" minLength:"1" doc:"用户名"`
		Password string `json:"password" minLength:"1" doc:"密码"`
	}
}

type AdminIDInput struct {
	ID int64 `path:"id" doc:"管理员ID"`
}

type CreateAdminInput struct {
	Body struct {
		Username string `json:"username" minLength:"1" doc:"用户名"`
		Password string `json:"password" minLength:"6" doc:"密码"`
		Role     int16  `json:"role" minimum:"1" maximum:"2" doc:"角色 (1=superadmin, 2=admin)"`
		FullName string `json:"full_name" doc:"姓名"`
		Phone    string `json:"phone" doc:"手机号"`
	}
}

type UpdateAdminInput struct {
	ID   int64 `path:"id" doc:"管理员ID"`
	Body struct {
		FullName string `json:"full_name" doc:"姓名"`
		Phone    string `json:"phone" doc:"手机号"`
		Role     *int16 `json:"role,omitempty" doc:"角色"`
		Status   *int16 `json:"status,omitempty" doc:"状态"`
	}
}

type ChangeAdminPasswordInput struct {
	ID   int64 `path:"id" doc:"管理员ID"`
	Body struct {
		NewPassword string `json:"new_password" minLength:"6" doc:"新密码"`
	}
}

type CustomerIDInput struct {
	ID int64 `path:"id" doc:"客户ID"`
}

type ResetCustomerPasswordInput struct {
	ID   int64 `path:"id" doc:"客户ID"`
	Body struct {
		NewPassword string `json:"new_password" minLength:"6" doc:"新密码"`
	}
}

type AdminRefreshInput struct {
	Body struct {
		RefreshToken string `json:"refresh_token" minLength:"1" doc:"刷新令牌"`
	}
}

type ListAdminsInput struct {
	PaginationParams
	Username string `query:"username" doc:"用户名模糊搜索"`
	Role     int16  `query:"role" doc:"角色过滤 (1=superadmin, 2=admin)"`
	Status   int16  `query:"status" doc:"状态过滤"`
}

type ListCustomersInput struct {
	PaginationParams
	Name  string `query:"name" doc:"姓名模糊搜索"`
	IsVip string `query:"is_vip" doc:"VIP过滤 (true/false)"`
}

type AdminListVideosInput struct {
	PaginationParams
	AuthorName string `query:"author_name" doc:"作者名模糊搜索"`
	Status     int16  `query:"status" doc:"视频状态过滤"`
	IsVip      string `query:"is_vip" doc:"作者VIP过滤 (true/false)"`
	StartDate  string `query:"start_date" doc:"开始日期 (YYYY-MM-DD)"`
	EndDate    string `query:"end_date" doc:"结束日期 (YYYY-MM-DD)"`
}

type ReviewVideoInput struct {
	ID   int64 `path:"id" doc:"视频ID"`
	Body struct {
		Status      int16  `json:"status" minimum:"4" maximum:"5" doc:"审核状态 (4=通过, 5=拒绝)"`
		ReviewNotes string `json:"review_notes" doc:"审核备注"`
	}
}

// --- Handlers ---

func (h *AdminHandler) Login(ctx context.Context, input *AdminLoginInput) (*OkResponse[*service.AuthResponse], error) {
	resp, err := h.svc.Login(ctx, &service.AdminLoginRequest{
		Username: input.Body.Username,
		Password: input.Body.Password,
	})
	if err != nil {
		return nil, err
	}
	return success(resp), nil
}

func (h *AdminHandler) Refresh(ctx context.Context, input *AdminRefreshInput) (*OkResponse[*service.AuthResponse], error) {
	resp, err := h.svc.Refresh(ctx, input.Body.RefreshToken)
	if err != nil {
		return nil, err
	}
	return success(resp), nil
}

func (h *AdminHandler) Logout(ctx context.Context, _ *struct{}) (*OkResponse[any], error) {
	claims, ok := ctx.Value(middleware.ClaimsKey).(*service.TokenClaims)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing claims")
	}
	if err := h.svc.Logout(ctx, claims); err != nil {
		return nil, err
	}
	return success[any](nil), nil
}

func (h *AdminHandler) GetMe(ctx context.Context, _ *struct{}) (*OkResponse[*service.AdminProfile], error) {
	adminID, ok := ctx.Value(middleware.AdminIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing admin identity")
	}
	profile, err := h.svc.GetMe(ctx, adminID)
	if err != nil {
		return nil, err
	}
	return success(profile), nil
}

func (h *AdminHandler) ListAdmins(ctx context.Context, input *ListAdminsInput) (*OkResponse[PagedData[service.AdminListItem]], error) {
	var filter service.AdminListFilter
	if input.Username != "" {
		filter.Username = &input.Username
	}
	if input.Role != 0 {
		filter.Role = &input.Role
	}
	if input.Status != 0 {
		filter.Status = &input.Status
	}
	items, total, err := h.svc.ListAdmins(ctx, input.Page, input.PageSize, filter)
	if err != nil {
		return nil, err
	}
	return pagedSuccess(items, total, input.Page, input.PageSize), nil
}

func (h *AdminHandler) CreateAdmin(ctx context.Context, input *CreateAdminInput) (*OkResponse[*service.AdminProfile], error) {
	role, _ := ctx.Value(middleware.AdminRoleKey).(string)
	if role != "superadmin" {
		return nil, model.NewAppError(model.ErrForbidden, "superadmin required")
	}
	profile, err := h.svc.CreateAdmin(ctx, &service.CreateAdminRequest{
		Username: input.Body.Username,
		Password: input.Body.Password,
		Role:     input.Body.Role,
		FullName: input.Body.FullName,
		Phone:    input.Body.Phone,
	})
	if err != nil {
		return nil, err
	}
	return success(profile), nil
}

func (h *AdminHandler) GetAdmin(ctx context.Context, input *AdminIDInput) (*OkResponse[*service.AdminProfile], error) {
	profile, err := h.svc.GetAdmin(ctx, input.ID)
	if err != nil {
		return nil, err
	}
	return success(profile), nil
}

func (h *AdminHandler) UpdateAdmin(ctx context.Context, input *UpdateAdminInput) (*OkResponse[*service.AdminProfile], error) {
	if input.Body.Role != nil || input.Body.Status != nil {
		role, _ := ctx.Value(middleware.AdminRoleKey).(string)
		if role != "superadmin" {
			return nil, model.NewAppError(model.ErrForbidden, "superadmin required to change role or status")
		}
	}
	profile, err := h.svc.UpdateAdmin(ctx, input.ID, &service.UpdateAdminRequest{
		FullName: input.Body.FullName,
		Phone:    input.Body.Phone,
		Role:     input.Body.Role,
		Status:   input.Body.Status,
	})
	if err != nil {
		return nil, err
	}
	return success(profile), nil
}

func (h *AdminHandler) DeleteAdmin(ctx context.Context, input *AdminIDInput) (*OkResponse[any], error) {
	role, _ := ctx.Value(middleware.AdminRoleKey).(string)
	if role != "superadmin" {
		return nil, model.NewAppError(model.ErrForbidden, "superadmin required")
	}
	adminID, _ := ctx.Value(middleware.AdminIDKey).(int64)
	if input.ID == adminID {
		return nil, model.NewAppError(model.ErrBadRequest, "cannot delete yourself")
	}
	if err := h.svc.DeleteAdmin(ctx, input.ID); err != nil {
		return nil, err
	}
	return success[any](nil), nil
}

func (h *AdminHandler) ChangeAdminPassword(ctx context.Context, input *ChangeAdminPasswordInput) (*OkResponse[any], error) {
	adminID, _ := ctx.Value(middleware.AdminIDKey).(int64)
	role, _ := ctx.Value(middleware.AdminRoleKey).(string)
	if input.ID != adminID && role != "superadmin" {
		return nil, model.NewAppError(model.ErrForbidden, "cannot change other admin's password")
	}
	if err := h.svc.ChangeAdminPassword(ctx, input.ID, input.Body.NewPassword); err != nil {
		return nil, err
	}
	return success[any](nil), nil
}

func (h *AdminHandler) ListCustomers(ctx context.Context, input *ListCustomersInput) (*OkResponse[PagedData[service.CustomerListItem]], error) {
	var filter service.CustomerListFilter
	if input.Name != "" {
		filter.Name = &input.Name
	}
	if input.IsVip == "true" {
		v := true
		filter.IsVip = &v
	} else if input.IsVip == "false" {
		v := false
		filter.IsVip = &v
	}
	items, total, err := h.svc.ListCustomers(ctx, input.Page, input.PageSize, filter)
	if err != nil {
		return nil, err
	}
	return pagedSuccess(items, total, input.Page, input.PageSize), nil
}

func (h *AdminHandler) ResetCustomerPassword(ctx context.Context, input *ResetCustomerPasswordInput) (*OkResponse[any], error) {
	if err := h.svc.ResetCustomerPassword(ctx, input.ID, input.Body.NewPassword); err != nil {
		return nil, err
	}
	return success[any](nil), nil
}

func (h *AdminHandler) AdminListVideos(ctx context.Context, input *AdminListVideosInput) (*OkResponse[PagedData[service.AdminVideoItem]], error) {
	var filter service.AdminVideoFilter
	if input.AuthorName != "" {
		filter.AuthorName = &input.AuthorName
	}
	if input.Status != 0 {
		filter.Status = &input.Status
	}
	if input.IsVip == "true" {
		v := true
		filter.IsVip = &v
	} else if input.IsVip == "false" {
		v := false
		filter.IsVip = &v
	}
	if input.StartDate != "" {
		t, err := time.Parse("2006-01-02", input.StartDate)
		if err != nil {
			return nil, model.NewAppError(model.ErrBadRequest, "invalid start_date format, expected YYYY-MM-DD")
		}
		filter.StartDate = &t
	}
	if input.EndDate != "" {
		t, err := time.Parse("2006-01-02", input.EndDate)
		if err != nil {
			return nil, model.NewAppError(model.ErrBadRequest, "invalid end_date format, expected YYYY-MM-DD")
		}
		// End date exclusive: add 1 day
		t = t.AddDate(0, 0, 1)
		filter.EndDate = &t
	}
	items, total, err := h.svc.ListVideos(ctx, input.Page, input.PageSize, filter)
	if err != nil {
		return nil, err
	}
	return pagedSuccess(items, total, input.Page, input.PageSize), nil
}

func (h *AdminHandler) GetVideoDetail(ctx context.Context, input *AdminIDInput) (*OkResponse[*service.AdminVideoDetail], error) {
	detail, err := h.svc.GetVideoDetail(ctx, input.ID)
	if err != nil {
		return nil, err
	}
	return success(detail), nil
}

func (h *AdminHandler) ReviewVideo(ctx context.Context, input *ReviewVideoInput) (*OkResponse[any], error) {
	adminID, ok := ctx.Value(middleware.AdminIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing admin identity")
	}
	if err := h.svc.ReviewVideo(ctx, input.ID, adminID, &service.ReviewVideoRequest{
		Status:      input.Body.Status,
		ReviewNotes: input.Body.ReviewNotes,
	}); err != nil {
		return nil, err
	}
	return success[any](nil), nil
}

func (h *AdminHandler) AdminDeleteVideo(ctx context.Context, input *AdminIDInput) (*OkResponse[any], error) {
	if err := h.svc.DeleteVideo(ctx, input.ID); err != nil {
		return nil, err
	}
	return success[any](nil), nil
}

// --- Route registration ---

func (h *AdminHandler) RegisterRoutes(api huma.API, authCfg middleware.AuthConfig, rlAuth, rlAdmin middleware.RateLimitConfig) {
	authRL := huma.Middlewares{middleware.RateLimit(rlAuth)}
	adminRL := huma.Middlewares{middleware.RequireAdminAuth(authCfg), middleware.RateLimit(rlAdmin)}

	// Auth
	huma.Register(api, huma.Operation{
		OperationID: "admin-login",
		Method:      http.MethodPost,
		Path:        "/admin/v1/auth/login",
		Summary:     "管理员登录",
		Tags:        []string{"Admin Auth"},
		Middlewares: authRL,
	}, h.Login)

	huma.Register(api, huma.Operation{
		OperationID: "admin-refresh-token",
		Method:      http.MethodPost,
		Path:        "/admin/v1/auth/refresh",
		Summary:     "管理员刷新令牌",
		Tags:        []string{"Admin Auth"},
		Middlewares: authRL,
	}, h.Refresh)

	huma.Register(api, huma.Operation{
		OperationID: "admin-logout",
		Method:      http.MethodPost,
		Path:        "/admin/v1/auth/logout",
		Summary:     "管理员登出",
		Tags:        []string{"Admin Auth"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: adminRL,
	}, h.Logout)

	huma.Register(api, huma.Operation{
		OperationID: "admin-get-me",
		Method:      http.MethodGet,
		Path:        "/admin/v1/auth/me",
		Summary:     "获取当前管理员信息",
		Tags:        []string{"Admin Auth"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: adminRL,
	}, h.GetMe)

	// Admin CRUD
	huma.Register(api, huma.Operation{
		OperationID: "list-admins",
		Method:      http.MethodGet,
		Path:        "/admin/v1/admin-users",
		Summary:     "管理员列表",
		Tags:        []string{"Admin User"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: adminRL,
	}, h.ListAdmins)

	huma.Register(api, huma.Operation{
		OperationID:   "create-admin",
		Method:        http.MethodPost,
		Path:          "/admin/v1/admin-users",
		Summary:       "创建管理员",
		Tags:          []string{"Admin User"},
		Security:      []map[string][]string{{"bearerAuth": {}}},
		DefaultStatus: http.StatusCreated,
		Middlewares:   adminRL,
	}, h.CreateAdmin)

	huma.Register(api, huma.Operation{
		OperationID: "get-admin",
		Method:      http.MethodGet,
		Path:        "/admin/v1/admin-users/{id}",
		Summary:     "获取管理员详情",
		Tags:        []string{"Admin User"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: adminRL,
	}, h.GetAdmin)

	huma.Register(api, huma.Operation{
		OperationID: "update-admin",
		Method:      http.MethodPut,
		Path:        "/admin/v1/admin-users/{id}",
		Summary:     "更新管理员",
		Tags:        []string{"Admin User"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: adminRL,
	}, h.UpdateAdmin)

	huma.Register(api, huma.Operation{
		OperationID: "delete-admin",
		Method:      http.MethodDelete,
		Path:        "/admin/v1/admin-users/{id}",
		Summary:     "删除管理员",
		Tags:        []string{"Admin User"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: adminRL,
	}, h.DeleteAdmin)

	huma.Register(api, huma.Operation{
		OperationID: "change-admin-password",
		Method:      http.MethodPut,
		Path:        "/admin/v1/admin-users/{id}/password",
		Summary:     "修改管理员密码",
		Tags:        []string{"Admin User"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: adminRL,
	}, h.ChangeAdminPassword)

	// Customer management
	huma.Register(api, huma.Operation{
		OperationID: "list-customers",
		Method:      http.MethodGet,
		Path:        "/admin/v1/customers",
		Summary:     "客户列表",
		Tags:        []string{"Admin Customer"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: adminRL,
	}, h.ListCustomers)

	huma.Register(api, huma.Operation{
		OperationID: "reset-customer-password",
		Method:      http.MethodPut,
		Path:        "/admin/v1/customers/{id}/password",
		Summary:     "重置客户密码",
		Tags:        []string{"Admin Customer"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: adminRL,
	}, h.ResetCustomerPassword)

	// Video management
	huma.Register(api, huma.Operation{
		OperationID: "admin-list-videos",
		Method:      http.MethodGet,
		Path:        "/admin/v1/videos",
		Summary:     "视频管理列表",
		Tags:        []string{"Admin Video"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: adminRL,
	}, h.AdminListVideos)

	huma.Register(api, huma.Operation{
		OperationID: "admin-get-video",
		Method:      http.MethodGet,
		Path:        "/admin/v1/videos/{id}",
		Summary:     "获取视频详情",
		Tags:        []string{"Admin Video"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: adminRL,
	}, h.GetVideoDetail)

	huma.Register(api, huma.Operation{
		OperationID: "review-video",
		Method:      http.MethodPost,
		Path:        "/admin/v1/videos/{id}/review",
		Summary:     "审核视频",
		Tags:        []string{"Admin Video"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: adminRL,
	}, h.ReviewVideo)

	huma.Register(api, huma.Operation{
		OperationID: "admin-delete-video",
		Method:      http.MethodDelete,
		Path:        "/admin/v1/videos/{id}",
		Summary:     "删除视频",
		Tags:        []string{"Admin Video"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: adminRL,
	}, h.AdminDeleteVideo)
}
