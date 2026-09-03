/*
 * EcoSphere
 * Copyright (c) 2026 Gabriel Enrique Villenet Montero.
 * Todos los derechos reservados. Uso sujeto al archivo LICENSE.
 */

import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val generateEcoSphereLauncher by tasks.registering {
    val encodedIconParts = listOf(
        layout.projectDirectory.file("src/main/ecosphere_logo_exact_00.b64"),
        layout.projectDirectory.file("src/main/ecosphere_logo_exact_01.b64"),
        layout.projectDirectory.file("src/main/ecosphere_logo_exact_02.b64"),
        layout.projectDirectory.file("src/main/ecosphere_logo_exact_03.b64"),
        layout.projectDirectory.file("src/main/ecosphere_logo_exact_04.b64"),
        layout.projectDirectory.file("src/main/ecosphere_logo_exact_05.b64")
    )
    val outputFile = layout.buildDirectory.file(
        "generated/ecosphereLauncher/res/drawable-nodpi/ecosphere_launcher_full.webp"
    )

    inputs.files(encodedIconParts)
    outputs.file(outputFile)

    doLast {
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()

        val encoded = buildString {
            encodedIconParts.forEach { part ->
                append(part.asFile.readText().filterNot { it.isWhitespace() })
            }
        }

        target.writeBytes(Base64.getDecoder().decode(encoded))
    }
}

android {
    namespace = "com.example.ecosphere"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.ecosphere"
        minSdk = 24
        targetSdk = 36
        versionCode = 15
        versionName = "1.4.10"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets.getByName("main").res.srcDir(
        layout.buildDirectory.get().asFile.resolve("generated/ecosphereLauncher/res")
    )
}

tasks.matching {
    it.name == "preBuild" || (it.name.startsWith("merge") && it.name.endsWith("Resources"))
}.configureEach {
    dependsOn(generateEcoSphereLauncher)
}

dependencies {
    implementation(project(":sharedCore"))
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.android)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
