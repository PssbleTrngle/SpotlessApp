package com.possible_triangle.spotless

import io.ktor.server.application.*

fun Application.module() {
    cleanup()

    configureSerialization()
    configureRouting()
    configureStatusPages()
}
