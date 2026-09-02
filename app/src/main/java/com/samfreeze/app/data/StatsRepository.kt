package com.samfreeze.app.data

import com.samfreeze.app.root.Commands
import com.samfreeze.app.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Optional, on-demand stats: which packages are currently running, their
 * on-disk size, and the filesystem type backing /system. None of this is
 * computed automatically for every app — it's fetched lazily (e.g. when
 * the user picks "sort by size") since each costs a real root round trip.
 */
class StatsRepository(private val rootShell: RootShell = RootShell.getInstance()) {

    /** Returns the set of package names that currently have a running process. */
    suspend fun runningPackages(): Set<String> = withContext(Dispatchers.IO) {
        val result = rootShell.execute(Commands.listRunningProcesses())
        if (!result.success) return@withContext emptySet()
        result.stdout.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it.contains('.') } // process names look like package names
            .toSet()
    }

    /**
     * Approximate on-disk size (data + APK, in KB) for a batch of packages
     * in one root round trip. Missing/failed entries are simply absent
     * from the result map rather than reported as zero.
     */
    suspend fun dataSizesKb(packages: List<String>): Map<String, Long> = withContext(Dispatchers.IO) {
        if (packages.isEmpty()) return@withContext emptyMap()
        val script = Commands.batchDataSizes(packages)
        val result = rootShell.execute(script, timeoutMs = 30000)
        if (!result.success) return@withContext emptyMap()

        val sizes = mutableMapOf<String, Long>()
        result.stdout.lineSequence().forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size >= 2) {
                val pkg = parts[0]
                val kb = parts[1].toLongOrNull()
                if (kb != null) sizes[pkg] = kb
            }
        }
        sizes
    }
}
