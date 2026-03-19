package lab.gabon.common;

public sealed interface AppError {

  String errorCode(); // Internal identifier, e.g. "AUTH_INVALID_CREDENTIALS" (for logs)

  String message(); // Human-readable, maps to response.message

  int status(); // HTTP status code, maps to response.code

  // Generic errors
  record Internal(String message) implements AppError {
    public String errorCode() {
      return "INTERNAL_ERROR";
    }

    public int status() {
      return 500;
    }
  }

  record BadRequest(String message) implements AppError {
    public String errorCode() {
      return "BAD_REQUEST";
    }

    public int status() {
      return 400;
    }
  }

  record NotFound(String message) implements AppError {
    public String errorCode() {
      return "NOT_FOUND";
    }

    public int status() {
      return 404;
    }
  }

  record Unauthorized() implements AppError {
    public String errorCode() {
      return "UNAUTHORIZED";
    }

    public String message() {
      return "unauthorized";
    }

    public int status() {
      return 401;
    }
  }

  record Forbidden() implements AppError {
    public String errorCode() {
      return "FORBIDDEN";
    }

    public String message() {
      return "forbidden";
    }

    public int status() {
      return 403;
    }
  }

  // Auth errors
  record InvalidCredentials() implements AppError {
    public String errorCode() {
      return "AUTH_INVALID_CREDENTIALS";
    }

    public String message() {
      return "invalid username or password";
    }

    public int status() {
      return 401;
    }
  }

  record TokenExpired() implements AppError {
    public String errorCode() {
      return "AUTH_TOKEN_EXPIRED";
    }

    public String message() {
      return "token expired";
    }

    public int status() {
      return 401;
    }
  }

  record TokenInvalid() implements AppError {
    public String errorCode() {
      return "AUTH_TOKEN_INVALID";
    }

    public String message() {
      return "token invalid";
    }

    public int status() {
      return 401;
    }
  }

  record UsernameExists() implements AppError {
    public String errorCode() {
      return "AUTH_USERNAME_EXISTS";
    }

    public String message() {
      return "username already exists";
    }

    public int status() {
      return 409;
    }
  }

  record PasswordMismatch() implements AppError {
    public String errorCode() {
      return "AUTH_PASSWORD_MISMATCH";
    }

    public String message() {
      return "current password is incorrect";
    }

    public int status() {
      return 400;
    }
  }

  // Video errors
  record VideoNotFound() implements AppError {
    public String errorCode() {
      return "VIDEO_NOT_FOUND";
    }

    public String message() {
      return "video not found";
    }

    public int status() {
      return 404;
    }
  }

  record VideoNotApproved() implements AppError {
    public String errorCode() {
      return "VIDEO_NOT_APPROVED";
    }

    public String message() {
      return "video not approved";
    }

    public int status() {
      return 403;
    }
  }

  record AlreadyLiked() implements AppError {
    public String errorCode() {
      return "VIDEO_ALREADY_LIKED";
    }

    public String message() {
      return "already liked";
    }

    public int status() {
      return 409;
    }
  }

  // User errors
  record AlreadyFollowing() implements AppError {
    public String errorCode() {
      return "USER_ALREADY_FOLLOWING";
    }

    public String message() {
      return "already following";
    }

    public int status() {
      return 409;
    }
  }

  record CannotFollowSelf() implements AppError {
    public String errorCode() {
      return "USER_CANNOT_FOLLOW_SELF";
    }

    public String message() {
      return "cannot follow yourself";
    }

    public int status() {
      return 400;
    }
  }

  record NotFollowing() implements AppError {
    public String errorCode() {
      return "USER_NOT_FOLLOWING";
    }

    public String message() {
      return "not following";
    }

    public int status() {
      return 400;
    }
  }

  // Task errors
  record TaskNotClaimable() implements AppError {
    public String errorCode() {
      return "TASK_NOT_CLAIMABLE";
    }

    public String message() {
      return "task not claimable";
    }

    public int status() {
      return 400;
    }
  }

  record AlreadySignedIn() implements AppError {
    public String errorCode() {
      return "ALREADY_SIGNED_IN";
    }

    public String message() {
      return "already signed in today";
    }

    public int status() {
      return 409;
    }
  }

  // Rate limit
  record RateLimited() implements AppError {
    public String errorCode() {
      return "RATE_LIMITED";
    }

    public String message() {
      return "too many requests";
    }

    public int status() {
      return 429;
    }
  }
}
