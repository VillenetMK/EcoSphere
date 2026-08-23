import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.example.ecosphere"
version = "1.1.0"

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    api(libs.gson)
    testImplementation(libs.junit)
}

tasks.test {
    useJUnit()
}
