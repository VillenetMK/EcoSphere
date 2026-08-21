import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val generateEcoSphereLauncher by tasks.registering {
    val encodedIcon = layout.projectDirectory.file("src/main/ecosphere_launcher_full.webp.b64")
    val generatedResDir = layout.buildDirectory.dir("generated/ecosphereLauncher/res")
    val outputFile = layout.buildDirectory.file(
        "generated/ecosphereLauncher/res/drawable-nodpi/ecosphere_launcher_full.webp"
    )

    inputs.file(encodedIcon)
    outputs.file(outputFile)

    doLast {
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()

        val encoded = encodedIcon.asFile
            .readText()
            .filterNot { it.isWhitespace() }

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
        versionCode = 1
        versionName = "1.0"

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
        compose = true
    }

    sourceSets.getByName("main").res.srcDir(
        layout.buildDirectory.dir("generated/ecosphereLauncher/res")
    )
}

tasks.matching {
    it.name == "preBuild" || (it.name.startsWith("merge") && it.name.endsWith("Resources"))
}.configureEach {
    dependsOn(generateEcoSphereLauncher)
}

dependencies {
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)
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