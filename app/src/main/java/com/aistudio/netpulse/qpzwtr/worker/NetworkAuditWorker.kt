package com.aistudio.netpulse.qpzwtr.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aistudio.netpulse.qpzwtr.data.AnomalyLog
import com.aistudio.netpulse.qpzwtr.data.AppDatabase
import com.aistudio.netpulse.qpzwtr.data.NetworkLog
import com.aistudio.netpulse.qpzwtr.network.NetworkClient
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.CancellationException

class NetworkAuditWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i("NetworkAuditWorker", "Starting automated periodic speed test...")
        val db = AppDatabase.getDatabase(applicationContext)

        try {
            // Check monthly data budget caps before initiating a background speed test
            val dao = db.networkDao()
            var budgetLimitMb = 500
            var currentUsedMb = 0.0

            // Query data cap
            val cap = dao.getDataCap().firstOrNull()
            if (cap != null) {
                budgetLimitMb = cap.maxBgDataMb
                currentUsedMb = cap.currentDataUsedMb
            }

            if (currentUsedMb >= budgetLimitMb) {
                Log.w("NetworkAuditWorker", "Automated speed test skipped. Background data budget cap reached: $currentUsedMb MB")
                return Result.success()
            }

            // Run speed diagnostic against our pinned server (Default target)
            val result = NetworkClient.runDiagnosticTest("Periodic") { phrase, percent ->
                Log.d("NetworkAuditWorker", "$phrase ($percent%)")
            }

            // High priority alerts if metrics exceed warnings thresholds
            if (result.latencyMs > 200.0) {
                dao.insertAnomaly(
                    AnomalyLog(
                        type = "LATENCY_SPIKE",
                        description = "Automated test report: Latency exceeded warning threshold. Realtime value: ${result.latencyMs} ms.",
                        severity = "HIGH"
                    )
                )
            }

            if (result.jitterMs > 25.0) {
                dao.insertAnomaly(
                    AnomalyLog(
                        type = "CONGESTION",
                        description = "Severe network jitter detected by background worker: ${result.jitterMs} ms. Connection unstable.",
                        severity = "MEDIUM"
                    )
                )
            }

            // Log network result
            val logItem = NetworkLog(
                ssid = "HomeMesh_Secure", // background task fallback standard
                bssid = "00:1A:11:F2:B3:91",
                rssiDbm = -55,
                linkSpeedMbps = 866,
                standard = "Wi-Fi 6 (802.11ax)",
                frequencyGhz = 5.2,
                downloadSpeedMbps = result.downloadMbps,
                uploadSpeedMbps = result.uploadMbps,
                latencyMs = result.latencyMs,
                jitterMs = result.jitterMs,
                isAnomaly = result.latencyMs > 200.0,
                isManual = false,
                gatewayIp = result.gatewayIp,
                publicIp = result.publicIp,
                ispName = result.ispName
            )
            dao.insertLog(logItem)

            // Update background data cap allocation (~5MB consumed per full download/upload loop)
            val updatedCap = com.aistudio.netpulse.qpzwtr.data.DataCapConfig(
                maxBgDataMb = budgetLimitMb,
                currentDataUsedMb = currentUsedMb + 5.0,
                lastResetTime = System.currentTimeMillis()
            )
            dao.insertOrUpdateDataCap(updatedCap)

            Log.i("NetworkAuditWorker", "Automated check successfully saved.")
            return Result.success()

        } catch (e: Exception) {
            Log.e("NetworkAuditWorker", "Automated background scan failed: ${e.message}", e)
            return Result.retry()
        }
    }
}

// Extra utility object to trigger flow collector helper cleanly
suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstOrNull(predicate: suspend (T) -> Boolean): T? {
    var result: T? = null
    try {
        collect { value ->
            if (predicate(value)) {
                result = value
                throw CancellationException()
            }
        }
    } catch (e: CancellationException) {
        // safe exit
    }
    return result
}
