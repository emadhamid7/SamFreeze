package com.samfreeze.app.root

/**
 * Result of a single root/shell command execution.
 */
data class RootResult(
    val success: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    /** Short, user-safe message suitable for a snackbar/dialog. */
    fun shortError(): String {
        val raw = stderr.ifBlank { stdout }
        val firstLine = raw.lineSequence().firstOrNull { it.isNotBlank() } ?: "Unknown error"
        return if (firstLine.length > 140) firstLine.take(140) + "…" else firstLine
    }
}
