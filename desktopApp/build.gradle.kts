plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.compose") version "1.11.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10"
}

group = "com.example.ecosphere"
version = "1.4.0"

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
            packageVersion = "1.4.0"
            description = "EcoSphere - Sistema inteligente de microclima"
            vendor = "EcoSphere"
            modules("java.net.http")
        }
    }
}
