package com.aistudio.netpulse.qpzwtr.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.aistudio.netpulse.qpzwtr.MainActivity
import com.aistudio.netpulse.qpzwtr.R
import com.aistudio.netpulse.qpzwtr.data.AppDatabase
import com.aistudio.netpulse.qpzwtr.data.NetworkLog
import kotlinx.coroutines.*

class NetPulseWidgetProvider : AppWidgetProvider() {

    private val widgetScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Run update safely in background dispatcher Thread
        widgetScope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                var lastLog: NetworkLog? = null
                
                // Fetch latest log safely
                db.networkDao().getAllLogs().collect { list ->
                    lastLog = list.firstOrNull()
                    throw CancellationException() // exit immediately once we have first emission
                }
                
                // Update widgets with the retrieved log
                for (id in appWidgetIds) {
                    updateWidgetUI(context, appWidgetManager, id, lastLog)
                }
            } catch (ce: CancellationException) {
                // Safe exit
            } catch (e: Exception) {
                Log.e("NetPulseWidget", "Widget DB update failed: ${e.message}")
            }
        }
    }

    private fun updateWidgetUI(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        log: NetworkLog?
    ) {
        val views = RemoteViews(context.packageName, R.layout.netpulse_widget_layout)

        // Bind data values
        if (log != null) {
            views.setTextViewText(R.id.widget_ssid, "SSID: ${log.ssid}")
            views.setTextViewText(R.id.widget_signal, "RSSI: ${log.rssiDbm} dBm (${log.linkSpeedMbps} Mbps)")
            
            if (log.downloadSpeedMbps > 0.0) {
                views.setTextViewText(R.id.widget_speed, "DL: ${String.format("%.1f", log.downloadSpeedMbps)} Mbps")
                views.setTextViewText(R.id.widget_ping, "Ping: ${String.format("%.0f", log.latencyMs)} ms | Std: ${log.standard.substringBefore(" (")}")
            } else {
                views.setTextViewText(R.id.widget_speed, "DL: untested")
                views.setTextViewText(R.id.widget_ping, "Standard: ${log.standard.substringBefore(" (")}")
            }
        } else {
            views.setTextViewText(R.id.widget_ssid, "SSID: not scanning")
            views.setTextViewText(R.id.widget_signal, "RSSI: unknown")
            views.setTextViewText(R.id.widget_speed, "DL: N/A")
            views.setTextViewText(R.id.widget_ping, "Tap to open Suite")
        }

        // Tap on widget opens NetPulse Pro Suite
        val clickIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            widgetId,
            clickIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        views.setOnClickPendingIntent(R.id.widget_title, pendingIntent)

        // Apply changes
        appWidgetManager.updateAppWidget(widgetId, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Check if DB changed, trigger self update
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisAppWidget = ComponentName(context.packageName, NetPulseWidgetProvider::class.java.name)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget)
            onUpdate(context, appWidgetManager, appWidgetIds)
        }
    }
}
