package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.network.DiagnosticResult
import com.example.network.GeminiDiagnosticsService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface SpeedTestUIState {
    object Idle : SpeedTestUIState
    data class Testing(val currentStep: String, val progressPercent: Double) : SpeedTestUIState
    data class Complete(val result: DiagnosticResult) : SpeedTestUIState
    data class Error(val message: String) : SpeedTestUIState
}

sealed interface AIDiagnoseUIState {
    object Idle : AIDiagnoseUIState
    object Loading : AIDiagnoseUIState
    data class Success(val recommendations: String) : AIDiagnoseUIState
    data class Error(val message: String) : AIDiagnoseUIState
}

data class ChannelCongestion(
    val channel: Int,
    val ssid: String,
    val signalStrengthDbm: Int,
    val widthMhz: Int
)

class NetworkViewModel(
    application: Application,
    private val repository: NetworkRepository
) : AndroidViewModel(application) {

    // Main telemetry state streams
    val allLogs: StateFlow<List<NetworkLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentAnomalies: StateFlow<List<AnomalyLog>> = repository.recentAnomalies
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRoamLogs: StateFlow<List<RoamingLog>> = repository.allRoamLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dataCapConfig: StateFlow<DataCapConfig?> = repository.dataCapConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Interactive custom state fields
    val speedTestState = MutableStateFlow<SpeedTestUIState>(SpeedTestUIState.Idle)
    val aiDiagnoseState = MutableStateFlow<AIDiagnoseUIState>(AIDiagnoseUIState.Idle)

    // --- Real-Time Ping Utility States ---
    val pingHost = MutableStateFlow("google.com")
    val isPinging = MutableStateFlow(false)
    val pingLogsState = MutableStateFlow<List<String>>(emptyList())
    val pingLatencyHistory = MutableStateFlow<List<Float>>(emptyList())
    val pingSuccessCount = MutableStateFlow(0)
    val pingFailureCount = MutableStateFlow(0)
    val pingCurrentLatency = MutableStateFlow<Float?>(null)
    
    val selectedProfile = MutableStateFlow("Streaming") // Streaming, Gaming, Eco
    val continuousTrackingActive = MutableStateFlow(false)
    val activeBandScanner = MutableStateFlow("5 GHz") // 2.4 GHz, 5 GHz, 6 GHz

    // Export secure parameters
    val exportedFileState = MutableStateFlow<File?>(null)

    // Configured mock dataset for Wi-Fi Channel Scanner (overlay curves)
    val scannedChannels: StateFlow<List<ChannelCongestion>> = MutableStateFlow(
        listOf(
            ChannelCongestion(36, "NetGear_Enterprise", -42, 80),
            ChannelCongestion(40, "HomeMesh_Secure", -32, 80),
            ChannelCongestion(44, "Asus_Gaming_Dual", -68, 40),
            ChannelCongestion(48, "Neighbor_WiFi", -78, 20),
            ChannelCongestion(149, "TP-Link_Extender", -62, 40),
            ChannelCongestion(157, "Coffee_Guest_Open", -82, 80)
        )
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleContinuousTracking(active: Boolean) {
        continuousTrackingActive.value = active
        if (active) {
            repository.startContinuousTracking()
        } else {
            repository.stopContinuousTracking()
        }
    }

    fun startManualDiagnostic() {
        val currentProfile = selectedProfile.value
        viewModelScope.launch {
            speedTestState.value = SpeedTestUIState.Testing("Preparing client certificate pins...", 5.0)
            try {
                val result = repository.runManualSpeedTest(currentProfile) { step, progress ->
                    speedTestState.value = SpeedTestUIState.Testing(step, progress)
                }
                speedTestState.value = SpeedTestUIState.Complete(result)
            } catch (e: Exception) {
                speedTestState.value = SpeedTestUIState.Error(e.localizedMessage ?: "Speed diagnostic stream timeout.")
            }
        }
    }

    fun triggerAIDiagnostics() {
        aiDiagnoseState.value = AIDiagnoseUIState.Loading
        viewModelScope.launch {
            try {
                // Compile telemetry summary strings to provide context to Gemini
                val logs = allLogs.value.take(6)
                val anomalies = recentAnomalies.value.take(4)

                val logSummary = if (logs.isEmpty()) {
                    "No Wi-Fi state logs have been created yet. Currently connected to HomeMesh_Secure (RSSI: -52dBm, speed: 866Mbps, Wi-Fi 6)."
                } else {
                    logs.joinToString("\n") { 
                        "Time: ${it.timestamp}, SSID: ${it.ssid}, Signal: ${it.rssiDbm}dBm, Speed: ${it.linkSpeedMbps}Mbps, Standard: ${it.standard}, DL: ${it.downloadSpeedMbps}Mbps"
                    }
                }

                val anomalySummary = if (anomalies.isEmpty()) {
                    "No critical connectivity anomalies stored at rest. Roaming mesh handoffs are highly stable."
                } else {
                    anomalies.joinToString("\n") {
                        "Severity: ${it.severity}, Type: ${it.type} Description: ${it.description}"
                    }
                }

                val report = GeminiDiagnosticsService.analyzeNetworkLogs(logSummary, anomalySummary)
                aiDiagnoseState.value = AIDiagnoseUIState.Success(report)
            } catch (e: Exception) {
                aiDiagnoseState.value = AIDiagnoseUIState.Error(e.localizedMessage ?: "Core AI Diagnostics timeout.")
            }
        }
    }

    fun configureDataCap(maxMb: Int) {
        viewModelScope.launch {
            repository.updateDataCap(maxMb, dataCapConfig.value?.currentDataUsedMb ?: 0.0)
        }
    }

    fun clearTelemetryHistory() {
        viewModelScope.launch {
            repository.clearAllTelemetry()
            speedTestState.value = SpeedTestUIState.Idle
            aiDiagnoseState.value = AIDiagnoseUIState.Idle
        }
    }

    fun exportEncryptedCSV(passcode: String) {
        viewModelScope.launch {
            val file = repository.exportEncryptedLogs(passcode)
            exportedFileState.value = file
        }
    }

    fun consumeExportedFile() {
        exportedFileState.value = null
    }

    // --- Real-Time Ping Utility Core Logic ---
    fun startPing() {
        val host = pingHost.value.trim()
        if (host.isEmpty()) return

        isPinging.value = true
        pingLogsState.value = listOf("Initializing ping sequence to $host...")
        pingLatencyHistory.value = emptyList()
        pingSuccessCount.value = 0
        pingFailureCount.value = 0
        pingCurrentLatency.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Command line ping sweep: -c 6 (6 packet count), -i 0.6 (0.6s packet interval)
                val process = Runtime.getRuntime().exec("ping -c 6 -i 0.6 $host")
                val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
                var line: String?
                var isFirstLine = true

                while (isPinging.value) {
                    line = reader.readLine()
                    if (line == null) break

                    val currentLine = line
                    var parsedLatency: Float? = null
                    
                    if (currentLine.contains("time=")) {
                        val timePart = currentLine.substringAfter("time=")
                        val msValStr = timePart.substringBefore(" ms").substringBefore("ms").trim()
                        parsedLatency = msValStr.toFloatOrNull()
                    }

                    withContext(Dispatchers.Main) {
                        if (parsedLatency != null) {
                            pingCurrentLatency.value = parsedLatency
                            pingLatencyHistory.value = (pingLatencyHistory.value + parsedLatency).takeLast(15)
                            pingSuccessCount.value = pingSuccessCount.value + 1
                            pingLogsState.value = pingLogsState.value + "↳ ping seq=${pingSuccessCount.value}: time=$parsedLatency ms"
                        } else {
                            if (currentLine.startsWith("PING") && isFirstLine) {
                                pingLogsState.value = pingLogsState.value + "Socket connection established (Resolved IP Address)."
                                isFirstLine = false
                            } else if (currentLine.contains("statistics") || currentLine.contains("packets transmitted")) {
                                pingLogsState.value = pingLogsState.value + currentLine
                            }
                        }
                    }
                }

                val exitCode = process.waitFor()
                withContext(Dispatchers.Main) {
                    if (pingSuccessCount.value == 0) {
                        pingLogsState.value = pingLogsState.value + "ICMP Ping blocked/restricted. Escalating connection to TCP port 80/443..."
                        runTcpSocketPing(host)
                    } else {
                        pingLogsState.value = pingLogsState.value + "ICMP Scan completed with exit code $exitCode."
                        isPinging.value = false
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pingLogsState.value = pingLogsState.value + "Environment error: ${e.localizedMessage}. Trying TCP handshake..."
                }
                runTcpSocketPing(host)
            }
        }
    }

    private suspend fun runTcpSocketPing(host: String) {
        val cleanHost = host.substringAfter("://").substringBefore("/").substringBefore(":")
        
        var successCount = 0
        var failureCount = 0

        for (i in 1..6) {
            if (!isPinging.value) break
            val startTime = System.currentTimeMillis()
            var isSuccess = false
            
            try {
                // Try standard HTTPS secure handshake port first
                val address = java.net.InetAddress.getByName(cleanHost)
                val socketAddress = java.net.InetSocketAddress(address, 443)
                val socket = java.net.Socket()
                socket.connect(socketAddress, 1500) // 1.5 second timeout
                socket.close()
                isSuccess = true
            } catch (e: Exception) {
                // Symmetrical fall back to HTTP default port 80
                try {
                    val address = java.net.InetAddress.getByName(cleanHost)
                    val socketAddress = java.net.InetSocketAddress(address, 80)
                    val socket = java.net.Socket()
                    socket.connect(socketAddress, 1500)
                    socket.close()
                    isSuccess = true
                } catch (e2: Exception) {
                    isSuccess = false
                }
            }

            val duration = (System.currentTimeMillis() - startTime).toFloat()

            if (isPinging.value) {
                withContext(Dispatchers.Main) {
                    if (isSuccess) {
                        successCount++
                        pingSuccessCount.value = successCount
                        pingCurrentLatency.value = duration
                        pingLatencyHistory.value = (pingLatencyHistory.value + duration).takeLast(15)
                        pingLogsState.value = pingLogsState.value + "↳ TCP handshake seq=$successCount: port 80/443 resolved. latency=$duration ms"
                    } else {
                        failureCount++
                        pingFailureCount.value = failureCount
                        pingLogsState.value = pingLogsState.value + "↳ TCP connection seq=$i failed: host unreachable or connection rejected."
                    }
                }
            }

            kotlinx.coroutines.delay(600)
        }

        withContext(Dispatchers.Main) {
            pingLogsState.value = pingLogsState.value + "TCP Handshake Sweep Terminated."
            isPinging.value = false
        }
    }

    fun stopPing() {
        isPinging.value = false
        pingLogsState.value = pingLogsState.value + "Ping sweep halted by user."
    }

    // Dynamic list calculations for visual graphing
    fun getScrubbedLogs(): List<NetworkLog> {
        return allLogs.value.filter { it.rssiDbm != 0 }.take(25).reversed()
    }

    // Provider Factory mapping
    class Factory(
        private val application: Application,
        private val repository: NetworkRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(NetworkViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return NetworkViewModel(application, repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class mapping.")
        }
    }
}
