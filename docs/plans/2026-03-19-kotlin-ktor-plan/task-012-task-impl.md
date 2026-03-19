# Task 012: Task System and Sign-In Implementation

**type**: impl
**depends-on**: ["012-task-test"]

## Description

Implement the task system and daily sign-in covering BDD Features 6 and 7. This is the Green phase -- make all task and sign-in tests pass.

### CRITICAL: Asia/Shanghai Timezone for Period Keys

Period key generation MUST use `Asia/Shanghai` (UTC+8) timezone. This affects all task operations and sign-in.

```
daily (type=1):   "2026-03-19"    // LocalDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
weekly (type=2):  "2026-W12"      // year + "-W" + weekOfYear (ISO week, zero-padded 2 digits)
monthly (type=3): "2026-03"       // year + "-" + month (zero-padded 2 digits)
```

Use `java.time.ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))` as the basis. The period key function must accept a Clock or Instant parameter for testability (so tests can inject fixed times for the Scenario Outline examples).

### TaskRepo

- `findActiveTaskDefinitions(taskType?)`: SELECT FROM tasks WHERE is_active=true, optional type filter
- `upsertProgress(customerId, taskId, periodKey, targetCount, rewardDiamonds)`: INSERT INTO task_progress (customer_id, task_id, period_key, target_count, reward_diamonds) ON CONFLICT (customer_id, task_id, period_key) DO NOTHING. Return the progress record (existing or new).
- `findProgressById(progressId)`: SELECT FROM task_progress WHERE id=?
- `findProgressByCustomerAndTask(customerId, taskId, periodKey)`: SELECT for specific progress lookup
- `listProgressWithTasks(customerId, periodKey, taskType?, taskStatus?)`: JOIN tasks and task_progress for the list endpoint. Returns combined task+progress data.
- `incrementProgress(progressId)`: UPDATE task_progress SET current_count = current_count + 1 WHERE id = ? AND task_status = 1. If current_count reaches target_count, also SET task_status = 2, completed_at = NOW(). Use a single UPDATE with CASE expression.
- `claimReward(progressId, customerId)`: FOR UPDATE row lock pattern within a transaction:
  1. SELECT FROM task_progress WHERE id=? AND customer_id=? FOR UPDATE
  2. Verify task_status = 2 (completed), else throw TASK_NOT_CLAIMABLE
  3. UPDATE task_progress SET task_status=3, claimed_at=NOW()
  4. UPDATE customers SET diamond_balance = diamond_balance + reward WHERE id=?

### CRITICAL: Claim Uses FOR UPDATE Row Lock

The claim operation MUST use `FOR UPDATE` to prevent concurrent claims from awarding diamonds multiple times:

```sql
-- Step 1: Lock the row
SELECT * FROM task_progress WHERE id = ? AND customer_id = ? FOR UPDATE

-- Step 2: Verify status (in Kotlin after fetching)
-- If status != 2, throw TASK_NOT_CLAIMABLE

-- Step 3: Update progress status
UPDATE task_progress SET task_status = 3, claimed_at = NOW() WHERE id = ?

-- Step 4: Credit diamonds
UPDATE customers SET diamond_balance = diamond_balance + ? WHERE id = ?
```

All 4 steps run within a single `newSuspendedTransaction` block. The FOR UPDATE lock ensures only one concurrent request can proceed -- others block and then see status=3 (already claimed).

### SignInRepo

- `signIn(customerId, periodKey, diamonds)`: Within a transaction:
  1. INSERT INTO sign_in_records (customer_id, period_key, diamonds) -- unique constraint on (customer_id, period_key)
  2. If unique constraint violated, throw ALREADY_SIGNED_IN (409)
  3. UPDATE customers SET diamond_balance = diamond_balance + diamonds WHERE id = ?
  4. Return the sign-in record

### TaskService

- `listTasks(customerId, taskType?, taskStatus?)`: Get current period keys (daily/weekly/monthly using Asia/Shanghai). Fetch active task definitions. Upsert progress for each task+period combination. Return joined task+progress list with filters applied.
- `incrementWatchProgress(customerId)`: Find the daily watch task progress for today's period. If exists and in_progress (status=1), increment. If current_count reaches target_count, auto-complete.
- `claimReward(customerId, progressId)`: Delegate to repo's transactional claim.
- `signIn(customerId)`: Generate today's period key (daily, Asia/Shanghai). Delegate to SignInRepo. Return diamonds awarded.
- `generatePeriodKey(taskType, instant)`: Pure function. Convert instant to Asia/Shanghai ZonedDateTime. Return formatted period key string.

### Task Routes (route/TaskRoutes.kt)

Authenticated:
- `GET /api/v1/tasks` -- list tasks with auto-created progress (query params: task_type, task_status)
- `POST /api/v1/tasks/{progressId}/claim` -- claim reward for completed task
- `POST /api/v1/activity/sign-in` -- daily sign-in

Register task routes in `plugin/Routing.kt`.

### Integration with Video Valid Play

The `POST /api/v1/videos/{id}/play-valid` endpoint (from Task 008) should call `TaskService.incrementWatchProgress(customerId)` after recording the valid play. This may require modifying VideoRoutes or VideoService to inject TaskService as a dependency.

## Files

- `kotlin/src/main/kotlin/lab/gabon/repository/TaskRepo.kt` -- Task and progress data access
- `kotlin/src/main/kotlin/lab/gabon/repository/SignInRepo.kt` -- Sign-in record data access
- `kotlin/src/main/kotlin/lab/gabon/service/TaskService.kt` -- Task and sign-in business logic
- `kotlin/src/main/kotlin/lab/gabon/route/TaskRoutes.kt` -- Task and sign-in HTTP route handlers
- `kotlin/src/main/kotlin/lab/gabon/plugin/Routing.kt` -- Modified to register task routes

## Verification

```bash
cd kotlin && ./gradlew test --tests '*Task*'
```

- All 17+ task and sign-in tests PASS (Green phase)
- Period key generation: all 5 timezone examples produce correct keys
- Task list auto-upserts progress for current period
- Filter by task_type and task_status works
- Valid play increments watch task progress
- Auto-complete when reaching target_count
- Claim: diamond_balance increases, status changes to 3
- Concurrent claim: exactly 1 of 10 succeeds, diamond_balance is exactly the reward (FOR UPDATE lock works)
- Sign-in: diamond_balance +1, sign_in_record created
- Duplicate sign-in returns 409
- Concurrent sign-in: exactly 1 of 5 succeeds, diamond_balance +1 only
