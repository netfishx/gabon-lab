package lab.gabon.model

import io.ktor.http.HttpStatusCode

/**
 * Sealed error hierarchy mirroring Go's ErrorCode + AppError pattern.
 * Each variant carries its HTTP status, machine-readable error code, and human message.
 */
sealed interface AppError {
    val statusCode: Int
    val errorCode: String
    val message: String

    // ── Auth ────────────────────────────────────────────────
    data class InvalidCredentials(
        override val message: String = "invalid username or password",
    ) : AppError {
        override val statusCode: Int = HttpStatusCode.Unauthorized.value
        override val errorCode: String = "AUTH_INVALID_CREDENTIALS"
    }

    data class TokenExpired(
        override val message: String = "token expired",
    ) : AppError {
        override val statusCode: Int = HttpStatusCode.Unauthorized.value
        override val errorCode: String = "AUTH_TOKEN_EXPIRED"
    }

    data class TokenInvalid(
        override val message: String = "invalid token",
    ) : AppError {
        override val statusCode: Int = HttpStatusCode.Unauthorized.value
        override val errorCode: String = "AUTH_TOKEN_INVALID"
    }

    data class UsernameExists(
        override val message: String = "username already exists",
    ) : AppError {
        override val statusCode: Int = HttpStatusCode.Conflict.value
        override val errorCode: String = "AUTH_USERNAME_EXISTS"
    }

    data class PasswordMismatch(
        override val message: String = "password mismatch",
    ) : AppError {
        override val statusCode: Int = HttpStatusCode.BadRequest.value
        override val errorCode: String = "AUTH_PASSWORD_MISMATCH"
    }

    // ── Generic ─────────────────────────────────────────────
    data class NotFound(
        override val message: String = "resource not found",
    ) : AppError {
        override val statusCode: Int = HttpStatusCode.NotFound.value
        override val errorCode: String = "NOT_FOUND"
    }

    data class BadRequest(
        override val message: String = "bad request",
    ) : AppError {
        override val statusCode: Int = HttpStatusCode.BadRequest.value
        override val errorCode: String = "BAD_REQUEST"
    }

    data class Forbidden(
        override val message: String = "forbidden",
    ) : AppError {
        override val statusCode: Int = HttpStatusCode.Forbidden.value
        override val errorCode: String = "FORBIDDEN"
    }

    // ── Video ───────────────────────────────────────────────
    data class VideoNotFound(
        override val message: String = "video not found",
    ) : AppError {
        override val statusCode: Int = HttpStatusCode.NotFound.value
        override val errorCode: String = "VIDEO_NOT_FOUND"
    }

    data class VideoNotApproved(
        override val message: String = "video not approved",
    ) : AppError {
        override val statusCode: Int = HttpStatusCode.Forbidden.value
        override val errorCode: String = "VIDEO_NOT_APPROVED"
    }

    data class AlreadyLiked(
        override val message: String = "already liked",
    ) : AppError {
        override val statusCode: Int = HttpStatusCode.Conflict.value
        override val errorCode: String = "VIDEO_ALREADY_LIKED"
    }

    // ── Social ──────────────────────────────────────────────
    data class AlreadyFollowing(
        override val message: String = "already following",
    ) : AppError {
        override val statusCode: Int = HttpStatusCode.Conflict.value
        override val errorCode: String = "USER_ALREADY_FOLLOWING"
    }

    data class CannotFollowSelf(
        override val message: String = "cannot follow self",
    ) : AppError {
        override val statusCode: Int = HttpStatusCode.BadRequest.value
        override val errorCode: String = "USER_CANNOT_FOLLOW_SELF"
    }

    data class NotFollowing(
        override val message: String = "not following",
    ) : AppError {
        override val statusCode: Int = HttpStatusCode.BadRequest.value
        override val errorCode: String = "USER_NOT_FOLLOWING"
    }

    // ── Task ────────────────────────────────────────────────
    data class TaskNotClaimable(
        override val message: String = "task not claimable",
    ) : AppError {
        override val statusCode: Int = HttpStatusCode.BadRequest.value
        override val errorCode: String = "TASK_NOT_CLAIMABLE"
    }

    data class AlreadySignedIn(
        override val message: String = "already signed in today",
    ) : AppError {
        override val statusCode: Int = HttpStatusCode.Conflict.value
        override val errorCode: String = "ALREADY_SIGNED_IN"
    }

    // ── Rate Limit ──────────────────────────────────────────
    data class RateLimited(
        override val message: String = "too many requests",
    ) : AppError {
        override val statusCode: Int = HttpStatusCode.TooManyRequests.value
        override val errorCode: String = "RATE_LIMITED"
    }

    // ── Internal ────────────────────────────────────────────
    data class Internal(
        override val message: String = "internal server error",
    ) : AppError {
        override val statusCode: Int = HttpStatusCode.InternalServerError.value
        override val errorCode: String = "INTERNAL_ERROR"
    }
}

/** Throwable wrapper so AppError can be thrown through Ktor's pipeline. */
class AppException(
    val error: AppError,
) : RuntimeException(error.message)
