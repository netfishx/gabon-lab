package lab.gabon.plugin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
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
                JsonData.error(500, "internal server error"),
            )
        }
    }
}
