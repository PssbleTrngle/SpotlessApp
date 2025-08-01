package com.possible_triangle.spotless

import com.lordcodes.turtle.shellRun
import java.io.File

private val isWindows = System.getProperty("os.name").lowercase().startsWith("windows")

fun runGradle(directory: File) {
    try {
        shellRun {
            changeWorkingDirectory(directory)
            val executable = if(isWindows) "gradlew.bat" else "gradlew"
            command(executable, listOf("spotlessApply"))
        }
    } catch (ex: Exception) {
        throw IllegalStateException("exception running gradle", ex)
    }
}