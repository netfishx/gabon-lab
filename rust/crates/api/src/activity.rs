use axum::extract::{Path, State};

use gabon_infra::activity_repo::PgActivityRepo;
use gabon_shared::error::AppError;
use gabon_shared::response::JsonData;
use gabon_shared::traits::{ActivityRepo, TaskProgressRow, TaskStatus};

use crate::AppState;
use crate::middleware::AuthCustomer;

// ─── Handlers ──────────────────────────────────

pub async fn sign_in_handler(
    State(state): State<AppState>,
    AuthCustomer(claims): AuthCustomer,
) -> Result<JsonData<i64>, AppError> {
    let repo = PgActivityRepo { pool: &state.db };
    let diamonds = sign_in(&repo, claims.sub).await?;
    Ok(JsonData::ok(diamonds))
}

pub async fn claim_handler(
    State(state): State<AppState>,
    Path(progress_id): Path<i64>,
    AuthCustomer(claims): AuthCustomer,
) -> Result<JsonData<i64>, AppError> {
    let repo = PgActivityRepo { pool: &state.db };
    let diamonds = claim_reward(&repo, progress_id, claims.sub).await?;
    Ok(JsonData::ok(diamonds))
}

pub async fn list_tasks_handler(
    State(state): State<AppState>,
    AuthCustomer(claims): AuthCustomer,
) -> Result<JsonData<Vec<TaskProgressRow>>, AppError> {
    let repo = PgActivityRepo { pool: &state.db };
    let tasks = list_tasks(&repo, claims.sub).await?;
    Ok(JsonData::ok(tasks))
}

// ─── Service ───────────────────────────────────

/// Sign in for today. Returns diamonds awarded.
pub async fn sign_in(repo: &impl ActivityRepo, customer_id: i64) -> Result<i64, AppError> {
    let today = today_period_key();

    if repo.has_signed_in_today(customer_id, &today).await? {
        return Err(AppError::Conflict("今日已签到".into()));
    }

    let diamonds = 1; // base sign-in reward
    repo.record_sign_in(customer_id, &today, diamonds).await
}

/// Claim a completed task reward. Returns diamonds awarded.
pub async fn claim_reward(
    repo: &impl ActivityRepo,
    progress_id: i64,
    customer_id: i64,
) -> Result<i64, AppError> {
    let progress = repo
        .get_task_progress(progress_id)
        .await?
        .ok_or(AppError::NotFound("任务不存在".into()))?;

    if progress.customer_id != customer_id {
        return Err(AppError::Forbidden);
    }

    if progress.task_status != TaskStatus::Completed as i16 {
        return Err(AppError::BadRequest("任务未完成或已领取".into()));
    }

    let diamonds = i64::from(progress.reward_diamonds);
    repo.claim_task(progress_id, customer_id, diamonds).await?;
    Ok(diamonds)
}

/// List all task progress for a customer.
pub async fn list_tasks(repo: &impl ActivityRepo, customer_id: i64) -> Result<Vec<TaskProgressRow>, AppError> {
    repo.list_tasks(customer_id).await
}

fn today_period_key() -> String {
    chrono::Utc::now().format("%Y-%m-%d").to_string()
}

#[cfg(test)]
mod tests {
    use gabon_shared::error::AppError;
    use gabon_shared::traits::{ActivityRepo, TaskProgressRow, TaskStatus};

    use super::*;

    // ─── Mock ActivityRepo ─────────────────────────

    struct MockActivityRepo {
        already_signed: bool,
        task_progress: Option<TaskProgressRow>,
        claim_should_fail: bool,
    }

    impl MockActivityRepo {
        fn not_signed() -> Self {
            Self { already_signed: false, task_progress: None, claim_should_fail: false }
        }

        fn already_signed() -> Self {
            Self { already_signed: true, task_progress: None, claim_should_fail: false }
        }

        fn with_task(status: i16, current: i32, target: i32) -> Self {
            Self {
                already_signed: false,
                task_progress: Some(TaskProgressRow {
                    id: 1,
                    customer_id: 42,
                    task_id: 10,
                    current_count: current,
                    target_count: target,
                    task_status: status,
                    reward_diamonds: 5,
                }),
                claim_should_fail: false,
            }
        }
    }

    impl ActivityRepo for MockActivityRepo {
        async fn has_signed_in_today(&self, _customer_id: i64, _period_key: &str) -> Result<bool, AppError> {
            Ok(self.already_signed)
        }

        async fn record_sign_in(&self, _customer_id: i64, _period_key: &str, diamonds: i64) -> Result<i64, AppError> {
            Ok(diamonds)
        }

        async fn list_tasks(&self, _customer_id: i64) -> Result<Vec<TaskProgressRow>, AppError> {
            Ok(self.task_progress.clone().into_iter().collect())
        }

        async fn get_task_progress(&self, _progress_id: i64) -> Result<Option<TaskProgressRow>, AppError> {
            Ok(self.task_progress.clone())
        }

        async fn claim_task(&self, _progress_id: i64, _customer_id: i64, _diamonds: i64) -> Result<(), AppError> {
            if self.claim_should_fail {
                Err(AppError::Internal("claim failed".into()))
            } else {
                Ok(())
            }
        }
    }

    // ─── sign_in tests ─────────────────────────────

    #[tokio::test]
    async fn sign_in_succeeds_first_time() {
        let repo = MockActivityRepo::not_signed();
        let result = sign_in(&repo, 42).await;
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), 1); // 1 diamond awarded
    }

    #[tokio::test]
    async fn sign_in_fails_if_already_signed() {
        let repo = MockActivityRepo::already_signed();
        let result = sign_in(&repo, 42).await;
        assert!(result.is_err());
    }

    // ─── claim_reward tests ────────────────────────

    #[tokio::test]
    async fn claim_succeeds_for_completed_task() {
        let repo = MockActivityRepo::with_task(TaskStatus::Completed as i16, 3, 3);
        let result = claim_reward(&repo, 1, 42).await;
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), 5); // 5 diamonds
    }

    #[tokio::test]
    async fn claim_fails_for_in_progress_task() {
        let repo = MockActivityRepo::with_task(TaskStatus::InProgress as i16, 1, 3);
        let result = claim_reward(&repo, 1, 42).await;
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn claim_fails_for_already_claimed_task() {
        let repo = MockActivityRepo::with_task(TaskStatus::Claimed as i16, 3, 3);
        let result = claim_reward(&repo, 1, 42).await;
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn claim_fails_for_nonexistent_task() {
        let repo = MockActivityRepo::not_signed(); // task_progress = None
        let result = claim_reward(&repo, 999, 42).await;
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn claim_fails_if_wrong_customer() {
        let repo = MockActivityRepo::with_task(TaskStatus::Completed as i16, 3, 3);
        // task belongs to customer 42, but requester is 99
        let result = claim_reward(&repo, 1, 99).await;
        assert!(result.is_err());
    }

    // ─── list_tasks tests ─────────────────────────

    #[tokio::test]
    async fn list_tasks_returns_items() {
        let repo = MockActivityRepo::with_task(TaskStatus::InProgress as i16, 1, 3);
        let tasks = list_tasks(&repo, 42).await.unwrap();
        assert_eq!(tasks.len(), 1);
        assert_eq!(tasks[0].task_id, 10);
    }

    #[tokio::test]
    async fn list_tasks_returns_empty_for_no_tasks() {
        let repo = MockActivityRepo::not_signed();
        let tasks = list_tasks(&repo, 42).await.unwrap();
        assert!(tasks.is_empty());
    }
}
