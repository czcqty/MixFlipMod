package com.parallelc.mixflipmod.ui.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val SU_TIMEOUT_SECONDS = 10L
private const val SYSTEM_FRAMEWORK_PACKAGE = "android"
private const val EXIT_OK = 0
private const val EXIT_NOT_RUNNING = 1
private const val EXIT_NO_ROOT = 126

suspend fun restartApp(packageName: String): RestartResult = withContext(Dispatchers.IO) {
    if (packageName == SYSTEM_FRAMEWORK_PACKAGE) {
        return@withContext if (runCatching { runSu("id -u >/dev/null 2>&1 || exit $EXIT_NO_ROOT; reboot") == EXIT_OK }.getOrDefault(false)) {
            RestartResult.Ok
        } else {
            RestartResult.Failed
        }
    }

    val pkg = packageName.shellSingleQuoted()
    val exitCode = runCatching {
        runSu(
            $$"""
            id -u >/dev/null 2>&1 || exit $$EXIT_NO_ROOT
            pkg=$$pkg
            pids=$(pidof "$pkg" 2>/dev/null)
            killed=0
            [ -z "$pids" ] && exit $$EXIT_NOT_RUNNING
            for pid in $pids; do
              kill -15 "$pid" 2>/dev/null && killed=1
            done
            [ "$killed" -eq 1 ] && exit $$EXIT_OK
            exit 2
            """.trimIndent()
        )
    }.getOrDefault(-1)

    when (exitCode) {
        EXIT_OK -> RestartResult.Ok
        EXIT_NOT_RUNNING -> RestartResult.NotRunning
        EXIT_NO_ROOT -> RestartResult.NoRoot
        else -> RestartResult.Failed
    }
}

enum class RestartResult { Ok, NotRunning, NoRoot, Failed }

private fun runSu(command: String): Int {
    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
    if (!process.waitFor(SU_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return -1
    }
    return process.exitValue()
}

private fun String.shellSingleQuoted(): String = "'${replace("'", "'\"'\"'")}'"
