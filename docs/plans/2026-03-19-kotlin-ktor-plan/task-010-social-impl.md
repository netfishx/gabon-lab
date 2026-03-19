# Task 010: Social System Implementation

**type**: impl
**depends-on**: ["010-social-test"]

## Description

Implement the follow/unfollow social system covering BDD Feature 5. This is the Green phase -- make all social tests pass.

### SocialRepo

- `follow(followerId, followedId)`: INSERT INTO user_follows (follower_id, followed_id). Use ON CONFLICT (follower_id, followed_id) DO NOTHING to handle duplicate. Return whether a row was actually inserted (to distinguish 200 vs 409).
- `unfollow(followerId, followedId)`: DELETE FROM user_follows WHERE follower_id=? AND followed_id=?. Return affected row count (0 means not following).
- `isFollowing(followerId, followedId)`: SELECT EXISTS for quick check.
- `getFollowStatus(viewerId, targetId)`: Compute mutual follow status. Query: check if viewer follows target (forward) and target follows viewer (reverse). Return 0 (none), 1 (one-way), or 2 (mutual). If viewerId is null or equals targetId, return 0.
- `listFollowing(userId, page, pageSize, viewerId?)`: SELECT users JOIN user_follows WHERE follower_id=userId. Include follow_status for each entry relative to viewerId (if authenticated). Return paginated result with user_id, username, name, avatar_url, follow_status.
- `listFollowers(userId, page, pageSize, viewerId?)`: SELECT users JOIN user_follows WHERE followed_id=userId. Include follow_status for each entry. Return paginated result.
- `getFollowingCount(userId)`: SELECT COUNT(*) FROM user_follows WHERE follower_id=?
- `getFollowerCount(userId)`: SELECT COUNT(*) FROM user_follows WHERE followed_id=?

### SocialService

- `follow(followerId, followedId)`: Validate not self-follow (400 USER_CANNOT_FOLLOW_SELF). Verify target user exists (404 NOT_FOUND "user not found"). Attempt insert. If no row inserted (duplicate), throw 409 USER_ALREADY_FOLLOWING.
- `unfollow(followerId, followedId)`: Validate not self-unfollow (400 USER_CANNOT_FOLLOW_SELF). Attempt delete. If no row deleted, throw 400 USER_NOT_FOLLOWING.
- `getFollowStatus(viewerId?, targetId)`: Delegate to repo. Handle null viewer (return 0) and self-view (return 0).
- `getFollowing(userId, page, pageSize, viewerId?)`: Delegate to repo.
- `getFollowers(userId, page, pageSize, viewerId?)`: Delegate to repo.

### Social Routes (route/SocialRoutes.kt)

Authenticated:
- `POST /api/v1/users/{userId}/follow` -- follow a user
- `DELETE /api/v1/users/{userId}/follow` -- unfollow a user
- `GET /api/v1/users/me/following` -- my following list (with mutual status)
- `GET /api/v1/users/me/followers` -- my followers list

Public (optional auth for follow_status):
- `GET /api/v1/users/{userId}/following` -- user's following list
- `GET /api/v1/users/{userId}/followers` -- user's followers list

**IMPORTANT**: The `GET /api/v1/users/{userId}` public profile endpoint is implemented HERE (not in Task 011) to avoid a circular dependency. This endpoint returns: id, username, name, avatar_url, signature, is_vip, following_count, follower_count, follow_status. It queries CustomerRepo.findById for basic fields and SocialRepo for counts + follow_status. Task 011 only handles `/users/me/*` endpoints (private profile, update, avatar).

Register social routes in `plugin/Routing.kt`.

### Follow Status in Lists

When returning following/followers lists, each entry's follow_status is computed relative to the current viewer:
- If unauthenticated: all entries show follow_status=0
- If authenticated: each entry shows the viewer's relationship to that user (0/1/2)

This requires a subquery or LEFT JOIN per entry to check both directions. For efficiency, batch-check follow relationships for all users in the page.

## Files

- `kotlin/src/main/kotlin/lab/gabon/repository/SocialRepo.kt` -- Follow/unfollow data access layer
- `kotlin/src/main/kotlin/lab/gabon/service/SocialService.kt` -- Social business logic
- `kotlin/src/main/kotlin/lab/gabon/route/SocialRoutes.kt` -- Social HTTP route handlers
- `kotlin/src/main/kotlin/lab/gabon/plugin/Routing.kt` -- Modified to register social routes

## Verification

```bash
cd kotlin && ./gradlew test --tests '*Social*'
```

- All 17+ social tests PASS (Green phase)
- Follow creates user_follows record
- Self-follow returns 400
- Duplicate follow returns 409
- Unfollow deletes record
- Unfollow when not following returns 400
- Mutual detection: 0/1/2 states correct for all combinations
- Unauthenticated always sees follow_status=0
- Following/followers lists paginated with correct follow_status per entry
