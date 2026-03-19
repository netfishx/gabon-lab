# Task 013: Admin Video Review + Admin CRUD implementation

**type**: impl
**depends-on**: [013-admin-test]

## Description

Implement all admin functionality to pass the Feature 8 BDD tests.

Key implementation areas:

- **Admin Auth**: Login endpoint with status check (disabled admin returns 403 "account is disabled"). Admin JWT uses `iss=gabon-admin`, `aud=admin` to distinguish from customer tokens. Passwords verified with bcrypt.

- **Admin Video Management**:
  - List videos with filters: `status`, `author_name` (ILIKE), `start_date`/`end_date` (end_date is exclusive, internally +1 day), `is_vip` (join customers table). Paginated response.
  - Video detail: full metadata including `review_notes`, `reviewed_by`, `reviewed_at`, `file_name`, `file_size`, all count fields.
  - Review: accept status 4 (approved) or 5 (rejected) only, reject 422 for other values. Set `reviewed_by`, `reviewed_at`, `review_notes`.
  - Delete: soft-delete (set `deleted_at`).

- **Admin User CRUD** (superadmin role=1 only for create/delete):
  - List admin users, get by ID, create (201), update, delete.
  - Create/delete require `role=1` (superadmin), otherwise 403 "superadmin required".
  - Cannot delete self (400 "cannot delete yourself").
  - Password change: admin can only change own password; changing another admin's password returns 403 "cannot change other admin's password".

- **Customer Management**:
  - List customers with filters (`name` ILIKE, `is_vip`).
  - Reset customer password (admin sets new bcrypt password for a customer).

- **Route Registration**: All routes under `/admin/v1/` prefix in `plugin/Routing.kt`.

## Files

- `kotlin/src/main/kotlin/lab/gabon/repository/AdminUserRepo.kt` -- admin_users table queries (findByUsername, findById, list, create, update, delete)
- `kotlin/src/main/kotlin/lab/gabon/service/AdminService.kt` -- admin auth logic (login with status check, JWT generation), admin CRUD with role checks, customer management
- `kotlin/src/main/kotlin/lab/gabon/route/AdminRoutes.kt` -- all /admin/v1/ route handlers (auth, videos, admin-users, customers)

## Verification

1. `./gradlew test --tests "lab.gabon.route.AdminRoutesTest"` -- all tests pass (Green)
2. Admin login returns tokens with `iss=gabon-admin`, `aud=admin`
3. Disabled admin gets 403
4. Video filters (status, author_name, date_range, is_vip) return correct subsets
5. Review with status=3 returns 422; status=4/5 succeeds
6. Non-superadmin creating admin gets 403
7. Self-delete returns 400
8. Customer password reset allows login with new password
