package com.example.ecosphere.desktop

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.Instant

/** Deliberately excludes messages, URLs, request bodies, identities, tokens and MFA secrets. */
internal object DesktopDiagnostics {
    private val allowedOperations = setOf("initialize", "sign-in", "register", "oauth", "mfa", "sign-out")

    internal fun safeEntry(operation: String, error: Throwable): String = buildString {
        append(Instant.now()).append(" version=").append(DesktopBuild.version)
        append(" operation=").append(operation.takeIf { it in allowedOperations } ?: "unknown")
        generateSequence(error) { it.cause }.take(5).forEach { cause ->
            append(" type=").append(cause.javaClass.name)
            cause.stackTrace.filter { it.className.startsWith("com.example.ecosphere.") }.take(5).forEach {
                append(" at=").append(it.className).append('.').append(it.methodName).append(':').append(it.lineNumber)
            }
        }
        append('\n')
    }

    @Synchronized
    fun record(operation: String, error: Throwable) {
        runCatching {
            val directory = Path.of(System.getProperty("user.home"), ".ecosphere", "logs")
            Files.createDirectories(directory)
            val file = directory.resolve("desktop-auth.log")
            if (Files.exists(file) && Files.size(file) > 256 * 1024) {
                Files.move(file, directory.resolve("desktop-auth.previous.log"), REPLACE_EXISTING)
            }
            Files.writeString(file, safeEntry(operation, error), CREATE, APPEND)
        }
    }
}
