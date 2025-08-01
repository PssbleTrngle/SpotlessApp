package com.possible_triangle.spotless

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.config.property
import kotlinx.coroutines.async
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

typealias Id = Long

@Serializable
enum class AuthorAssociation {
    COLLABORATOR,
    CONTRIBUTOR,
    FIRST_TIMER,
    FIRST_TIME_CONTRIBUTOR,
    MANNEQUIN,
    MEMBER,
    NONE,
    OWNER,
}

@Serializable
data class User(
    val id: Id,
    val login: String,
    val type: String,
)

@Serializable
data class Comment(
    val id: Id,
    val body: String,
    val user: User,
    @SerialName("author_association")
    val authorAssociation: AuthorAssociation,
)

@Serializable
data class PullRequestLink(
    val url: String,
)

@Serializable
data class Head(
    val ref: String,
    val sha: String,
)

@Serializable
data class PullRequest(
    val url: String,
    val head: Head,
    @SerialName("author_association")
    val authorAssociation: AuthorAssociation,
    @SerialName("maintainer_can_modify")
    val maintainerCanModify: Boolean,
)

@Serializable
data class Repository(
    @SerialName("clone_url")
    val cloneUrl: String,
)

@Serializable
data class Issue(
    val id: Id,
    @SerialName("pull_request")
    val pr: PullRequestLink?,
    val state: IssueState,
)

@Serializable
enum class IssueState {
    @SerialName("open")
    OPEN,

    @SerialName("closed")
    CLOSED,
}

@Serializable
data class Installation(
    val id: Id,
)

@Serializable
data class IssueCommentEvent(
    val action: String,
    val comment: Comment,
    val issue: Issue,
    val repository: Repository,
    val installation: Installation,
)

val client = HttpClient(CIO) {
    defaultRequest {
        url("https://api.github.com/")
        userAgent("https://github.com/PssbleTrngle/SpotlessApp")
    }
    install(ContentNegotiation) {
        json(createJson())
    }
}

fun Application.generateJWT(): String {
    return JWT.create()
        .withIssuedAt(Instant.now())
        .withExpiresAt(Instant.now().plusSeconds(60))
        .withIssuer(property("github.client.id"))
        .sign(Algorithm.RSA256(createKeyProvider()))
}

@Serializable
data class TokenResponse(
    val token: String,
)

suspend fun Application.handleComment(event: IssueCommentEvent) {
    if (event.issue.pr == null) return
    log.debug("Received PR comment '${event.comment.body}' on ${event.issue.pr.url}")

    if (event.comment.body.trim() != "/spotless") return log.debug(" not a command")
    if (event.issue.state != IssueState.OPEN) return log.debug(" already closed")

    val jwt = generateJWT()

    val token = client.post("app/installations/${event.installation.id}/access_tokens") {
        bearerAuth(jwt)
    }.body<TokenResponse>()

    val pullRequest = client.get(event.issue.pr.url) {
        bearerAuth(token.token)
    }.body<PullRequest>()

    async {
        spotlessApply(event.repository.cloneUrl, pullRequest.head)
    }
}