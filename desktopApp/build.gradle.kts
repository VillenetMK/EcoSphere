/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

import java.util.Properties

plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.compose") version "1.11.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10"
}

group = "com.example.ecosphere"
version = Properties().apply {
    file("src/main/resources/ecosphere-version.properties").inputStream().use { load(it) }
}.getProperty("version")

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        kotlin.srcDir("../sharedCore/src/main/kotlin")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("io.github.jan-tennert.supabase:auth-kt:3.7.0")
    implementation("io.github.jan-tennert.supabase:postgrest-kt:3.7.0")
    implementation("io.ktor:ktor-client-cio:3.5.1")
    testImplementation(kotlin("test-junit"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation(compose.desktop.uiTestJUnit4)
}

compose.desktop {
    application {
        mainClass = "com.example.ecosphere.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "EcoSphere"
            packageVersion = project.version.toString()
            description = "EcoSphere - Sistema inteligente de microclima"
            vendor = "EcoSphere"
            modules("java.net.http", "java.prefs", "jdk.httpserver")
            windows {
                iconFile.set(project.file("src/main/resources/brand/ecosphere.ico"))
                shortcut = true
                menu = true
            }
            linux {
                iconFile.set(project.file("src/main/resources/brand/ecosphere.png"))
            }
        }
    }
}
