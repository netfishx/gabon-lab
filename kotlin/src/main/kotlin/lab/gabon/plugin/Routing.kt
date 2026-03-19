package lab.gabon.plugin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import lab.gabon.model.JsonData
import lab.gabon.route.authRoutes
import lab.gabon.service.AuthService

fun Application.configureRouting(authService: AuthService) {
    routing {
        get("/health") {
            call.respond(HttpStatusCode.OK, JsonData.ok("ok"))
        }

        route("/api/v1") {
            authRoutes(authService)
        }
    }
}
