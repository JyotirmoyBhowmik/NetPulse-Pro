package com.aistudio.netpulse.qpzwtr.tile

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.aistudio.netpulse.qpzwtr.MainActivity
import com.aistudio.netpulse.qpzwtr.data.AppDatabase
import com.aistudio.netpulse.qpzwtr.data.NetworkLog
import com.aistudio.netpulse.qpzwtr.network.NetworkClient
import kotlinx.coroutines.*

class NetworkTileService : TileService() {

    private val tileScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        
        tileScope.launch {
            try {
                // Fetch latest log entry
                val db = AppDatabase.getDatabase(applicationContext)
                var lastLog: NetworkLog? = null
                
                withContext(Dispatchers.IO) {
                    // Fetch latest log safely
                    db.networkDao().getAllLogs().collect { list ->
                        lastLog = list.firstOrNull()
                        throw CancellationException() // quick pull
                    }
                }
                
                lastLog?.let { log ->
                    tile.label = log.ssid
                    tile.subtitle = "${log.rssiDbm} dBm | ${log.linkSpeedMbps}M"
                    tile.state = Tile.STATE_ACTIVE
                } ?: run {
                    tile.label = "NetPulse Pro"
                    tile.subtitle = "Tap to test"
                    tile.state = Tile.STATE_INACTIVE
                }
            } catch (ce: CancellationException) {
                // Normal exit
            } catch (e: Exception) {
                Log.e("NetworkTileService", "Error reading tile status: ${e.message}")
                tile.label = "NetPulse Pro"
                tile.state = Tile.STATE_ACTIVE
            }
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return

        // Instant background diagnostic sweep
        tile.label = "Testing..."
        tile.subtitle = "Connecting to Core..."
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()

        tileScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    NetworkClient.runDiagnosticTest("Periodic") { _, _ -> }
                }
                tile.label = "Speed Sweep Complete"
                tile.subtitle = "DL: ${String.format("%.1f", result.downloadMbps)} Mbps"
                tile.updateTile()
                
                // Fire MainActivity
                val intent = Intent(applicationContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    // Modern Android 14+ tile click requirements
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
            } catch (e: Exception) {
                Log.e("NetworkTileService", "Tile manual speed test failed: ${e.message}")
                tile.label = "Test Failed"
                tile.subtitle = "Check link"
                tile.updateTile()
            }
            
            delay(4000)
            onStartListening() // restore default ssid look
        }
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }
}
