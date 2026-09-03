package com.samfreeze.app.root

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Registered in the manifest but intentionally inert in this MVP build.
 * Freeze Manager does not run a background service or apply anything on
 * boot in v1 — "apply profile after boot" is a placeholder for a future
 * release (see item 18/45 of the spec) and is left unimplemented here
 * rather than half-wired, since profiles themselves aren't in this MVP.
 * No action is taken; this exists only so the manifest entry and future
 * wiring point are already in place.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d("SamFreeze", "Boot completed — boot automation not enabled in this build")
        // Intentionally no-op. Future: read a "boot profile enabled" preference
        // and, if set, apply the selected FreezeProfile via PackageRepository.
    }
}
