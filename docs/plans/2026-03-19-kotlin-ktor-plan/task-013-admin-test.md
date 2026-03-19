# Task 013: Admin Video Review + Admin CRUD tests (BDD Feature 8)

**type**: test
**depends-on**: [003, 004, 005]

## Description

Write BDD-driven test cases for Feature 8: Admin Video Review. This covers admin authentication (login, disabled admin rejection), admin video management (list with filters, detail, review approve/reject, delete), admin user CRUD (superadmin-only create/delete, password change), and customer management (list, reset password). All tests should fail initially (Red phase).

Admin routes use separate JWT domain: `iss=gabon-admin`, `aud=admin`. Routes are under `/admin/v1/`.

## Gherkin Specifications

```gherkin
Feature: Admin Video Review
  Admins review videos (approve=4, reject=5).
  Admin auth uses separate JWT domain: iss=gabon-admin, aud=admin.
  Routes are under /admin/v1/.

  # --- Admin Auth ---

  Scenario: Admin login
    Given an active admin "superadmin1" with password "admin123"
    When I POST /admin/v1/auth/login with:
      | username | superadmin1 |
      | password | admin123    |
    Then the response status is 200
    And the response contains admin "access_token" and "refresh_token"

  Scenario: Disabled admin cannot login
    Given admin "disabled_admin" has status=0 (disabled)
    When I POST /admin/v1/auth/login with valid credentials
    Then the response status is 403
    And the error code is "FORBIDDEN"
    And the error message is "account is disabled"

  # --- List Videos with Filters ---

  Scenario: List all videos (admin)
    Given I am logged in as admin
    When I GET /admin/v1/videos?page=1&page_size=20
    Then the response status is 200
    And the response contains videos of all statuses

  Scenario: Filter videos by status
    Given I am logged in as admin
    When I GET /admin/v1/videos?status=3
    Then only videos with status=3 (pending_review) are returned

  Scenario: Filter videos by author name
    Given I am logged in as admin
    When I GET /admin/v1/videos?author_name=alice
    Then only videos by authors matching "alice" are returned

  Scenario: Filter videos by date range
    Given I am logged in as admin
    When I GET /admin/v1/videos?start_date=2026-03-01&end_date=2026-03-19
    Then only videos created within the date range are returned
    Note: end_date is exclusive (internally adds 1 day)

  Scenario: Filter videos by VIP author
    Given I am logged in as admin
    When I GET /admin/v1/videos?is_vip=true
    Then only videos from VIP authors are returned

  # --- Review Video ---

  Scenario: Approve a pending video
    Given I am logged in as admin (id=1)
    And video 42 has status=3 (pending_review)
    When I POST /admin/v1/videos/42/review with:
      | status       | 4               |
      | review_notes | Content is good |
    Then the response status is 200
    And video 42 status changes to 4 (approved)
    And reviewed_by is set to admin id=1
    And reviewed_at is set

  Scenario: Reject a pending video
    Given I am logged in as admin
    And video 42 has status=3 (pending_review)
    When I POST /admin/v1/videos/42/review with:
      | status       | 5                       |
      | review_notes | Inappropriate content   |
    Then the response status is 200
    And video 42 status changes to 5 (rejected)

  Scenario: Review with invalid status value
    Given I am logged in as admin
    When I POST /admin/v1/videos/42/review with:
      | status | 3 |
    Then the response status is 422
    Because status must be 4 (approved) or 5 (rejected)

  # --- Admin Video Detail ---

  Scenario: Get video detail (admin)
    Given I am logged in as admin
    When I GET /admin/v1/videos/42
    Then the response status is 200
    And the response contains full detail including review_notes, reviewed_by,
        reviewed_at, file_name, file_size, all counts

  # --- Admin Delete Video ---

  Scenario: Admin delete video
    Given I am logged in as admin
    When I DELETE /admin/v1/videos/42
    Then the response status is 200
    And the video is soft-deleted

  # --- Admin CRUD (Superadmin Only) ---

  Scenario: Create admin (superadmin only)
    Given I am logged in as superadmin (role=1)
    When I POST /admin/v1/admin-users with:
      | username | newadmin   |
      | password | admin123   |
      | role     | 2          |
    Then the response status is 201

  Scenario: Create admin as regular admin (forbidden)
    Given I am logged in as admin (role=2)
    When I POST /admin/v1/admin-users
    Then the response status is 403
    And the error code is "FORBIDDEN"
    And the error message is "superadmin required"

  Scenario: Delete admin (cannot delete self)
    Given I am logged in as superadmin (id=1)
    When I DELETE /admin/v1/admin-users/1
    Then the response status is 400
    And the error message is "cannot delete yourself"

  Scenario: Change another admin's password (superadmin only)
    Given I am logged in as admin (role=2, id=2)
    When I PUT /admin/v1/admin-users/3/password
    Then the response status is 403
    And the error code is "FORBIDDEN"
    And the error message is "cannot change other admin's password"

  # --- Customer Management ---

  Scenario: List customers with filters
    Given I am logged in as admin
    When I GET /admin/v1/customers?name=alice&is_vip=true
    Then only matching customer records are returned

  Scenario: Reset customer password
    Given I am logged in as admin
    When I PUT /admin/v1/customers/42/password with:
      | new_password | resetpass123 |
    Then the response status is 200
    And customer 42 can login with "resetpass123"
```

## Files

- `kotlin/src/test/kotlin/lab/gabon/route/AdminRoutesTest.kt` -- all test cases for admin auth, video management, admin CRUD, customer management

## Verification

1. `./gradlew test --tests "lab.gabon.route.AdminRoutesTest"` compiles
2. All tests fail (Red) because no admin routes or services exist yet
