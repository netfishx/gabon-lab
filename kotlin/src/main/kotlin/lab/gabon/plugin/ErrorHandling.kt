package lab.gabon.plugin

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import lab.gabon.model.AppException
import lab.gabon.model.JsonData

fun Application.configureErrorHandling() {
    install(StatusPages) {
        exception<AppException> { call, cause ->
            val error = cause.error
            call.respond(
                HttpStatusCode.fromValue(error.statusCode),
                JsonData.error(error),
            )
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                JsonData.error(HttpStatusCode.InternalServerError.value, "internal server error"),
            )
        }
    }
}
