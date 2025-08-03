package com.possible_triangle.spotless

import io.ktor.server.application.*
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant

private val CLONE_DIR = File("data/clones")

private val JSON = createJson()

@Serializable
data class Metadata(
    val sha: String,
    @Contextual
    val clonedAt: Instant
)

suspend fun Application.spotlessApply(head: Head, user: GitUser): Boolean = coroutineScope {
    val dir = CLONE_DIR.resolve(head.repo.name).resolve(head.ref)
    val destination = dir.resolve("repository")
    val metaFile = dir.resolve("meta.json")

    if (dir.exists()) {
        val metadata = try {
            JSON.decodeFromString<Metadata>(metaFile.readText())
        } catch (_: Exception) {
            dir.deleteRecursively()
            null
        }

        metadata?.let {
            if (it.sha != head.sha) error("already checked out with ${it.sha}")
            else error("already running for this commit")
        }
    }


    dir.mkdirs()
    metaFile.writeText(
        JSON.encodeToString(
            Metadata(
                sha = head.sha,
                clonedAt = Instant.now()
            )
        )
    )

    val result = runCatching {
        log.info("cloning into ${head.repo.cloneUrl}, branch ${head.ref}")
        head.clone( destination, user)

        log.info("running spotlessApply")
        runGradle(destination)

        log.info("committing and pushing changes")
        commitAndPush(destination, user)
    }

    dir.deleteRecursively()
    result.getOrThrow()
}

fun Application.cleanup() {
    if (CLONE_DIR.list() == null || CLONE_DIR.list().isEmpty()) return;

    log.warn("deleting headless data")

    CLONE_DIR.deleteRecursively()
}