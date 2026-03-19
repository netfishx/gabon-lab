package lab.gabon.model

import kotlinx.serialization.Serializable

/** Unified API response envelope. code=0 means success, otherwise error. */
@Serializable
data class JsonData<T>(
    val code: Int,
    val message: String,
    val data: T? = null,
) {
    companion object {
        fun <T> ok(data: T): JsonData<T> = JsonData(code = 0, message = "ok", data = data)

        fun ok(): JsonData<Unit> = JsonData(code = 0, message = "ok", data = null)

        fun error(error: AppError): JsonData<Nothing?> =
            JsonData(
                code = error.statusCode,
                message = error.message,
                data = null,
            )

        fun error(
            code: Int,
            message: String,
        ): JsonData<Nothing?> = JsonData(code = code, message = message, data = null)
    }
}

/** Paginated list wrapper. */
@Serializable
data class Paginated<T>(
    val items: List<T>,
    val total: Long,
    val page: Int,
    val pageSize: Int,
)
