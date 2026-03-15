package transport

import (
	"context"
	"net/http"

	"github.com/danielgtaylor/huma/v2"

	"gabon-go/internal/model"
	"gabon-go/internal/service"
	"gabon-go/internal/transport/middleware"
)

type TaskHandler struct {
	svc *service.TaskService
}

func NewTaskHandler(svc *service.TaskService) *TaskHandler {
	return &TaskHandler{svc: svc}
}

// --- Input types ---

type TaskListInput struct {
	TaskType   int16 `query:"task_type" doc:"任务类型过滤 (1=每日, 2=每周, 3=每月)"`
	TaskStatus int16 `query:"task_status" doc:"任务状态过滤 (1=进行中, 2=已完成, 3=已领取)"`
}

type ClaimRewardInput struct {
	ProgressID int64 `path:"progressId" doc:"进度ID"`
}

// --- Handlers ---

func (h *TaskHandler) ListTasks(ctx context.Context, input *TaskListInput) (*OkResponse[[]service.TaskItem], error) {
	customerID, ok := ctx.Value(middleware.CustomerIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing customer identity")
	}
	var filter service.TaskListFilter
	if input.TaskType != 0 {
		filter.TaskType = &input.TaskType
	}
	if input.TaskStatus != 0 {
		filter.TaskStatus = &input.TaskStatus
	}
	items, err := h.svc.ListTasks(ctx, customerID, filter)
	if err != nil {
		return nil, err
	}
	return success(items), nil
}

func (h *TaskHandler) ClaimReward(ctx context.Context, input *ClaimRewardInput) (*OkResponse[any], error) {
	customerID, ok := ctx.Value(middleware.CustomerIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing customer identity")
	}
	if err := h.svc.ClaimReward(ctx, input.ProgressID, customerID); err != nil {
		return nil, err
	}
	return success[any](nil), nil
}

func (h *TaskHandler) SignIn(ctx context.Context, _ *struct{}) (*OkResponse[*service.SignInResult], error) {
	customerID, ok := ctx.Value(middleware.CustomerIDKey).(int64)
	if !ok {
		return nil, model.NewAppError(model.ErrUnauthorized, "missing customer identity")
	}
	result, err := h.svc.SignIn(ctx, customerID)
	if err != nil {
		return nil, err
	}
	return success(result), nil
}

// --- Route registration ---

func (h *TaskHandler) RegisterRoutes(api huma.API, authCfg middleware.AuthConfig, rlUser middleware.RateLimitConfig) {
	authRL := huma.Middlewares{middleware.RequireCustomerAuth(authCfg), middleware.RateLimit(rlUser)}

	huma.Register(api, huma.Operation{
		OperationID: "list-tasks",
		Method:      http.MethodGet,
		Path:        "/api/v1/tasks",
		Summary:     "获取任务列表",
		Tags:        []string{"Task"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: authRL,
	}, h.ListTasks)

	huma.Register(api, huma.Operation{
		OperationID: "claim-reward",
		Method:      http.MethodPost,
		Path:        "/api/v1/tasks/{progressId}/claim",
		Summary:     "领取任务奖励",
		Tags:        []string{"Task"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: authRL,
	}, h.ClaimReward)

	huma.Register(api, huma.Operation{
		OperationID: "sign-in",
		Method:      http.MethodPost,
		Path:        "/api/v1/activity/sign-in",
		Summary:     "每日签到",
		Tags:        []string{"Activity"},
		Security:    []map[string][]string{{"bearerAuth": {}}},
		Middlewares: authRL,
	}, h.SignIn)
}
