package com.possible_triangle.spotless

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import kotlinx.serialization.Serializable

class ApiException(val status: HttpStatusCode, message: String) : RuntimeException(message)

@Serializable
private data class ApiError(val status: Int, val message: String)

suspend fun ApplicationCall.respondError(status: HttpStatusCode, exception: Exception) {
    respond(status, ApiError(status.value, exception.message ?: "Internal Server Error"))
}

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<Exception> { call, ex ->
            if (ex is ApiException) {
                call.respondError(ex.status, ex)
            } else {
                call.respondError(HttpStatusCode.InternalServerError, ex)
                call.application.log.error("unhandled exception occured", ex)
            }
        }
    }
}

fun unauthorized(message: String): Nothing = throw ApiException(HttpStatusCode.Unauthorized, message)
fun forbidden(message: String): Nothing = throw ApiException(HttpStatusCode.Forbidden, message)
fun badRequest(message: String): Nothing = throw ApiException(HttpStatusCode.BadRequest, message)
