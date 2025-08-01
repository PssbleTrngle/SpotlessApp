package com.possible_triangle.spotless

import io.ktor.server.application.*

fun Application.module() {
    configureSerialization()
    configureRouting()
    configureStatusPages()
}
