package service

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgtype"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"gabon-go/internal/repository"
)

// --- Mock TaskRepo ---

type mockTaskRepo struct {
	mu          sync.Mutex
	definitions []repository.ListActiveTaskDefinitionsRow
	progress    map[int64]*repository.TaskProgress // by progress ID
	signIns     map[string]bool                    // "customerID:periodKey" -> signed
	nextID      int64
	diamonds    map[int64]int64 // customerID -> balance
}

func newMockTaskRepo() *mockTaskRepo {
	return &mockTaskRepo{
		definitions: []repository.ListActiveTaskDefinitionsRow{
			{
				ID: 1, TaskCode: "DAILY_WATCH_3", TaskName: "Watch 3 Videos",
				Description: pgtype.Text{String: "Watch 3 videos today", Valid: true},
				TaskType:    1, TaskCategory: 1, TargetCount: 3,
				RewardDiamonds: 10, DisplayOrder: 1,
			},
			{
				ID: 2, TaskCode: "WEEKLY_WATCH_20", TaskName: "Watch 20 Videos",
				Description: pgtype.Text{String: "Watch 20 videos this week", Valid: true},
				TaskType:    2, TaskCategory: 1, TargetCount: 20,
				RewardDiamonds: 50, DisplayOrder: 2,
			},
		},
		progress: make(map[int64]*repository.TaskProgress),
		signIns:  make(map[string]bool),
		nextID:   1,
		diamonds: make(map[int64]int64),
	}
}

func (m *mockTaskRepo) ListActiveTaskDefinitions(_ context.Context) ([]repository.ListActiveTaskDefinitionsRow, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.definitions, nil
}

func (m *mockTaskRepo) UpsertTaskProgress(_ context.Context, arg repository.UpsertTaskProgressParams) (repository.TaskProgress, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	// Check for conflict
	for _, p := range m.progress {
		if p.CustomerID == arg.CustomerID && p.TaskID == arg.TaskID && p.PeriodKey == arg.PeriodKey {
			return repository.TaskProgress{}, pgx.ErrNoRows // ON CONFLICT DO NOTHING
		}
	}
	tp := &repository.TaskProgress{
		ID:             m.nextID,
		CustomerID:     arg.CustomerID,
		TaskID:         arg.TaskID,
		TargetCount:    arg.TargetCount,
		PeriodKey:      arg.PeriodKey,
		TaskStatus:     1,
		RewardDiamonds: arg.RewardDiamonds,
		CreatedAt:      time.Now(),
		UpdatedAt:      time.Now(),
	}
	m.progress[m.nextID] = tp
	m.nextID++
	return *tp, nil
}

func (m *mockTaskRepo) GetTaskProgressByCustomerAndPeriod(_ context.Context, arg repository.GetTaskProgressByCustomerAndPeriodParams) ([]repository.GetTaskProgressByCustomerAndPeriodRow, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var rows []repository.GetTaskProgressByCustomerAndPeriodRow
	for _, p := range m.progress {
		if p.CustomerID == arg.CustomerID && p.PeriodKey == arg.PeriodKey {
			rows = append(rows, repository.GetTaskProgressByCustomerAndPeriodRow{
				ID:             p.ID,
				CustomerID:     p.CustomerID,
				TaskID:         p.TaskID,
				CurrentCount:   p.CurrentCount,
				TargetCount:    p.TargetCount,
				PeriodKey:      p.PeriodKey,
				TaskStatus:     p.TaskStatus,
				RewardDiamonds: p.RewardDiamonds,
				CompletedAt:    p.CompletedAt,
				ClaimedAt:      p.ClaimedAt,
				CreatedAt:      p.CreatedAt,
			})
		}
	}
	return rows, nil
}

func (m *mockTaskRepo) GetTaskProgressForUpdate(_ context.Context, arg repository.GetTaskProgressForUpdateParams) (repository.GetTaskProgressForUpdateRow, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	p, ok := m.progress[arg.ID]
	if !ok || p.CustomerID != arg.CustomerID {
		return repository.GetTaskProgressForUpdateRow{}, errors.New("not found")
	}
	return repository.GetTaskProgressForUpdateRow{
		ID:             p.ID,
		CustomerID:     p.CustomerID,
		TaskID:         p.TaskID,
		CurrentCount:   p.CurrentCount,
		TargetCount:    p.TargetCount,
		TaskStatus:     p.TaskStatus,
		RewardDiamonds: p.RewardDiamonds,
	}, nil
}

func (m *mockTaskRepo) ClaimTaskProgress(_ context.Context, id int64) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	p, ok := m.progress[id]
	if !ok {
		return errors.New("not found")
	}
	if p.TaskStatus != 2 {
		return errors.New("not claimable") // simulate FOR UPDATE atomicity
	}
	p.TaskStatus = 3
	now := time.Now()
	p.ClaimedAt = &now
	return nil
}

func (m *mockTaskRepo) AddCustomerDiamonds(_ context.Context, arg repository.AddCustomerDiamondsParams) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.diamonds[arg.CustomerID] += arg.Amount
	return nil
}

func (m *mockTaskRepo) IncrTaskProgressCount(_ context.Context, arg repository.IncrTaskProgressCountParams) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	for _, p := range m.progress {
		if p.CustomerID == arg.CustomerID && p.TaskID == arg.TaskID && p.PeriodKey == arg.PeriodKey && p.TaskStatus == 1 {
			p.CurrentCount++
			if p.CurrentCount >= p.TargetCount {
				p.TaskStatus = 2
				now := time.Now()
				p.CompletedAt = &now
			}
			return nil
		}
	}
	return nil
}

func (m *mockTaskRepo) GetWatchTaskIDs(_ context.Context) ([]int64, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	var ids []int64
	for _, d := range m.definitions {
		if d.TaskCategory == 1 {
			ids = append(ids, d.ID)
		}
	}
	return ids, nil
}

func (m *mockTaskRepo) HasSignedInToday(_ context.Context, arg repository.HasSignedInTodayParams) (bool, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	key := fmt.Sprintf("%d:%s", arg.CustomerID, arg.PeriodKey)
	return m.signIns[key], nil
}

func (m *mockTaskRepo) RecordSignIn(_ context.Context, arg repository.RecordSignInParams) (repository.CustomerSignInRecord, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	key := fmt.Sprintf("%d:%s", arg.CustomerID, arg.PeriodKey)
	if m.signIns[key] {
		return repository.CustomerSignInRecord{}, errors.New("unique constraint violation")
	}
	m.signIns[key] = true
	m.nextID++
	return repository.CustomerSignInRecord{
		ID:             m.nextID,
		CustomerID:     arg.CustomerID,
		PeriodKey:      arg.PeriodKey,
		RewardDiamonds: arg.RewardDiamonds,
		CreatedAt:      time.Now(),
	}, nil
}

// --- Mock TxBeginner that uses the same repo (no real tx) ---

type mockTxBeginner struct {
	repo *mockTaskRepo
	txMu sync.Mutex // serializes transactions like FOR UPDATE
}

func (m *mockTxBeginner) Begin(_ context.Context) (pgx.Tx, error) {
	m.txMu.Lock()
	return &mockTx{repo: m.repo, beginner: m}, nil
}

type mockTx struct {
	pgx.Tx
	repo     *mockTaskRepo
	beginner *mockTxBeginner
	done     bool
}

func (m *mockTx) Commit(_ context.Context) error {
	if !m.done {
		m.done = true
		m.beginner.txMu.Unlock()
	}
	return nil
}

func (m *mockTx) Rollback(_ context.Context) error {
	if !m.done {
		m.done = true
		m.beginner.txMu.Unlock()
	}
	return nil
}

// --- Tests ---

func TestPeriodKey_Daily(t *testing.T) {
	// 2026-02-19 15:00 Asia/Shanghai
	loc, _ := time.LoadLocation("Asia/Shanghai")
	ts := time.Date(2026, 2, 19, 15, 0, 0, 0, loc)
	assert.Equal(t, "2026-02-19", PeriodKey(1, ts))
}

func TestPeriodKey_Weekly(t *testing.T) {
	loc, _ := time.LoadLocation("Asia/Shanghai")
	ts := time.Date(2026, 2, 19, 15, 0, 0, 0, loc)
	assert.Equal(t, "2026-W08", PeriodKey(2, ts))
}

func TestPeriodKey_Monthly(t *testing.T) {
	loc, _ := time.LoadLocation("Asia/Shanghai")
	ts := time.Date(2026, 2, 19, 15, 0, 0, 0, loc)
	assert.Equal(t, "2026-02", PeriodKey(3, ts))
}

func TestPeriodKey_DayBoundary(t *testing.T) {
	// UTC 16:30 = Shanghai 00:30 next day
	utc := time.Date(2026, 2, 19, 16, 30, 0, 0, time.UTC)
	assert.Equal(t, "2026-02-20", PeriodKey(1, utc))
}

func newTestTaskService(repo *mockTaskRepo) *TaskService {
	txBeginner := &mockTxBeginner{repo: repo}
	return NewTaskService(repo, txBeginner, func(_ pgx.Tx) TaskRepo {
		return repo // reuse same mock (no real tx isolation in unit test)
	})
}

func TestTask_ListTasks(t *testing.T) {
	repo := newMockTaskRepo()
	svc := newTestTaskService(repo)
	ctx := context.Background()

	items, err := svc.ListTasks(ctx, 100, TaskListFilter{})
	require.NoError(t, err)
	assert.Len(t, items, 2)
	assert.Equal(t, "DAILY_WATCH_3", items[0].TaskCode)
	assert.Equal(t, int32(0), items[0].CurrentCount)
	assert.Equal(t, int16(1), items[0].TaskStatus)
	assert.NotZero(t, items[0].ProgressID)
}

func TestTask_ListTasks_Idempotent(t *testing.T) {
	repo := newMockTaskRepo()
	svc := newTestTaskService(repo)
	ctx := context.Background()

	_, err := svc.ListTasks(ctx, 100, TaskListFilter{})
	require.NoError(t, err)

	// Second call should not create duplicate progress
	items, err := svc.ListTasks(ctx, 100, TaskListFilter{})
	require.NoError(t, err)
	assert.Len(t, items, 2)
}

func TestTask_ClaimReward(t *testing.T) {
	repo := newMockTaskRepo()
	svc := newTestTaskService(repo)
	ctx := context.Background()

	// Setup: create progress and complete it
	items, err := svc.ListTasks(ctx, 100, TaskListFilter{})
	require.NoError(t, err)

	progressID := items[0].ProgressID
	// Manually complete the task
	repo.mu.Lock()
	repo.progress[progressID].TaskStatus = 2
	repo.mu.Unlock()

	err = svc.ClaimReward(ctx, progressID, 100)
	require.NoError(t, err)

	// Check diamonds added
	assert.Equal(t, int64(10), repo.diamonds[100])

	// Check status = 3 (claimed)
	repo.mu.Lock()
	assert.Equal(t, int16(3), repo.progress[progressID].TaskStatus)
	repo.mu.Unlock()
}

func TestTask_ClaimReward_NotCompleted(t *testing.T) {
	repo := newMockTaskRepo()
	svc := newTestTaskService(repo)
	ctx := context.Background()

	items, err := svc.ListTasks(ctx, 100, TaskListFilter{})
	require.NoError(t, err)

	// Task is still in progress (status=1), should fail
	err = svc.ClaimReward(ctx, items[0].ProgressID, 100)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "not completed")
}

func TestTask_ClaimReward_DoubleClaim(t *testing.T) {
	repo := newMockTaskRepo()
	svc := newTestTaskService(repo)
	ctx := context.Background()

	items, err := svc.ListTasks(ctx, 100, TaskListFilter{})
	require.NoError(t, err)

	progressID := items[0].ProgressID
	repo.mu.Lock()
	repo.progress[progressID].TaskStatus = 2
	repo.mu.Unlock()

	err = svc.ClaimReward(ctx, progressID, 100)
	require.NoError(t, err)

	// Second claim should fail (status is now 3)
	err = svc.ClaimReward(ctx, progressID, 100)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "not completed")
}

func TestTask_ClaimReward_WrongCustomer(t *testing.T) {
	repo := newMockTaskRepo()
	svc := newTestTaskService(repo)
	ctx := context.Background()

	items, err := svc.ListTasks(ctx, 100, TaskListFilter{})
	require.NoError(t, err)

	progressID := items[0].ProgressID
	repo.mu.Lock()
	repo.progress[progressID].TaskStatus = 2
	repo.mu.Unlock()

	// Different customer trying to claim
	err = svc.ClaimReward(ctx, progressID, 999)
	assert.Error(t, err)
}

func TestTask_OnValidPlay(t *testing.T) {
	repo := newMockTaskRepo()
	svc := newTestTaskService(repo)
	ctx := context.Background()

	// First create progress entries
	_, err := svc.ListTasks(ctx, 100, TaskListFilter{})
	require.NoError(t, err)

	// Simulate 3 valid plays
	svc.OnValidPlay(ctx, 100)
	svc.OnValidPlay(ctx, 100)
	svc.OnValidPlay(ctx, 100)

	// Check daily task (target=3) should be completed
	items, err := svc.ListTasks(ctx, 100, TaskListFilter{})
	require.NoError(t, err)

	var dailyTask *TaskItem
	for i := range items {
		if items[i].TaskCode == "DAILY_WATCH_3" {
			dailyTask = &items[i]
			break
		}
	}
	require.NotNil(t, dailyTask)
	assert.Equal(t, int32(3), dailyTask.CurrentCount)
	assert.Equal(t, int16(2), dailyTask.TaskStatus) // completed
}

func TestTask_ConcurrentClaim(t *testing.T) {
	repo := newMockTaskRepo()
	svc := newTestTaskService(repo)
	ctx := context.Background()

	items, err := svc.ListTasks(ctx, 100, TaskListFilter{})
	require.NoError(t, err)

	progressID := items[0].ProgressID
	repo.mu.Lock()
	repo.progress[progressID].TaskStatus = 2
	repo.mu.Unlock()

	// Race: 10 goroutines try to claim simultaneously
	var wg sync.WaitGroup
	var successCount atomic.Int32
	for range 10 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if claimErr := svc.ClaimReward(ctx, progressID, 100); claimErr == nil {
				successCount.Add(1)
			}
		}()
	}
	wg.Wait()

	// Only 1 should succeed (mock uses mutex, real DB uses FOR UPDATE)
	assert.Equal(t, int32(1), successCount.Load())
	assert.Equal(t, int64(10), repo.diamonds[100]) // 10 diamonds added once
}

func TestTask_SignIn(t *testing.T) {
	repo := newMockTaskRepo()
	svc := newTestTaskService(repo)
	ctx := context.Background()

	result, err := svc.SignIn(ctx, 100)
	require.NoError(t, err)
	assert.Equal(t, int32(1), result.Diamonds)
	assert.Equal(t, int64(1), repo.diamonds[100])
}

func TestTask_SignIn_AlreadySigned(t *testing.T) {
	repo := newMockTaskRepo()
	svc := newTestTaskService(repo)
	ctx := context.Background()

	// First sign-in succeeds
	_, err := svc.SignIn(ctx, 100)
	require.NoError(t, err)

	// Second sign-in should fail
	_, err = svc.SignIn(ctx, 100)
	assert.Error(t, err)
	assert.Contains(t, err.Error(), "already signed in")
}

func TestTask_SignIn_ConcurrentAttempts(t *testing.T) {
	repo := newMockTaskRepo()
	svc := newTestTaskService(repo)
	ctx := context.Background()

	var wg sync.WaitGroup
	var successCount atomic.Int32
	for range 10 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if _, signErr := svc.SignIn(ctx, 100); signErr == nil {
				successCount.Add(1)
			}
		}()
	}
	wg.Wait()

	// Only 1 should succeed
	assert.Equal(t, int32(1), successCount.Load())
	assert.Equal(t, int64(1), repo.diamonds[100])
}
