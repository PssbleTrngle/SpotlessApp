package com.possible_triangle.spotless

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.config.property
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.internal.readJson

private val JSON = createJson()

fun Application.configureRouting() {
    routing {
        get("/status") {
            call.respond(HttpStatusCode.OK)
        }

        post("/github") {
            val event = call.decodeEvent()

            if (event != null) {
                handleComment(event)
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotModified)
            }
        }
    }
}

private fun RoutingCall.eventType(): String {
    return request.header("x-github-event") ?: badRequest("no event type specified")
}

private suspend fun RoutingCall.decodeEvent(): IssueCommentEvent? {
    val body = receiveText()
    verifySecret(body)

    val eventType = eventType()
    return if (eventType == "issue_comment") {
        JSON.decodeFromString<IssueCommentEvent>(body)
    } else {
        null
    }
}

private fun RoutingCall.verifySecret(body: String) {
    val secret = application.property<String>("webhook.secret")
    val received = request.header("x-hub-signature-256") ?: unauthorized("webhook secret missing")

    val expected = "sha256=${body.sha256(secret)}"

    if (expected != received) {
        forbidden("invalid webhook secret")
    }
}
