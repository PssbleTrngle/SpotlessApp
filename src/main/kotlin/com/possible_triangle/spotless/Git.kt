package com.possible_triangle.spotless

import com.lordcodes.turtle.ShellRunException
import com.lordcodes.turtle.ShellScript
import com.lordcodes.turtle.shellRun
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.server.engine.applicationEnvironment
import java.io.File
import java.lang.RuntimeException

data class GitUser(
    val email: String,
    val name: String,
    val token: String,
)

fun Head.clone(repository: Repository, destination: File, user: GitUser) {
    try {
        val cloneUrl = Url(URLBuilder(repository.cloneUrl).apply {
            this.user = user.name
            this.password = user.token
        })

        shellRun(
            "git", listOf(
                "clone",
                "-b",
                ref,
                "--depth",
                "1",
                cloneUrl.toString(),
                destination.absolutePath
            )
        )

    } catch (ex: Exception) {
        throw IllegalStateException("exception while cloning", ex)
    }
}

fun ShellScript.gitConfig(key: String, value: String) {
    command("git", listOf("config", "--local", key, value))

}

class UnchangedException() : RuntimeException("no git changes found")

fun commitAndPush(directory: File, user: GitUser): Boolean {
    try {
        shellRun {
            changeWorkingDirectory(directory)

            gitConfig("user.name", user.name)
            gitConfig("user.email", user.email)
            gitConfig("commit.gpgsign", "false")

            val status = command("git", listOf("status", "--porcelain")).trim()
            if (status.isEmpty()) throw UnchangedException()

            git.addAll()
            command("git", listOf("commit", "-m", "run spotless", "--no-verify"))

            git.push()

        }

        return true
    } catch (_: UnchangedException) {
        return false
    } catch (ex: Exception) {
        throw IllegalStateException("exception while commiting & pushing", ex)
    }
}