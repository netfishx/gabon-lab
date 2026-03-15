package service

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"

	"gabon-go/internal/model"
	"gabon-go/internal/repository"
)

var shanghaiTZ = func() *time.Location {
	loc, err := time.LoadLocation("Asia/Shanghai")
	if err != nil {
		panic("failed to load Asia/Shanghai timezone: " + err.Error())
	}
	return loc
}()

// PeriodKey returns the period key for a given task type.
// taskType: 1=daily, 2=weekly, 3=monthly.
func PeriodKey(taskType int16, now time.Time) string {
	t := now.In(shanghaiTZ)
	switch taskType {
	case model.TaskTypeDaily:
		return t.Format("2006-01-02")
	case model.TaskTypeWeekly:
		year, week := t.ISOWeek()
		return fmt.Sprintf("%d-W%02d", year, week)
	case model.TaskTypeMonthly:
		return t.Format("2006-01")
	default:
		return t.Format("2006-01-02")
	}
}

type TaskRepo interface {
	ListActiveTaskDefinitions(ctx context.Context) ([]repository.ListActiveTaskDefinitionsRow, error)
	UpsertTaskProgress(ctx context.Context, arg repository.UpsertTaskProgressParams) (repository.TaskProgress, error)
	GetTaskProgressByCustomerAndPeriod(ctx context.Context, arg repository.GetTaskProgressByCustomerAndPeriodParams) ([]repository.GetTaskProgressByCustomerAndPeriodRow, error)
	GetTaskProgressForUpdate(ctx context.Context, arg repository.GetTaskProgressForUpdateParams) (repository.GetTaskProgressForUpdateRow, error)
	ClaimTaskProgress(ctx context.Context, id int64) error
	AddCustomerDiamonds(ctx context.Context, arg repository.AddCustomerDiamondsParams) error
	IncrTaskProgressCount(ctx context.Context, arg repository.IncrTaskProgressCountParams) error
	GetWatchTaskIDs(ctx context.Context) ([]int64, error)
	HasSignedInToday(ctx context.Context, arg repository.HasSignedInTodayParams) (bool, error)
	RecordSignIn(ctx context.Context, arg repository.RecordSignInParams) (repository.CustomerSignInRecord, error)
}

// TxBeginner abstracts pgxpool.Pool for starting transactions.
type TxBeginner interface {
	Begin(ctx context.Context) (pgx.Tx, error)
}

// NewTaskRepoFromTx creates a TaskRepo from a transaction.
type NewTaskRepoFunc func(tx pgx.Tx) TaskRepo

type TaskService struct {
	repo        TaskRepo
	txBeginner  TxBeginner
	newRepoFunc NewTaskRepoFunc
}

func NewTaskService(repo TaskRepo, txBeginner TxBeginner, newRepoFunc NewTaskRepoFunc) *TaskService {
	return &TaskService{
		repo:        repo,
		txBeginner:  txBeginner,
		newRepoFunc: newRepoFunc,
	}
}

type TaskItem struct {
	TaskID         int64  `json:"task_id"`
	TaskCode       string `json:"task_code"`
	TaskName       string `json:"task_name"`
	Description    string `json:"description,omitempty"`
	TaskType       int16  `json:"task_type"`
	TaskCategory   int16  `json:"task_category"`
	TargetCount    int32  `json:"target_count"`
	RewardDiamonds int32  `json:"reward_diamonds"`
	IconURL        string `json:"icon_url,omitempty"`
	VipOnly        bool   `json:"vip_only"`
	ProgressID     int64  `json:"progress_id"`
	CurrentCount   int32  `json:"current_count"`
	TaskStatus     int16  `json:"task_status"`
}

type TaskListFilter struct {
	TaskType   *int16
	TaskStatus *int16
}

func (s *TaskService) ListTasks(ctx context.Context, customerID int64, filter TaskListFilter) ([]TaskItem, error) {
	now := time.Now()

	defs, err := s.repo.ListActiveTaskDefinitions(ctx)
	if err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to list task definitions", err)
	}

	// Group definitions by period key and auto-assign progress
	periodKeys := make(map[string]bool)
	defPeriod := make(map[int64]string, len(defs))
	for i := range defs {
		pk := PeriodKey(defs[i].TaskType, now)
		periodKeys[pk] = true
		defPeriod[defs[i].ID] = pk

		// Upsert progress (ignore conflict = already exists)
		_, upsertErr := s.repo.UpsertTaskProgress(ctx, repository.UpsertTaskProgressParams{
			CustomerID:     customerID,
			TaskID:         defs[i].ID,
			TargetCount:    defs[i].TargetCount,
			PeriodKey:      pk,
			RewardDiamonds: defs[i].RewardDiamonds,
		})
		if upsertErr != nil && !errors.Is(upsertErr, pgx.ErrNoRows) {
			return nil, model.WrapError(model.ErrInternal, "failed to upsert task progress", upsertErr)
		}
	}

	// Fetch all progress rows for this customer across relevant period keys
	progressMap := make(map[int64]*repository.GetTaskProgressByCustomerAndPeriodRow)
	for pk := range periodKeys {
		rows, fetchErr := s.repo.GetTaskProgressByCustomerAndPeriod(ctx, repository.GetTaskProgressByCustomerAndPeriodParams{
			CustomerID: customerID,
			PeriodKey:  pk,
		})
		if fetchErr != nil {
			return nil, model.WrapError(model.ErrInternal, "failed to get task progress", fetchErr)
		}
		for i := range rows {
			progressMap[rows[i].TaskID] = &rows[i]
		}
	}

	// Merge definitions with progress
	items := make([]TaskItem, 0, len(defs))
	for i := range defs {
		d := &defs[i]
		item := TaskItem{
			TaskID:         d.ID,
			TaskCode:       d.TaskCode,
			TaskName:       d.TaskName,
			Description:    d.Description.String,
			TaskType:       d.TaskType,
			TaskCategory:   d.TaskCategory,
			TargetCount:    d.TargetCount,
			RewardDiamonds: d.RewardDiamonds,
			IconURL:        d.IconUrl.String,
			VipOnly:        d.VipOnly,
			TaskStatus:     model.TaskStatusInProgress,
		}
		if p, ok := progressMap[d.ID]; ok {
			item.ProgressID = p.ID
			item.CurrentCount = p.CurrentCount
			item.TaskStatus = p.TaskStatus
		}

		if filter.TaskType != nil && item.TaskType != *filter.TaskType {
			continue
		}
		if filter.TaskStatus != nil && item.TaskStatus != *filter.TaskStatus {
			continue
		}

		items = append(items, item)
	}

	return items, nil
}

func (s *TaskService) ClaimReward(ctx context.Context, progressID, customerID int64) error {
	tx, err := s.txBeginner.Begin(ctx)
	if err != nil {
		return model.WrapError(model.ErrInternal, "failed to begin transaction", err)
	}
	defer func() { _ = tx.Rollback(ctx) }()

	txRepo := s.newRepoFunc(tx)

	// Step 1: Row lock + ownership check
	progress, err := txRepo.GetTaskProgressForUpdate(ctx, repository.GetTaskProgressForUpdateParams{
		ID:         progressID,
		CustomerID: customerID,
	})
	if err != nil {
		return model.NewAppError(model.ErrTaskNotClaimable, "task progress not found")
	}

	// Step 2: Status check
	if progress.TaskStatus != model.TaskStatusCompleted {
		return model.NewAppError(model.ErrTaskNotClaimable, "task is not completed")
	}

	// Step 3: Add diamonds
	if err := txRepo.AddCustomerDiamonds(ctx, repository.AddCustomerDiamondsParams{
		Amount:     int64(progress.RewardDiamonds),
		CustomerID: customerID,
	}); err != nil {
		return model.WrapError(model.ErrInternal, "failed to add diamonds", err)
	}

	// Step 4: Mark claimed
	if err := txRepo.ClaimTaskProgress(ctx, progressID); err != nil {
		return model.WrapError(model.ErrInternal, "failed to claim task", err)
	}

	return tx.Commit(ctx)
}

const signInBaseReward int32 = 1

// SignInResult is the response data for a successful sign-in.
type SignInResult struct {
	Diamonds int32 `json:"diamonds"`
}

// SignIn performs a daily sign-in (check-in) for the customer.
// It awards base diamonds and updates the customer's balance atomically.
func (s *TaskService) SignIn(ctx context.Context, customerID int64) (*SignInResult, error) {
	now := time.Now()
	periodKey := PeriodKey(model.TaskTypeDaily, now) // daily

	// Pre-check outside transaction (fast path)
	signed, err := s.repo.HasSignedInToday(ctx, repository.HasSignedInTodayParams{
		CustomerID: customerID,
		PeriodKey:  periodKey,
	})
	if err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to check sign-in status", err)
	}
	if signed {
		return nil, model.NewAppError(model.ErrAlreadySignedIn, "already signed in today")
	}

	// Transaction: record + award diamonds
	tx, err := s.txBeginner.Begin(ctx)
	if err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to begin transaction", err)
	}
	defer func() { _ = tx.Rollback(ctx) }()

	txRepo := s.newRepoFunc(tx)

	// Double-check inside transaction (handles race)
	signed, err = txRepo.HasSignedInToday(ctx, repository.HasSignedInTodayParams{
		CustomerID: customerID,
		PeriodKey:  periodKey,
	})
	if err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to check sign-in status", err)
	}
	if signed {
		return nil, model.NewAppError(model.ErrAlreadySignedIn, "already signed in today")
	}

	reward := signInBaseReward

	if _, err := txRepo.RecordSignIn(ctx, repository.RecordSignInParams{
		CustomerID:     customerID,
		PeriodKey:      periodKey,
		RewardDiamonds: reward,
	}); err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to record sign-in", err)
	}

	if err := txRepo.AddCustomerDiamonds(ctx, repository.AddCustomerDiamondsParams{
		Amount:     int64(reward),
		CustomerID: customerID,
	}); err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to add diamonds", err)
	}

	if err := tx.Commit(ctx); err != nil {
		return nil, model.WrapError(model.ErrInternal, "failed to commit sign-in", err)
	}

	return &SignInResult{Diamonds: reward}, nil
}

// OnValidPlay increments watch task progress for a customer.
func (s *TaskService) OnValidPlay(ctx context.Context, customerID int64) {
	now := time.Now()

	taskIDs, err := s.repo.GetWatchTaskIDs(ctx)
	if err != nil {
		return
	}

	for _, taskID := range taskIDs {
		// For watch tasks, we only handle daily (type=1) period
		pk := PeriodKey(model.TaskTypeDaily, now)
		_ = s.repo.IncrTaskProgressCount(ctx, repository.IncrTaskProgressCountParams{
			CustomerID: customerID,
			TaskID:     taskID,
			PeriodKey:  pk,
		})
	}
}
