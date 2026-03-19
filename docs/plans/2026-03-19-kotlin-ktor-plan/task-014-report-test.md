# Task 014: Admin Reports tests (BDD Feature 11)

**type**: test
**depends-on**: [013-admin-impl]

## Description

Write BDD-driven test cases for Feature 11: Admin Reports. This covers revenue reports (grouped by date with diamond sums), video daily reports (grouped by date with upload/click/like counts), and video summary reports (aggregate totals). All admin-only endpoints under `/admin/v1/reports/`. All tests should fail initially (Red phase).

Note the route paths: video reports are at `/reports/video/daily` and `/reports/video/summary` (singular, matching Go implementation at `go/internal/transport/report_handler.go:117`).

## Gherkin Specifications

```gherkin
Feature: Admin Reports
  Revenue reports, daily video statistics, and video summary.
  All admin-only endpoints under /admin/v1/.

  Scenario: Revenue report by date range
    Given I am logged in as admin
    When I GET /admin/v1/reports/revenue?start_date=2026-03-01&end_date=2026-03-19
    Then the response status is 200
    And each item contains date, claim_count, total_diamonds

  Scenario: Video daily report
    Given I am logged in as admin
    When I GET /admin/v1/reports/video/daily?start_date=2026-03-01&end_date=2026-03-19
    Then the response status is 200
    And each item contains date, upload_count, total_clicks, total_valid_clicks, total_likes

  Scenario: Video summary report
    Given I am logged in as admin
    When I GET /admin/v1/reports/video/summary?start_date=2026-03-01&end_date=2026-03-19
    Then the response status is 200
    And the response contains total_videos, approved_count, pending_count,
        rejected_count, total_clicks, total_valid_clicks, total_likes
```

## Files

- `kotlin/src/test/kotlin/lab/gabon/route/ReportRoutesTest.kt` -- test cases for revenue report, video daily report, video summary report

## Verification

1. `./gradlew test --tests "lab.gabon.route.ReportRoutesTest"` compiles
2. All tests fail (Red) because no report routes or services exist yet
