package com.possible_triangle.spotless

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

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
    val secret = application.propertyOrNull<String>("webhook.secret") ?: run {
        if (application.developmentMode) return
        else error("webhook secret not defined")
    }

    val received = request.header("x-hub-signature-256") ?: unauthorized("webhook secret missing")

    val expected = "sha256=${body.sha256(secret)}"

    if (expected != received) {
        forbidden("invalid webhook secret")
    }
}
