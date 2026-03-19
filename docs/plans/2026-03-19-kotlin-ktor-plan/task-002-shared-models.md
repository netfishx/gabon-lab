# Task 002: Shared models and error handling

**type**: setup
**depends-on**: [001]

## Description

Define the shared type system, error hierarchy, and Ktor plugins for serialization and error mapping. This is the foundation that all feature tasks depend on for request/response handling.

Key decisions:

- **Error hierarchy** (`model/Error.kt`): define `sealed interface AppError` with a `code: Int` and `message: String` property. Each variant is a data class implementing AppError. Variants from BDD Appendix B:
  - `InvalidCredentials` (401, "invalid username or password")
  - `TokenExpired` (401, "token expired")
  - `TokenInvalid` (401, "invalid token")
  - `TokenRevoked` (401, "token revoked")
  - `Unauthorized` (401, "unauthorized")
  - `Forbidden` (403, "forbidden")
  - `NotFound` (404, "resource not found")
  - `Conflict` (409, "resource already exists")
  - `ValidationError` (422, custom message)
  - `InternalError` (500, "internal server error")
  Define `class AppException(val error: AppError) : RuntimeException(error.message)`.

- **Response models** (`model/Response.kt`): `@Serializable data class JsonData<T>(val code: Int, val message: String, val data: T?)` with companion factory methods `ok(data)`, `error(appError)`. `@Serializable data class Paginated<T>(val list: List<T>, val total: Long, val page: Int, val pageSize: Int)`.

- **Request models** (`model/Request.kt`): define `@Serializable data class PageQuery(val page: Int = 1, val pageSize: Int = 10)` as a shared pagination parameter type.

- **Constants** (`model/Constants.kt`): define `enum class VideoStatus(val value: Int)` with PENDING(0), APPROVED(1), REJECTED(2). Define `enum class TaskType(val value: String)` with SIGN_IN, WATCH_VIDEO, LIKE_VIDEO, FOLLOW_USER. Define `enum class TaskStatus(val value: Int)` with INCOMPLETE(0), COMPLETE(1), REWARDED(2). Define bucket name constants.

- **Serialization plugin** (`plugin/Serialization.kt`): install `ContentNegotiation` with `json { namingStrategy = JsonNamingStrategy.SnakeCase; ignoreUnknownKeys = true; encodeDefaults = true }`.

- **Error handling plugin** (`plugin/ErrorHandling.kt`): install `StatusPages` to catch `AppException` and return the corresponding HTTP status code with `JsonData.error(e.error)` body. Also catch generic exceptions and return 500.

## Files

- `kotlin/src/main/kotlin/lab/gabon/model/Error.kt` — sealed AppError hierarchy and AppException
- `kotlin/src/main/kotlin/lab/gabon/model/Constants.kt` — VideoStatus, TaskType, TaskStatus enums, bucket constants
- `kotlin/src/main/kotlin/lab/gabon/model/Request.kt` — shared request types (PageQuery)
- `kotlin/src/main/kotlin/lab/gabon/model/Response.kt` — JsonData<T>, Paginated<T>
- `kotlin/src/main/kotlin/lab/gabon/plugin/ErrorHandling.kt` — StatusPages configuration mapping AppException to HTTP responses
- `kotlin/src/main/kotlin/lab/gabon/plugin/Serialization.kt` — ContentNegotiation with kotlinx-serialization, snake_case

## Verification

1. `./gradlew build` compiles without errors
2. Write a temporary test route that `throw AppException(AppError.InvalidCredentials())` — curl returns HTTP 401 with body `{"code":401,"message":"invalid username or password","data":null}`
3. A route returning `JsonData.ok(mapOf("name" to "test"))` produces `{"code":0,"message":"ok","data":{"name":"test"}}`
4. Verify snake_case: a response field declared as `videoCount` serializes as `"video_count"` in JSON
