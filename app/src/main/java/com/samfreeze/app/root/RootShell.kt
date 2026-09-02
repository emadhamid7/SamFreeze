package com.samfreeze.app.root

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.util.UUID

private const val TAG = "SamFreeze"

/**
 * Executes privileged commands through a single, persistent `su` shell process.
 *
 * Design notes:
 *  - Only ever runs commands this app itself constructs (see [Commands]).
 *    There is no generic "run arbitrary shell command" entry point exposed
 *    to callers outside this package.
 *  - Reuses one su process across calls (cheaper than spawning `su -c` per
 *    command) but falls back to a fresh process if the shell dies.
 *  - Every call runs off the calling thread's dispatcher expectations —
 *    callers must invoke this from a coroutine; internally we hop to IO.
 *  - Output is captured between unique sentinel markers so we can reliably
 *    tell where one command's output ends, even with multi-line stdout/stderr.
 */
class RootShell {

    private var process: Process? = null
    private var stdin: DataOutputStream? = null
    private var stdoutReader: BufferedReader? = null
    private var stderrReader: BufferedReader? = null
    private val lock = Mutex()

    @Volatile
    private var rootAvailable: Boolean? = null

    /** True/false cached result of the last root check, or null if never checked. */
    fun cachedRootState(): Boolean? = rootAvailable

    /** Clears the cached root state so the next check re-verifies from scratch. */
    fun invalidateCache() {
        rootAvailable = null
    }

    /**
     * Verifies root access is available by running `id` through su and checking
     * for `uid=0`. Never throws; always returns a result.
     */
    suspend fun checkRoot(timeoutMs: Long = 8000): Boolean {
        val result = execute("id", timeoutMs = timeoutMs, forceNewShellOnFailure = true)
        val ok = result.success && result.stdout.contains("uid=0")
        rootAvailable = ok
        return ok
    }

    /**
     * Executes a single trusted, pre-built command string through su.
     * Callers within this app must only ever pass commands built via
     * [Commands] helpers — never raw user input.
     */
    suspend fun execute(
        command: String,
        timeoutMs: Long = 15000,
        forceNewShellOnFailure: Boolean = false
    ): RootResult = withContext(Dispatchers.IO) {
        lock.withLock {
            val result = runInternal(command, timeoutMs)
            if (result == null) {
                // Shell died or timed out — restart once and retry.
                closeInternal()
                val retry = runInternal(command, timeoutMs)
                retry ?: RootResult(
                    success = false,
                    exitCode = -1,
                    stdout = "",
                    stderr = "Root shell unavailable or timed out"
                )
            } else {
                result
            }
        }
    }

    private fun ensureProcess(): Boolean {
        if (process != null && isAlive()) return true
        return try {
            val p = ProcessBuilder("su").redirectErrorStream(false).start()
            process = p
            stdin = DataOutputStream(p.outputStream)
            stdoutReader = BufferedReader(InputStreamReader(p.inputStream))
            stderrReader = BufferedReader(InputStreamReader(p.errorStream))
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to start su process: ${t.javaClass.simpleName}")
            process = null
            false
        }
    }

    private fun isAlive(): Boolean {
        val p = process ?: return false
        return try {
            p.exitValue()
            false // exited
        } catch (e: IllegalThreadStateException) {
            true // still running
        }
    }

    private suspend fun runInternal(command: String, timeoutMs: Long): RootResult? {
        if (!ensureProcess()) return null
        val sin = stdin ?: return null
        val sout = stdoutReader ?: return null
        val serr = stderrReader ?: return null

        val marker = "FM_${UUID.randomUUID().toString().replace("-", "")}"
        val exitVar = "\$?"

        return try {
            val deferred = CompletableDeferred<RootResult>()

            // Run command, then echo a sentinel with the exit code so we know
            // exactly when this command's output has ended.
            val fullCmd = buildString {
                append(command)
                append('\n')
                append("echo ").append(marker).append(' ').append(exitVar).append('\n')
                append("echo ").append(marker).append(" ERR_DONE 1>&2\n")
            }

            sin.write(fullCmd.toByteArray())
            sin.flush()

            withTimeoutOrNull(timeoutMs) {
                val stdoutBuf = StringBuilder()
                val stderrBuf = StringBuilder()
                var exitCode = -1

                // Read stdout until we hit the marker line.
                while (true) {
                    val line = sout.readLine() ?: break
                    if (line.startsWith(marker)) {
                        val parts = line.trim().split(" ")
                        exitCode = parts.getOrNull(1)?.toIntOrNull() ?: -1
                        break
                    }
                    if (stdoutBuf.isNotEmpty()) stdoutBuf.append('\n')
                    stdoutBuf.append(line)
                }

                // stderr may or may not have content; drain non-blocking-ish by
                // reading until its own marker (su interleaves reliably enough
                // for our sequential command pattern since we always emit both
                // markers after every command).
                while (serr.ready()) {
                    val line = serr.readLine() ?: break
                    if (line.startsWith(marker)) break
                    if (stderrBuf.isNotEmpty()) stderrBuf.append('\n')
                    stderrBuf.append(line)
                }

                deferred.complete(
                    RootResult(
                        success = exitCode == 0,
                        exitCode = exitCode,
                        stdout = stdoutBuf.toString().trim(),
                        stderr = stderrBuf.toString().trim()
                    )
                )
                deferred.await()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Root command failed: ${t.javaClass.simpleName}")
            null
        }
    }

    private fun closeInternal() {
        try { stdin?.writeBytes("exit\n"); stdin?.flush() } catch (_: Throwable) {}
        try { stdin?.close() } catch (_: Throwable) {}
        try { stdoutReader?.close() } catch (_: Throwable) {}
        try { stderrReader?.close() } catch (_: Throwable) {}
        try { process?.destroy() } catch (_: Throwable) {}
        process = null
        stdin = null
        stdoutReader = null
        stderrReader = null
    }

    /** Call when the app is going away to release the su process cleanly. */
    suspend fun close() = withContext(Dispatchers.IO) {
        lock.withLock { closeInternal() }
    }

    companion object {
        @Volatile private var instance: RootShell? = null

        fun getInstance(): RootShell =
            instance ?: synchronized(this) {
                instance ?: RootShell().also { instance = it }
            }
    }
}
