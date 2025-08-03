package com.possible_triangle.spotless

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

typealias Id = Long

@Serializable
enum class AuthorAssociation(val canRunCommand: Boolean) {
    COLLABORATOR(true),
    CONTRIBUTOR(false),
    FIRST_TIMER(false),
    FIRST_TIME_CONTRIBUTOR(false),
    MANNEQUIN(false),
    MEMBER(true),
    NONE(false),
    OWNER(true),
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
    val repo: Repository,
)

@Serializable
data class PullRequest(
    val url: String,
    val number: Id,
    val head: Head,
    val base: Head,
    @SerialName("author_association")
    val authorAssociation: AuthorAssociation,
    @SerialName("maintainer_can_modify")
    val maintainerCanModify: Boolean,
)

@Serializable
data class Repository(
    val id: Id,
    @SerialName("clone_url")
    val cloneUrl: String,
    @SerialName("full_name")
    val name: String,
)

@Serializable
data class Issue(
    val id: Id,
    @SerialName("pull_request")
    val pr: PullRequestLink? = null,
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
enum class CommentAction {
    @SerialName("created")
    CREATED,

    @SerialName("edited")
    EDITED,

    @SerialName("deleted")
    DELETED,
}

@Serializable
data class ChangeMap(
    val from: String
)

@Serializable
data class CommentChanges(
    val body: ChangeMap? = null
)

@Serializable
data class IssueCommentEvent(
    val action: CommentAction,
    val comment: Comment,
    val issue: Issue,
    val repository: Repository,
    val installation: Installation,
    val changes: CommentChanges? = null,
)

val client = HttpClient(CIO) {
    defaultRequest {
        url("https://api.github.com/")
        userAgent("https://github.com/PssbleTrngle/SpotlessApp")
        header("X-GitHub-Api-Version", "2022-11-28")
        contentType(ContentType.Application.Json)
        accept(ContentType.parse("application/vnd.github+json"))
    }
    install(ContentNegotiation) {
        json(createJson())
    }
}

private val applicationName = "spotless-bot[bot]"

private suspend fun githubUser(token: String): GitUser {
    val user = client.get("users/${applicationName}").body<User>()
    val email = "${user.id}+$applicationName@users.noreply.github.com"
    return GitUser(email, applicationName, token)
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

private fun IssueCommentEvent.shouldRun(): Boolean {
    if (action == CommentAction.DELETED) return false
    val matchesCommand = comment.body.trim() == "/spotless"
    if (action == CommentAction.CREATED) return matchesCommand
    return matchesCommand && changes?.body?.from != comment.body
}

suspend fun Application.handleComment(event: IssueCommentEvent) {
    if (event.issue.pr == null) return
    log.debug("Received PR comment '${event.comment.body}' on ${event.issue.pr.url}")

    if (!event.shouldRun()) return log.debug("not a command")
    if (event.issue.state != IssueState.OPEN) return log.debug("already closed")
    if (!event.comment.authorAssociation.canRunCommand) return log.debug("user not allowed to run command")

    val jwt = generateJWT()

    val (token) = client.post("app/installations/${event.installation.id}/access_tokens") {
        bearerAuth(jwt)
    }.body<TokenResponse>()

    val pullRequest = client.get(event.issue.pr.url) {
        bearerAuth(token)
    }.body<PullRequest>()

    if (!pullRequest.maintainerCanModify && pullRequest.head.repo.id != pullRequest.base.repo.id) {
        forbidden("pull request owner does not allow modifications by maintainers")
    }

    val user = githubUser(token)
    react(event.comment, event.repository, user, Emoji.EYES)

    launch(Dispatchers.IO) {
        val reference = "${event.repository.name} #${pullRequest.number}"
        try {
            val committed = spotlessApply(pullRequest.head, user)
            if (committed) {
                react(event.comment, event.repository, user, Emoji.THUMBS_UP)
                log.info("done for $reference")
            } else {
                react(event.comment, event.repository, user, Emoji.THUMBS_DOWN)
                log.info("no changes for $reference")
            }
        } catch (ex: Exception) {
            log.error("exception occured while handling $reference", ex)
            react(event.comment, event.repository, user, Emoji.CONFUSED)
        }
    }
}

@Serializable
enum class Emoji {
    @SerialName("+1")
    THUMBS_UP,

    @SerialName("-1")
    THUMBS_DOWN,

    @SerialName("hooray")
    HOORAY,

    @SerialName("eyes")
    EYES,

    @SerialName("confused")
    CONFUSED,
}

@Serializable
data class AddReaction(
    val content: Emoji
)

@Serializable
data class Reaction(
    val id: Id,
    val user: User,
)

suspend fun react(comment: Comment, repository: Repository, user: GitUser, reaction: Emoji) {
    val existing = client.get("/repos/${repository.name}/issues/comments/${comment.id}/reactions") {
        bearerAuth(user.token)
    }.body<List<Reaction>>().filter {
        it.user.login == applicationName
    }

    existing.asFlow().onEach {
        client.delete("/repos/${repository.name}/issues/comments/${comment.id}/reactions/${it.id}") {
            bearerAuth(user.token)
        }
    }.collect()

    client.post("/repos/${repository.name}/issues/comments/${comment.id}/reactions") {
        bearerAuth(user.token)
        setBody(AddReaction(reaction))
    }
}