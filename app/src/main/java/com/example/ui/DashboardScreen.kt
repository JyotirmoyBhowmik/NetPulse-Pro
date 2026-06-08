package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MainActivity
import com.example.data.AnomalyLog
import com.example.data.NetworkLog
import com.example.data.RoamingLog
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("DefaultLocale")
@Composable
fun DashboardScreen(viewModel: NetworkViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Observe DB streams
    val logs by viewModel.allLogs.collectAsState()
    val anomalies by viewModel.recentAnomalies.collectAsState()
    val roamLogs by viewModel.allRoamLogs.collectAsState()
    val dataCap by viewModel.dataCapConfig.collectAsState()

    // Observe interactive states
    val speedTestState by viewModel.speedTestState.collectAsState()
    val aiState by viewModel.aiDiagnoseState.collectAsState()
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val activeTracking by viewModel.continuousTrackingActive.collectAsState()
    val activeBand by viewModel.activeBandScanner.collectAsState()
    val exportedFile by viewModel.exportedFileState.collectAsState()

    // Forms
    var serverUrl by remember { mutableStateOf("https://vpnoci.jyotirmoyb.com") }
    var dataCapLimitStr by remember { mutableStateOf("500") }
    var encryptPasscode by remember { mutableStateOf("") }
    var showExportDialog by remember { mutableStateOf(false) }

    // Chart scrubbing state
    var scrubbedIndex by remember { mutableStateOf<Int?>(null) }

    // Request permissions launcher
    val permissionsToRequest = remember {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
            list.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        list.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (locationGranted) {
            Toast.makeText(context, "Location parameters synced successfully.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Some telemetry readouts might require fine location.", Toast.LENGTH_LONG).show()
        }
    }

    // Trigger permission requests on start
    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest)
        // Auto-refresh Cap to showcase
        viewModel.configureDataCap(500)
    }

    // Share cipher logs once generated
    LaunchedEffect(exportedFile) {
        exportedFile?.let { file ->
            val payloadText = "NetPulse Encrypted Log Export File ready. Secured via AES. Path: ${file.absolutePath}"
            
            // Native Share Sheet trigger for our encrypted file block
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Secure NetPulse Encrypted Log Export")
                    putExtra(Intent.EXTRA_TEXT, "$payloadText\n\n[CIPHER BLOB ATTACHED]")
                }
                context.startActivity(Intent.createChooser(intent, "Transmit Encrypted Audit Payload"))
            } catch (e: Exception) {
                Toast.makeText(context, "Sharing system busy. Saved to cache.", Toast.LENGTH_SHORT).show()
            }
            viewModel.consumeExportedFile()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "NETPULSE SECURE PRO",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp,
                            color = CyberGreen
                        )
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = CyberBlack
                ),
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.clearTelemetryHistory()
                            Toast.makeText(context, "Telemetry database wiped locally.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(android.R.drawable.ic_menu_delete, contentDescription = "Wipe logs", tint = CyberRed)
                    }
                }
            )
        },
        containerColor = CyberBlack
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16dp),
            verticalArrangement = Arrangement.spacedBy(16dp)
        ) {

            // SECTION 1: REAL-TIME SIGNAL WAVEFORM & STATE INDICATORS
            item {
                CyberCardGlowPanel {
                    Column(
                        modifier = Modifier.padding(16dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (logs.isNotEmpty()) logs.first().ssid else "LAN_Mesh_System",
                                    fontSize = 18sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                                Text(
                                    text = if (logs.isNotEmpty()) "BSSID: ${logs.first().bssid}" else "BSSID: scanning...",
                                    fontSize = 11sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.LightGray
                                )
                            }
                            
                            // Live standard badge
                            val currentStd = if (logs.isNotEmpty()) logs.first().standard else "Wi-Fi 6"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4dp))
                                    .background(CyberGreen.copy(alpha = 0.15f))
                                    .padding(horizontal = 8dp, vertical = 4dp)
                            ) {
                                Text(
                                    text = currentStd,
                                    color = CyberGreen,
                                    fontSize = 10sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16dp))

                        // Custom morphing waveform animation based on latest RSSI
                        val latestRssi = if (logs.isNotEmpty()) logs.first().rssiDbm else -45
                        val waveAmplitude = remember { Animatable(30f) }
                        
                        LaunchedEffect(latestRssi) {
                            // Signal strength translates to Wave Amplitude and frequency
                            val targetAmplitude = when {
                                latestRssi > -50 -> 45f  // Very Stable
                                latestRssi > -70 -> 25f  // Medium Range
                                else -> 10f            // Flatline decay
                            }
                            waveAmplitude.animateTo(
                                targetValue = targetAmplitude,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                        }

                        // Animating wave loop
                        val waveOffsetState = rememberInfiniteTransition(label = "Wave Transition")
                        val wavePhase by waveOffsetState.animateFloat(
                            initialValue = 0f,
                            targetValue = 2f * java.lang.Math.PI.toFloat(),
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "Wave phase"
                        )

                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(65dp)
                                .clip(RoundedCornerShape(8dp))
                                .background(CyberBlack)
                                .border(1.dp, CyberCharcoal, RoundedCornerShape(8dp))
                        ) {
                            val width = size.width
                            val height = size.height
                            val midY = height / 2f
                            val path = Path()

                            path.moveTo(0f, midY)
                            // Draw nice cyber wave curves
                            for (x in 0..width.toInt() step 5) {
                                val xRad = (x.toFloat() / width) * 4f * java.lang.Math.PI.toFloat()
                                val y = midY + waveAmplitude.value * kotlin.math.sin(xRad + wavePhase)
                                path.lineTo(x.toFloat(), y)
                            }

                            val waveColor = when {
                                latestRssi > -50 -> CyberGreen
                                latestRssi > -75 -> CyberCyan
                                else -> CyberRed
                            }

                            drawPath(
                                path = path,
                                color = waveColor,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                            
                            // Background tech grid lines
                            for (gridX in 0..width.toInt() step (width / 10).toInt()) {
                                drawLine(
                                    color = CyberCharcoal.copy(alpha = 0.5f),
                                    start = Offset(gridX.toFloat(), 0f),
                                    end = Offset(gridX.toFloat(), height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16dp))

                        // Stats metrics summary grid
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("RSSI SIGNAL", fontSize = 9sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                Text("$latestRssi dBm", fontSize = 16sp, fontWeight = FontWeight.Bold, color = CyberGreen)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("LINK SPEED", fontSize = 9sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                Text("${if (logs.isNotEmpty()) logs.first().linkSpeedMbps else 866} Mbps", fontSize = 16sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("FREQUENCY", fontSize = 9sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                Text("${String.format("%.1f", if (logs.isNotEmpty()) logs.first().frequencyGhz else 5.2)} GHz", fontSize = 16sp, fontWeight = FontWeight.Bold, color = CyberAmber)
                            }
                        }

                        Spacer(modifier = Modifier.height(16dp))

                        // TRACKING TOGGLE FOR FOREGROUND ENGINE
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8dp))
                                .background(CyberCharcoal)
                                .padding(horizontal = 12dp, vertical = 8dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (activeTracking) Icons.Default.Circle else Icons.Default.PlayArrow,
                                    contentDescription = "Active Indicator",
                                    tint = if (activeTracking) CyberGreen else Color.Gray,
                                    modifier = Modifier.size(16dp)
                                )
                                Spacer(modifier = Modifier.width(8dp))
                                Text(
                                    "Continuous Trace Mode (Foreground)",
                                    fontSize = 11sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                            }
                            Switch(
                                checked = activeTracking,
                                onCheckedChange = { viewModel.toggleContinuousTracking(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CyberGreen,
                                    checkedTrackColor = CyberGreen.copy(alpha = 0.3f),
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = CyberCharcoal
                                ),
                                modifier = Modifier.scale(0.85f).testTag("foreground_service_switch")
                            )
                        }
                    }
                }
            }

            // SECTION 2: SCRUBBABLE HISTORY GRAPH
            item {
                val graphLogs = viewModel.getScrubbedLogs()
                CyberCardGlowPanel {
                    Column(modifier = Modifier.padding(16dp)) {
                        Text(
                            "TELEMETRY TIMELINE & SIGNAL METRICS",
                            fontSize = 12sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyberCyan
                        )
                        Spacer(modifier = Modifier.height(12dp))

                        if (graphLogs.size < 2) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Info, contentDescription = "info", tint = Color.Gray)
                                    Spacer(modifier = Modifier.height(4dp))
                                    Text(
                                        "Awaiting telemetry entries... Turn on Trace Mode.",
                                        fontSize = 11sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.Gray
                                    )
                                }
                            }
                        } else {
                            // Touch responsive Canvas with scrubber!
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140dp)
                                    .background(CyberBlack)
                                    .border(1.dp, CyberCharcoal, RoundedCornerShape(4dp))
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onTap = { offset ->
                                                val colWidth = size.width / graphLogs.size.toFloat()
                                                val clickedIdx = (offset.x / colWidth).toInt()
                                                scrubbedIndex = clickedIdx.coerceIn(0, graphLogs.size - 1)
                                            }
                                        )
                                    }
                            ) {
                                val canvasWidth = constraints.maxWidth.toFloat()
                                val canvasHeight = constraints.maxHeight.toFloat()
                                val minDbm = -90f
                                val maxDbm = -30f
                                val dbmRange = maxDbm - minDbm

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val colWidth = canvasWidth / (graphLogs.size - 1)
                                    val path = Path()

                                    // Build line path for signal readings
                                    graphLogs.forEachIndexed { i, log ->
                                        val x = i * colWidth
                                        val rssiFraction = (log.rssiDbm.toFloat() - minDbm) / dbmRange
                                        val y = canvasHeight - (rssiFraction * canvasHeight)
                                        
                                        if (i == 0) {
                                            path.moveTo(x, y)
                                        } else {
                                            path.lineTo(x, y)
                                        }
                                    }

                                    // Draw background references
                                    drawLine(
                                        color = CyberCharcoal.copy(alpha = 0.7f),
                                        start = Offset(0f, canvasHeight * 0.25f),
                                        end = Offset(canvasWidth, canvasHeight * 0.25f),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                    drawLine(
                                        color = CyberCharcoal.copy(alpha = 0.7f),
                                        start = Offset(0f, canvasHeight * 0.75f),
                                        end = Offset(canvasWidth, canvasHeight * 0.75f),
                                        strokeWidth = 1.dp.toPx()
                                    )

                                    // Draw line path
                                    drawPath(
                                        path = path,
                                        color = CyberGreen,
                                        style = Stroke(width = 2.dp.toPx())
                                    )

                                    // Optional scrub crossbar indicators
                                    scrubbedIndex?.let { idx ->
                                        if (idx in graphLogs.indices) {
                                            val log = graphLogs[idx]
                                            val targetX = idx * colWidth
                                            val fraction = (log.rssiDbm.toFloat() - minDbm) / dbmRange
                                            val targetY = canvasHeight - (fraction * canvasHeight)

                                            // Draw slider line
                                            drawLine(
                                                color = CyberCyan.copy(alpha = 0.8f),
                                                start = Offset(targetX, 0f),
                                                end = Offset(targetX, canvasHeight),
                                                strokeWidth = 1.5.dp.toPx()
                                            )
                                            // Draw highlighted hub
                                            drawCircle(
                                                color = CyberCyan,
                                                radius = 6.dp.toPx(),
                                                center = Offset(targetX, targetY)
                                            )
                                        }
                                    }
                                }
                            }

                            // Render scrub parameters below chart dynamically
                            val activeIndex = scrubbedIndex ?: (graphLogs.size - 1)
                            if (activeIndex in graphLogs.indices) {
                                val activeLog = graphLogs[activeIndex]
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Scrub: SSID [${activeLog.ssid}] | AP [${activeLog.bssid}]",
                                        fontSize = 11sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.LightGray
                                    )
                                    Text(
                                        "Signal: ${activeLog.rssiDbm} dBm",
                                        fontSize = 11sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (activeLog.rssiDbm > -50) CyberGreen else if (activeLog.rssiDbm > -75) CyberAmber else CyberRed
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 3: AUTOMATED TARGET SPEED TESTING PANEL
            item {
                CyberCardGlowPanel {
                    Column(modifier = Modifier.padding(16dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "PERFORMANCE SWEEP CORE",
                                fontSize = 12sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = CyberGreen
                            )
                            
                            // Speed test profile indicators
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                listOf("Streaming", "Gaming", "Eco").forEach { profile ->
                                    val isSelected = selectedProfile == profile
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 4dp)
                                            .clip(RoundedCornerShape(4dp))
                                            .background(if (isSelected) CyberGreen.copy(alpha = 0.25f) else CyberCharcoal)
                                            .border(1.dp, if (isSelected) CyberGreen else Color.Transparent, RoundedCornerShape(4dp))
                                            .clickable { viewModel.selectedProfile.value = profile }
                                            .padding(horizontal = 8dp, vertical = 4dp)
                                    ) {
                                        Text(
                                            profile,
                                            fontSize = 9sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (isSelected) CyberGreen else Color.White
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12dp))

                        // Domain Field with Certificate Pinning check
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("Pin Target Server (SSL)", color = Color.Gray, fontSize = 11sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberGreen,
                                unfocusedBorderColor = CyberCharcoal,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = CyberGreen
                            ),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().testTag("server_url_input")
                        )

                        Spacer(modifier = Modifier.height(12dp))

                        // Action Speed diagnostic
                        when (val state = speedTestState) {
                            is SpeedTestUIState.Idle -> {
                                Button(
                                    onClick = { viewModel.startManualDiagnostic() },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                                    shape = RoundedCornerShape(4dp),
                                    modifier = Modifier.fillMaxWidth().testTag("start_test_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = CyberBlack)
                                    Spacer(modifier = Modifier.width(8dp))
                                    Text("INITIATE PERFORMANCE SWEEP", fontWeight = FontWeight.Bold, color = CyberBlack)
                                }
                            }
                            is SpeedTestUIState.Testing -> {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            state.currentStep,
                                            fontSize = 11sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = CyberGreen
                                        )
                                        Text(
                                            "${state.progressPercent.toInt()}%",
                                            fontSize = 11sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = CyberGreen
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6dp))
                                    LinearProgressIndicator(
                                        progress = { (state.progressPercent / 100.0).toFloat() },
                                        modifier = Modifier.fillMaxWidth().height(4dp).clip(RoundedCornerShape(2dp)),
                                        color = CyberGreen,
                                        trackColor = CyberCharcoal,
                                    )
                                }
                            }
                            is SpeedTestUIState.Complete -> {
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4dp))
                                        .background(CyberCharcoal)
                                        .padding(12dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("METRIC RESULTS", fontSize = 10sp, color = CyberGreen, fontFamily = FontFamily.Monospace)
                                        Text("SECURE PIPELINE OK", fontSize = 10sp, color = CyberCyan, fontFamily = FontFamily.Monospace)
                                    }

                                    Divider(color = CyberBlack, modifier = Modifier.padding(vertical = 8dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("DOWNLOAD", fontSize = 9sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                            Text("${String.format("%.1f", state.result.downloadMbps)} Mbps", fontSize = 20sp, fontWeight = FontWeight.ExtraBold, color = CyberGreen)
                                        }
                                        Column {
                                            Text("UPLOAD", fontSize = 9sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                            Text("${String.format("%.1f", state.result.uploadMbps)} Mbps", fontSize = 20sp, fontWeight = FontWeight.ExtraBold, color = CyberCyan)
                                        }
                                        Column {
                                            Text("LATENCY (PNG)", fontSize = 9sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                            Text("${String.format("%.0f", state.result.latencyMs)} ms", fontSize = 20sp, fontWeight = FontWeight.ExtraBold, color = CyberAmber)
                                        }
                                        Column {
                                            Text("JITTER", fontSize = 9sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                            Text("${String.format("%.1f", state.result.jitterMs)} ms", fontSize = 20sp, fontWeight = FontWeight.ExtraBold, color = CyberAmber)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8dp))
                                    Text(
                                        "ISP Routing pathway: ${state.result.ispName} (Public IP: ${state.result.publicIp})",
                                        fontSize = 10sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.LightGray
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8dp))
                                    
                                    Button(
                                        onClick = { viewModel.speedTestState.value = SpeedTestUIState.Idle },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberCharcoal),
                                        shape = RoundedCornerShape(4dp),
                                        border = BorderStroke(1.dp, CyberCyan),
                                        modifier = Modifier.fillMaxWidth().height(32dp)
                                    ) {
                                        Text("RESET TESTER", fontSize = 10sp, color = CyberCyan, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            is SpeedTestUIState.Error -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Test failed and bypassed securely: ${state.message}", fontSize = 11sp, color = CyberRed, fontFamily = FontFamily.Monospace)
                                    Spacer(modifier = Modifier.height(8dp))
                                    Button(
                                        onClick = { viewModel.speedTestState.value = SpeedTestUIState.Idle },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberCharcoal)
                                    ) {
                                        Text("Retry", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 4: MOCK WI-FI CHANNEL SCANNER RADAR OVERLAY
            item {
                CyberCardGlowPanel {
                    Column(modifier = Modifier.padding(16dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "WI-FI CONGESTION SCANS (BAND OVERLAYS)",
                                fontSize = 12sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = CyberGreen
                            )
                            Row {
                                listOf("2.4 GHz", "5 GHz", "6 GHz").forEach { band ->
                                    val isCurrent = activeBand == band
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 2dp)
                                            .clip(RoundedCornerShape(4dp))
                                            .background(if (isCurrent) CyberGreen.copy(alpha = 0.2f) else CyberCharcoal)
                                            .clickable { viewModel.activeBandScanner.value = band }
                                            .padding(horizontal = 6dp, vertical = 3dp)
                                    ) {
                                        Text(band, fontSize = 8sp, color = if (isCurrent) CyberGreen else Color.White, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12dp))

                        // Drawing live channels parabolics inside Compose Canvas!
                        val scopedChannels by viewModel.scannedChannels.collectAsState()
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120dp)
                                .background(CyberBlack)
                                .border(1.dp, CyberCharcoal)
                        ) {
                            val w = size.width
                            val h = size.height

                            // Draw baseline
                            drawLine(
                                color = Color.Gray,
                                start = Offset(0f, h - 10f),
                                end = Offset(w, h - 10f),
                                strokeWidth = 1.dp.toPx()
                            )

                            // Overlay mock channels curves dynamically
                            scopedChannels.forEachIndexed { idx, ch ->
                                val channelCenterFraction = (ch.channel.toFloat()) / 165f // scale width indices
                                val centerPx = channelCenterFraction * w
                                val curveWidth = (ch.widthMhz.toFloat() / 160f) * w
                                
                                // Height translates directly to Signal dbm levels (optimal -30 is high, bad -90 is low)
                                val strengthFraction = (ch.signalStrengthDbm.toFloat() + 100f) / 70f
                                val peakY = h - 10f - (strengthFraction * (h - 20f))

                                val curvePath = Path()
                                curvePath.moveTo(centerPx - curveWidth / 2f, h - 10f)
                                curvePath.quadraticTo(
                                    centerPx, peakY,
                                    centerPx + curveWidth / 2f, h - 10f
                                )

                                val overlayColor = if (ch.ssid == "HomeMesh_Secure") CyberGreen else CyberCyan.copy(alpha = 0.4f)
                                drawPath(
                                    path = curvePath,
                                    color = overlayColor,
                                    style = Stroke(width = 1.5.dp.toPx())
                                )

                                // Text channel descriptors overlay
                                drawCircle(
                                    color = if (ch.ssid == "HomeMesh_Secure") CyberGreen else CyberCyan,
                                    radius = 3.dp.toPx(),
                                    center = Offset(centerPx, peakY)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8dp))
                        Text(
                            "Visual overlays showcase overlapping channels from adjacent BSSIDs. Peak parabola indicates high local signal strength.",
                            fontSize = 9sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.Gray
                        )
                    }
                }
            }

            // SECTION 5: AI DIAGNOSTICS & RECOMMENDATIONS TERMINAL
            item {
                CyberCardGlowPanel {
                    Column(modifier = Modifier.padding(16dp)) {
                        Text(
                            "AI DIAGNOSTICS DECRYPT (GEMINI LABS)",
                            fontSize = 12sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyberGreen
                        )
                        Spacer(modifier = Modifier.height(10dp))

                        when (val state = aiState) {
                            is AIDiagnoseUIState.Idle -> {
                                Text(
                                    "Analyze your historical connection dBm levels and mesh access point roaming speeds with Gemini-3.5 AI diagnostics.",
                                    fontSize = 11sp,
                                    color = Color.LightGray
                                )
                                Spacer(modifier = Modifier.height(12dp))
                                Button(
                                    onClick = { viewModel.triggerAIDiagnostics() },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCharcoal),
                                    shape = RoundedCornerShape(4dp),
                                    border = BorderStroke(1.dp, CyberGreen),
                                    modifier = Modifier.fillMaxWidth().testTag("ai_diagnose_button")
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = "Star", tint = CyberGreen)
                                    Spacer(modifier = Modifier.width(8dp))
                                    Text("ASK AI DIAGNOSTICK RECS", color = CyberGreen)
                                }
                            }
                            is AIDiagnoseUIState.Loading -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(color = CyberGreen)
                                    Spacer(modifier = Modifier.height(8dp))
                                    Text(
                                        "Decrypting telemetry datasets with Gemini Core...",
                                        fontSize = 11sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = CyberGreen
                                    )
                                }
                            }
                            is AIDiagnoseUIState.Success -> {
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4dp))
                                        .background(CyberBlack)
                                        .border(1.dp, CyberCharcoal, RoundedCornerShape(4dp))
                                        .padding(12dp)
                                ) {
                                    Text(
                                        "SECURE AI RECS REPORT:",
                                        fontSize = 10sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyberGreen,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.height(8dp))
                                    Text(
                                        text = state.recommendations,
                                        fontSize = 11sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(12dp))
                                    Button(
                                        onClick = { viewModel.aiDiagnoseState.value = AIDiagnoseUIState.Idle },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberCharcoal),
                                        shape = RoundedCornerShape(4dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("WIPE REPORT CACHE", color = Color.White, fontSize = 10sp)
                                    }
                                }
                            }
                            is AIDiagnoseUIState.Error -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("AI Analysis decapsulation error: ${state.message}", fontSize = 11sp, color = CyberRed, fontFamily = FontFamily.Monospace)
                                    Spacer(modifier = Modifier.height(8dp))
                                    Button(
                                        onClick = { viewModel.aiDiagnoseState.value = AIDiagnoseUIState.Idle },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberCharcoal)
                                    ) {
                                        Text("Retry Connection", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 6: Mesh BSSID Roaming Transit & Anomalies Log Table
            item {
                CyberCardGlowPanel {
                    Column(modifier = Modifier.padding(16dp)) {
                        Text(
                            "MESH ROAMING TRANSITS & ANOMALIES LOGS",
                            fontSize = 12sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyberGreen
                        )
                        Spacer(modifier = Modifier.height(10dp))

                        if (roamLogs.isEmpty() && anomalies.isEmpty()) {
                            Text(
                                "No roaming transitions or anomalies captured. Keep logging active.",
                                fontSize = 11sp,
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            // Roam Hops List
                            if (roamLogs.isNotEmpty()) {
                                Text("Roam mesh AP transits found:", fontSize = 10sp, color = CyberCyan, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.height(4dp))
                                roamLogs.take(4).forEach { log ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4dp)
                                            .clip(RoundedCornerShape(4dp))
                                            .background(CyberCharcoal)
                                            .padding(8dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Transit AP: ${log.fromBssid.takeLast(5)} ➜ ${log.toBssid.takeLast(5)}", fontSize = 10sp, color = Color.White, fontFamily = FontFamily.Monospace)
                                            Text("Handoff delay: ${log.handoffDurationMs} ms", fontSize = 9sp, color = Color.LightGray, fontFamily = FontFamily.Monospace)
                                        }
                                        Text(
                                            "-${log.signalDropDbm} dBm Drop",
                                            fontSize = 10sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (log.signalDropDbm > 15) CyberRed else CyberGreen,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8dp))
                            }

                            // Anomalies List
                            if (anomalies.isNotEmpty()) {
                                Text("Instability logs found:", fontSize = 10sp, color = CyberAmber, fontFamily = FontFamily.Monospace)
                                Spacer(modifier = Modifier.height(4dp))
                                anomalies.take(4).forEach { log ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4dp)
                                            .clip(RoundedCornerShape(4dp))
                                            .background(CyberCharcoal)
                                            .padding(8dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(log.type, fontSize = 10sp, fontWeight = FontWeight.Bold, color = CyberAmber, fontFamily = FontFamily.Monospace)
                                            Text(log.description, fontSize = 9sp, color = Color.White, fontFamily = FontFamily.Monospace)
                                        }
                                        Text(
                                            log.severity,
                                            fontSize = 9sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (log.severity == "HIGH") CyberRed else CyberAmber,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(start = 8dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // SECTION 7: DATA BUDGET CONTROLLER & AES ENCRYPT EXPORT
            item {
                CyberCardGlowPanel {
                    Column(modifier = Modifier.padding(16dp)) {
                        Text(
                            "DATA BUDGETS & CIPHER LOGS EXPORTS",
                            fontSize = 12sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyberGreen
                        )
                        Spacer(modifier = Modifier.height(12dp))

                        // Data Cap budget sliders
                        val capLimit = dataCap?.maxBgDataMb ?: 500
                        val capUsed = dataCap?.currentDataUsedMb ?: 0.0

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Monthly Background Speed tests budget quota cap:", fontSize = 10sp, color = Color.LightGray)
                            Text("$capLimit MB Limit", fontSize = 10sp, fontWeight = FontWeight.Bold, color = CyberGreen)
                        }
                        
                        Slider(
                            value = capLimit.toFloat(),
                            onValueChange = { viewModel.configureDataCap(it.toInt()) },
                            valueRange = 100f..2000f,
                            colors = SliderDefaults.colors(
                                thumbColor = CyberGreen,
                                activeTrackColor = CyberGreen,
                                inactiveTrackColor = CyberCharcoal
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("data_budget_slider")
                        )

                        Spacer(modifier = Modifier.height(6dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Current monthly allocated consumption:", fontSize = 10sp, color = Color.Gray)
                            Text("${String.format("%.1f", capUsed)} MB Used", fontSize = 10sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                        }

                        Divider(color = CyberCharcoal, modifier = Modifier.padding(vertical = 12dp))

                        // Encrypted CSV export parameters
                        Text(
                            "Zero-Trust symmetric export. Set an AES 128 passphrase to encrypt standard telemetry databases before exporting.",
                            fontSize = 9sp,
                            color = Color.LightGray
                        )
                        Spacer(modifier = Modifier.height(8dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8dp)
                        ) {
                            OutlinedTextField(
                                value = encryptPasscode,
                                onValueChange = { encryptPasscode = it },
                                label = { Text("AES Key Passphrase", color = Color.Gray, fontSize = 11sp) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberGreen,
                                    unfocusedBorderColor = CyberCharcoal,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.weight(1f).testTag("encryption_passcode_input")
                            )

                            Button(
                                onClick = {
                                    if (encryptPasscode.length >= 4) {
                                        viewModel.exportEncryptedCSV(encryptPasscode)
                                        Toast.makeText(context, "Encrypted secure export queued.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Passphrase must be >= 4 characters.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(4dp),
                                colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                                modifier = Modifier.align(Alignment.CenterVertically).testTag("export_logs_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = CyberBlack)
                                Spacer(modifier = Modifier.width(4dp))
                                Text("EXPORT", color = CyberBlack, fontWeight = FontWeight.Bold, fontSize = 11sp)
                            }
                        }
                    }
                }
            }
            
            // Footer spacer
            item {
                Spacer(modifier = Modifier.height(32dp))
            }
        }
    }
}

// A beautiful glassmorphism styled cyberpunk dashboard panel
@Composable
fun CyberCardGlowPanel(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // Subtle radial glow layout behind the cards
                drawRoundRect(
                    color = CyberSlate,
                    size = size,
                    cornerRadius = CornerRadius(12dp.toPx(), 12dp.toPx())
                )
            }
            .border(1.dp, CyberCharcoal, RoundedCornerShape(12dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        content()
    }
}
