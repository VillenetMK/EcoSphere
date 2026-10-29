package com.example.ecosphere.desktop

import java.util.Properties

internal object DesktopBuild {
    val version: String = Properties().apply {
        checkNotNull(DesktopBuild::class.java.getResourceAsStream("/ecosphere-version.properties")) {
            "Missing desktop version resource"
        }.use { load(it) }
    }.getProperty("version")
}
