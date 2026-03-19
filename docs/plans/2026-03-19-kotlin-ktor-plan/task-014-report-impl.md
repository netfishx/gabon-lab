# Task 014: Admin Reports implementation

**type**: impl
**depends-on**: [014-report-test]

## Description

Implement all report endpoints to pass the Feature 11 BDD tests.

Key implementation areas:

- **Revenue Report** (`GET /admin/v1/reports/revenue`):
  - Query parameters: `start_date`, `end_date` (date range filter on `claimed_at`).
  - SQL: `GROUP BY DATE(claimed_at)`, aggregate `COUNT(*)` as `claim_count` and `SUM(reward_diamonds)` as `total_diamonds` from task_progress where `task_status=3` (claimed).
  - Response: list of `{ date, claim_count, total_diamonds }`.

- **Video Daily Report** (`GET /admin/v1/reports/videos/daily`):
  - Query parameters: `start_date`, `end_date` (date range filter on `created_at`).
  - SQL: `GROUP BY DATE(created_at)`, aggregate `COUNT(*)` as `upload_count`, `SUM(total_clicks)`, `SUM(valid_clicks)`, `SUM(like_count)`.
  - Response: list of `{ date, upload_count, total_clicks, total_valid_clicks, total_likes }`.

- **Video Summary Report** (`GET /admin/v1/reports/videos/summary`):
  - Query parameters: `start_date`, `end_date` (date range filter on `created_at`).
  - SQL: single-row aggregate -- `COUNT(*)` as `total_videos`, conditional counts for `approved_count` (status=4), `pending_count` (status=3), `rejected_count` (status=5), plus `SUM` of click/like columns.
  - Response: single object `{ total_videos, approved_count, pending_count, rejected_count, total_clicks, total_valid_clicks, total_likes }`.

- **Route paths**: `/admin/v1/reports/revenue`, `/admin/v1/reports/videos/daily`, `/admin/v1/reports/videos/summary`. Note: paths use `/reports/videos/` (plural), not `/reports/video/`.

- All endpoints require admin authentication.

## Files

- `kotlin/src/main/kotlin/lab/gabon/repository/ReportRepo.kt` -- report SQL queries (revenue grouped by date, video daily grouped by date, video summary aggregate)
- `kotlin/src/main/kotlin/lab/gabon/service/ReportService.kt` -- report business logic, date range parsing
- `kotlin/src/main/kotlin/lab/gabon/route/ReportRoutes.kt` -- /admin/v1/reports/ route handlers

## Verification

1. `./gradlew test --tests "lab.gabon.route.ReportRoutesTest"` -- all tests pass (Green)
2. Revenue report returns date-grouped claim counts and diamond sums
3. Video daily report returns date-grouped upload/click/like counts
4. Video summary returns aggregate totals with status breakdowns
5. All endpoints require admin authentication (401 without token)
