plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.plugin.serialization)
    alias(libs.plugins.spotless)
    idea
}

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.netty)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.logback.classic)
    implementation(libs.lordcodes.turtle)
    implementation(libs.auth0.jwt)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
}

idea {
    module {
        excludeDirs.add(file("data"))
    }
}

spotless {
    kotlin {
        ktlint()

        leadingTabsToSpaces()

        suppressLintsFor { shortCode = "standard:package-name" }
    }

    kotlinGradle {
        ktlint()
    }
}