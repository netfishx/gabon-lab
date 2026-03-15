use axum::extract::{Path, State};

use gabon_infra::social_repo::PgSocialRepo;
use gabon_shared::error::AppError;
use gabon_shared::response::JsonData;
use gabon_shared::traits::SocialRepo;

use crate::AppState;
use crate::middleware::AuthCustomer;

// ─── Handlers ──────────────────────────────────

pub async fn follow_handler(
    State(state): State<AppState>,
    Path(user_id): Path<i64>,
    AuthCustomer(claims): AuthCustomer,
) -> Result<JsonData<()>, AppError> {
    let repo = PgSocialRepo { pool: &state.db };
    follow_user(&repo, claims.sub, user_id).await?;
    Ok(JsonData::ok(()))
}

pub async fn unfollow_handler(
    State(state): State<AppState>,
    Path(user_id): Path<i64>,
    AuthCustomer(claims): AuthCustomer,
) -> Result<JsonData<()>, AppError> {
    let repo = PgSocialRepo { pool: &state.db };
    unfollow_user(&repo, claims.sub, user_id).await?;
    Ok(JsonData::ok(()))
}

// ─── Service ───────────────────────────────────

#[allow(clippy::similar_names)]
fn validate_follow(follower_id: i64, followed_id: i64) -> Result<(), AppError> {
    if follower_id == followed_id {
        return Err(AppError::BadRequest("不能关注自己".into()));
    }
    Ok(())
}

#[allow(clippy::similar_names)]
pub async fn follow_user(
    repo: &impl SocialRepo,
    follower_id: i64,
    followed_id: i64,
) -> Result<(), AppError> {
    validate_follow(follower_id, followed_id)?;

    let created = repo.follow(follower_id, followed_id).await?;
    if !created {
        return Err(AppError::Conflict("已关注该用户".into()));
    }
    Ok(())
}

#[allow(clippy::similar_names)]
pub async fn unfollow_user(
    repo: &impl SocialRepo,
    follower_id: i64,
    followed_id: i64,
) -> Result<(), AppError> {
    let deleted = repo.unfollow(follower_id, followed_id).await?;
    if !deleted {
        return Err(AppError::NotFound("未关注该用户".into()));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use std::cell::Cell;

    use gabon_shared::traits::SocialRepo;

    use super::*;

    // ─── Mock repo ─────────────────────────────────
    struct MockSocialRepo {
        follow_returns: Cell<bool>,
        unfollow_returns: Cell<bool>,
    }

    impl MockSocialRepo {
        fn new() -> Self {
            Self {
                follow_returns: Cell::new(true),
                unfollow_returns: Cell::new(true),
            }
        }

        fn with_follow_returns(mut self, val: bool) -> Self {
            self.follow_returns = Cell::new(val);
            self
        }

        fn with_unfollow_returns(mut self, val: bool) -> Self {
            self.unfollow_returns = Cell::new(val);
            self
        }
    }

    impl SocialRepo for MockSocialRepo {
        async fn follow(&self, _follower_id: i64, _followed_id: i64) -> Result<bool, AppError> {
            Ok(self.follow_returns.get())
        }

        async fn unfollow(&self, _follower_id: i64, _followed_id: i64) -> Result<bool, AppError> {
            Ok(self.unfollow_returns.get())
        }

        async fn get_following(&self, _customer_id: i64) -> Result<Vec<gabon_shared::traits::FollowRow>, AppError> {
            Ok(vec![])
        }

        async fn get_followers(&self, _customer_id: i64) -> Result<Vec<gabon_shared::traits::FollowRow>, AppError> {
            Ok(vec![])
        }
    }

    // ─── Validation tests ──────────────────────────
    #[test]
    fn follow_self_returns_error() {
        let result = validate_follow(42, 42);
        assert!(result.is_err());
    }

    #[test]
    fn follow_different_user_passes_validation() {
        let result = validate_follow(1, 2);
        assert!(result.is_ok());
    }

    // ─── follow_user tests ─────────────────────────
    #[tokio::test]
    async fn follow_user_succeeds() {
        let repo = MockSocialRepo::new();
        let result = follow_user(&repo, 1, 2).await;
        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn follow_user_rejects_self_follow() {
        let repo = MockSocialRepo::new();
        let result = follow_user(&repo, 5, 5).await;
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn follow_user_rejects_duplicate() {
        let repo = MockSocialRepo::new().with_follow_returns(false);
        let result = follow_user(&repo, 1, 2).await;
        assert!(result.is_err());
    }

    // ─── unfollow_user tests ───────────────────────
    #[tokio::test]
    async fn unfollow_user_succeeds() {
        let repo = MockSocialRepo::new();
        let result = unfollow_user(&repo, 1, 2).await;
        assert!(result.is_ok());
    }

    #[tokio::test]
    async fn unfollow_user_fails_when_not_following() {
        let repo = MockSocialRepo::new().with_unfollow_returns(false);
        let result = unfollow_user(&repo, 1, 2).await;
        assert!(result.is_err());
    }
}
