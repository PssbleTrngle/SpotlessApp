package com.possible_triangle.spotless

import com.lordcodes.turtle.shellRun
import io.ktor.server.application.Application
import io.ktor.server.application.log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.io.File
import java.lang.Exception
import java.time.Instant

private val CLONE_DIR = File("data/clones")

private val JSON = createJson()

@Serializable
data class Metadata(
    val sha: String,
    @Contextual
    val clonedAt: Instant
)

suspend fun Application.spotlessApply(url: String, head: Head) = coroutineScope {
    val dir = CLONE_DIR.resolve(head.ref)
    val destination = dir.resolve("repository")
    val metaFile = dir.resolve("meta.json")

    if (dir.exists()) {
        val metadata = JSON.decodeFromString<Metadata>(metaFile.readText())
        error("already checked out with ${metadata.sha}")
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
        log.info("cloning into $url")
        head.clone(url, destination)

        log.info("running spotlessApply")
        runGradle(destination)

        log.info("committing and pushing changes")
        commitAndPush(destination)
    }

    dir.deleteRecursively()

    result.exceptionOrNull()?.let {
        log.error("exception occured while handling $url", it)
    }
}

private fun Head.clone(url: String, destination: File) {
    try {
        shellRun(
            "git", listOf(
                "clone",
                "-b",
                ref,
                "--depth",
                "1",
                url,
                destination.absolutePath
            )
        )

    } catch (ex: Exception) {
        throw IllegalStateException("exception while cloning", ex)
    }
}

private fun commitAndPush(directory: File) {
    shellRun(dryRun = true) {
        changeWorkingDirectory(directory)
        git.commitAllChanges("run spotless")
        git.push()
    }
}