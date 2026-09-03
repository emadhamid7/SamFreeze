package com.samfreeze.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import com.samfreeze.app.R
import com.samfreeze.app.SamFreezeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A single home-screen tile: tap it, and every package in the user's
 * Quick Stop list (Settings > Manage Quick Stop List) gets `am force-stop`'d
 * via root — no need to open the app. The widget itself has no persistent
 * process; each tap is a self-contained broadcast handled here.
 */
class QuickStopWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_FORCE_STOP = "com.samfreeze.app.widget.ACTION_FORCE_STOP"
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_FORCE_STOP) return

        // goAsync() keeps the receiver (and process) alive long enough for
        // the root round trip to finish — a plain onReceive would otherwise
        // get killed by the system before the coroutine below completes.
        val pendingResult = goAsync()
        val appCtx = context.applicationContext as SamFreezeApp

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ok = appCtx.rootShell.checkRoot()
                if (!ok) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, context.getString(R.string.widget_no_root), Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val targets = appCtx.preferencesRepository.quickStopList.first()
                var stopped = 0
                for (pkg in targets) {
                    if (appCtx.packageRepository.isHidden(pkg)) continue
                    if (appCtx.packageRepository.forceStop(pkg)) stopped++
                }

                withContext(Dispatchers.Main) {
                    val message = if (targets.isEmpty()) {
                        context.getString(R.string.widget_list_empty)
                    } else {
                        context.getString(R.string.widget_stopped_n, stopped)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_quick_stop)
        val tapIntent = Intent(context, QuickStopWidgetProvider::class.java).apply { action = ACTION_FORCE_STOP }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            id,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        manager.updateAppWidget(id, views)
    }
}
