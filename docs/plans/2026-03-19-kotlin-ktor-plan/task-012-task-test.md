# Task 012: Task System and Sign-In Tests

**type**: test
**depends-on**: ["003", "007-auth-impl"]

## BDD Scenarios

```gherkin
Feature: Task System
  Tasks have types: daily(1), weekly(2), monthly(3).
  Period keys use Asia/Shanghai timezone:
    daily="2026-03-19", weekly="2026-W12", monthly="2026-03".
  Task progress statuses: in_progress(1), completed(2), claimed(3), expired(4).
  Claiming uses FOR UPDATE row lock + transactional diamond credit.

  # --- List Tasks ---

  Scenario: List all active tasks with auto-created progress
    Given I am logged in as customer "alice"
    And there are 3 active task definitions (1 daily, 1 weekly, 1 monthly)
    When I GET /api/v1/tasks
    Then the response status is 200
    And the response contains 3 task items
    And each item has task_id, task_code, task_name, task_type, target_count,
        reward_diamonds, progress_id, current_count, task_status
    And progress records are auto-created (upserted) for the current period

  Scenario: Filter tasks by type
    Given I am logged in as customer "alice"
    When I GET /api/v1/tasks?task_type=1
    Then only daily tasks are returned

  Scenario: Filter tasks by status
    Given I am logged in as customer "alice"
    And alice has 1 completed task and 2 in-progress tasks
    When I GET /api/v1/tasks?task_status=2
    Then only the 1 completed task is returned

  # --- Period Key Generation ---

  Scenario Outline: Period key for task types
    Given the current time in Asia/Shanghai is <datetime>
    When the system generates a period key for task type <type>
    Then the period key is "<expected_key>"

    Examples:
      | datetime            | type | expected_key |
      | 2026-03-19 08:00:00 | 1    | 2026-03-19   |
      | 2026-03-19 08:00:00 | 2    | 2026-W12     |
      | 2026-03-19 08:00:00 | 3    | 2026-03      |
      | 2026-01-01 00:30:00 | 2    | 2026-W01     |
      | 2025-12-31 23:59:59 | 1    | 2026-01-01   |

  Note: The last example assumes UTC 23:59:59 Dec 31 is already Jan 1 in Asia/Shanghai (+8)

  # --- Task Progress via Valid Play ---

  Scenario: Watching videos increments watch task progress
    Given I am logged in as customer "alice"
    And there is a daily watch task (category=1) with target_count=3
    And alice's current_count is 1, task_status=1 (in_progress)
    When I record a valid play on any video
    Then alice's watch task current_count becomes 2

  Scenario: Reaching target count auto-completes task
    Given alice's daily watch task has target_count=3, current_count=2
    When alice records 1 more valid play
    Then current_count becomes 3
    And task_status changes to 2 (completed)
    And completed_at is set

  # --- Claim Reward ---

  Scenario: Claim reward for completed task
    Given I am logged in as customer "alice"
    And alice has a completed task progress (id=10, status=2, reward=50)
    And alice's diamond_balance is 100
    When I POST /api/v1/tasks/10/claim
    Then the response status is 200
    And alice's diamond_balance becomes 150
    And task progress status changes to 3 (claimed)
    And claimed_at is set

  Scenario: Claim reward for in-progress task
    Given I am logged in as customer "alice"
    And alice has an in-progress task (status=1)
    When I POST /api/v1/tasks/{progressId}/claim
    Then the response status is 400
    And the error code is "TASK_NOT_CLAIMABLE"
    And the error message is "task is not completed"

  Scenario: Claim already-claimed reward
    Given I am logged in as customer "alice"
    And alice has a claimed task (status=3)
    When I POST /api/v1/tasks/{progressId}/claim
    Then the response status is 400
    And the error code is "TASK_NOT_CLAIMABLE"

  Scenario: Claim reward for another user's task
    Given I am logged in as customer "alice"
    And progress 10 belongs to customer "bob"
    When I POST /api/v1/tasks/10/claim
    Then the response status is 400
    And the error code is "TASK_NOT_CLAIMABLE"
    And the error message is "task progress not found"

  Scenario: Concurrent claim — only one succeeds
    Given customer "alice" has a completed task (progress_id=10, reward=100)
    And alice's diamond_balance is 0
    When 10 concurrent requests POST /api/v1/tasks/10/claim with alice's token
    Then exactly 1 request returns status 200
    And the remaining 9 return status 400 with "TASK_NOT_CLAIMABLE"
    And alice's diamond_balance is exactly 100 (not 1000)
```

```gherkin
Feature: Daily Sign-In
  One sign-in per customer per day. Uses (customer_id, period_key)
  unique constraint. Awards base diamonds (1) within a transaction.
  Period key is daily format in Asia/Shanghai timezone.

  Scenario: Successful daily sign-in
    Given I am logged in as customer "alice"
    And alice has not signed in today (no record for today's period_key)
    And alice's diamond_balance is 50
    When I POST /api/v1/activity/sign-in
    Then the response status is 200
    And the response body contains diamonds=1
    And alice's diamond_balance becomes 51
    And a sign_in_record exists for (alice, today's period_key)

  Scenario: Duplicate sign-in on same day
    Given I am logged in as customer "alice"
    And alice has already signed in today
    When I POST /api/v1/activity/sign-in
    Then the response status is 409
    And the error code is "ALREADY_SIGNED_IN"
    And alice's diamond_balance is unchanged

  Scenario: Sign-in on next day (period key changes)
    Given alice signed in on "2026-03-18" (yesterday)
    And today is "2026-03-19" in Asia/Shanghai
    When alice POSTs /api/v1/activity/sign-in
    Then the response status is 200
    Because the period_key "2026-03-19" is different from "2026-03-18"

  Scenario: Concurrent sign-in requests — only one succeeds
    Given customer "alice" has not signed in today
    When 5 concurrent requests POST /api/v1/activity/sign-in with alice's token
    Then exactly 1 request returns status 200
    And the remaining 4 return status 409 with "ALREADY_SIGNED_IN"
    And alice's diamond_balance increases by exactly 1

  Scenario: Sign-in without auth
    When I POST /api/v1/activity/sign-in without authorization
    Then the response status is 401
```

## Description

Write tests covering all BDD scenarios from Feature 6 (Task System) and Feature 7 (Daily Sign-In). This is the Red phase -- all tests must compile but FAIL because no task/sign-in implementation exists yet.

Test structure:

- **TaskRoutesTest.kt**: Integration tests for task list, filter, claim, and sign-in endpoints. Set up task definitions in DB (daily watch task, weekly task, monthly task). Create task progress records at various statuses for filter and claim tests.

- **TaskServiceTest.kt**: Unit tests for period key generation and task progress logic. Test Asia/Shanghai timezone conversions explicitly. Test auto-completion logic (current_count reaching target_count).

Key test concerns:
- List tasks: verify auto-upsert of progress records, verify all fields present in response, verify filter by task_type and task_status
- Period key generation (critical): test all 5 examples from the Scenario Outline. The UTC 23:59:59 Dec 31 = Asia/Shanghai Jan 1 case is especially important to catch timezone bugs.
- Task progress: verify valid play increments current_count, verify auto-complete when reaching target_count (status changes to 2, completed_at set)
- Claim reward: verify 200 with diamond_balance increase, status change to 3 (claimed), claimed_at set. Verify 400 for in-progress, already-claimed, and other user's task.
- Concurrent claim (critical): launch 10 coroutines claiming same task. Verify exactly 1 succeeds and diamond_balance is exactly the reward amount (not multiplied). This tests FOR UPDATE row lock.
- Sign-in: verify 200 with diamonds=1, diamond_balance increased, sign_in_record created. Verify 409 for duplicate. Verify next-day sign-in works (different period_key).
- Concurrent sign-in (critical): launch 5 coroutines. Verify exactly 1 succeeds and diamond_balance increases by exactly 1.

## Files

- `kotlin/src/test/kotlin/lab/gabon/route/TaskRoutesTest.kt` -- Integration tests for task and sign-in endpoints
- `kotlin/src/test/kotlin/lab/gabon/service/TaskServiceTest.kt` -- Unit tests for period key generation and task logic

## Verification

```bash
cd kotlin && ./gradlew test --tests '*Task*'
```

- All tests compile successfully
- All tests FAIL (Red phase) because TaskService, TaskRepo, SignInRepo, and task routes do not exist yet
- Test count matches BDD scenario count (12 Feature 6 + 5 Feature 7 = 17 scenarios minimum)
