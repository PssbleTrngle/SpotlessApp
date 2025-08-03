package com.possible_triangle.spotless

import com.lordcodes.turtle.ProcessCallbacks
import com.lordcodes.turtle.shellRun
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.config.property
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.PrintStream

private val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")

private val Application.timeout get() = property<Long>("gradle.timeout_millis")

suspend fun Application.runGradle(directory: File) = withTimeout(timeout) {
    try {
        val output = directory.parentFile.resolve("gradle.log")

        shellRun {
            changeWorkingDirectory(directory)
            val executable = if (isWindows) "gradlew.bat" else "./gradlew"
            command(executable, listOf("spotlessApply"), object : ProcessCallbacks {
                override fun onProcessStart(process: Process) {
                    process.inputStream.transferTo(PrintStream(output))
                }
            })
        }
    } catch (ex: Exception) {
        throw IllegalStateException("exception running gradle", ex)
    }
}