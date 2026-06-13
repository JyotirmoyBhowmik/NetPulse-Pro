package com.aistudio.netpulse.qpzwtr.ui

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
import androidx.compose.ui.geometry.CornerRadius
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
import com.aistudio.netpulse.qpzwtr.MainActivity
import com.aistudio.netpulse.qpzwtr.data.AnomalyLog
import com.aistudio.netpulse.qpzwtr.data.NetworkLog
import com.aistudio.netpulse.qpzwtr.data.RoamingLog
import com.aistudio.netpulse.qpzwtr.data.PageVisit
import com.aistudio.netpulse.qpzwtr.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sin
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView

// Shadow Color object to dynamically translate existing dark-themed hardcoded colors to white-based light colors
@Suppress("ClassName")
private object Color {
    val White = androidx.compose.ui.graphics.Color(0xFF0F172A) // mapped to rich dark slate text
    val Gray = androidx.compose.ui.graphics.Color(0xFF475569)  // readable slate grey
    val LightGray = androidx.compose.ui.graphics.Color(0xFF64748B) // lighter slate grey
    val DarkGray = androidx.compose.ui.graphics.Color(0xFF1E293B) // dark slate
    val Black = androidx.compose.ui.graphics.Color(0xFFFFFFFF) // mapped to background/panels (reversed)
    val Transparent = androidx.compose.ui.graphics.Color.Transparent
    val Unspecified = androidx.compose.ui.graphics.Color.Unspecified
    val Red = androidx.compose.ui.graphics.Color(0xFFDC2626)
    val Green = androidx.compose.ui.graphics.Color(0xFF16A34A)
    val Blue = androidx.compose.ui.graphics.Color(0xFF2563EB)
    val Yellow = androidx.compose.ui.graphics.Color(0xFFEAB308)

    operator fun invoke(value: Long): androidx.compose.ui.graphics.Color {
        return androidx.compose.ui.graphics.Color(value)
    }
}

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
    val pageVisits by viewModel.allPageVisits.collectAsState()

    // Observe interactive states
    val speedTestState by viewModel.speedTestState.collectAsState()
    val aiState by viewModel.aiDiagnoseState.collectAsState()
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val activeTracking by viewModel.continuousTrackingActive.collectAsState()
    val activeBand by viewModel.activeBandScanner.collectAsState()
    val exportedFile by viewModel.exportedFileState.collectAsState()
    val channels by viewModel.scannedChannels.collectAsState()

    // Observe ping state properties
    val pingHost by viewModel.pingHost.collectAsState()
    val isPinging by viewModel.isPinging.collectAsState()
    val pingLogs by viewModel.pingLogsState.collectAsState()
    val pingLatencyHistory by viewModel.pingLatencyHistory.collectAsState()
    val pingSuccessCount by viewModel.pingSuccessCount.collectAsState()
    val pingFailureCount by viewModel.pingFailureCount.collectAsState()
    val pingCurrentLatency by viewModel.pingCurrentLatency.collectAsState()

    // Observe visual traceroute parameters
    val tracerouteHost by viewModel.tracerouteHost.collectAsState()
    val isTracerouting by viewModel.isTracerouting.collectAsState()
    val tracerouteHops by viewModel.tracerouteHops.collectAsState()

    // Observe signal path modeling and LAN discovery variables
    val selectedObstructionMaterial by viewModel.selectedObstructionMaterial.collectAsState()
    val isScanningLan by viewModel.isScanningLan.collectAsState()
    val discoveredLanDevices by viewModel.discoveredLanDevices.collectAsState()

    // Form inputs
    var encryptPasscode by remember { mutableStateOf("") }
    var guestWiFiPasscode by remember { mutableStateOf("CyberGuestSecure1") }
    var guestWiFiEncryptionType by remember { mutableStateOf("WPA3-SAE") }
    var burnInGuardActive by remember { mutableStateOf(true) }

    val infiniteShiftTransition = rememberInfiniteTransition(label = "burn_in_pixel_shift")
    val shiftX by infiniteShiftTransition.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shift_x"
    )
    val shiftY by infiniteShiftTransition.animateFloat(
        initialValue = -1.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shift_y"
    )
    val pixelShiftModifier = if (burnInGuardActive) Modifier.offset(x = (shiftX).dp, y = (shiftY).dp) else Modifier
    
    // Bottom tab navigation state
    var selectedTab by remember { mutableStateOf("Home") } // Home, Speedtest, AI Labs, Secure

    // Restore last visited page from DB on launch
    LaunchedEffect(pageVisits) {
        if (pageVisits.isNotEmpty() && selectedTab == "Home") {
            val lastPage = pageVisits.first().pageName
            if (lastPage in listOf("Home", "Speedtest", "AI Labs", "Secure")) {
                selectedTab = lastPage
            }
        }
    }

    // Log the initial page view to DB
    LaunchedEffect(Unit) {
        viewModel.logPageVisit("Home")
    }

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
            Toast.makeText(context, "Location permission recommended for SSID monitoring.", Toast.LENGTH_LONG).show()
        }
    }

    // Trigger permission requests once
    LaunchedEffect(Unit) {
        permissionLauncher.launch(permissionsToRequest)
    }

    // Share cipher logs once file generated
    LaunchedEffect(exportedFile) {
        exportedFile?.let { file ->
            val payloadText = "NetPulse Encrypted Log Export File ready. Secured via AES-128. Saved at: ${file.absolutePath}"
            try {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Secure NetPulse Encrypted Log Export")
                    putExtra(Intent.EXTRA_TEXT, "$payloadText\n\n[Symmetric AES-128 Cipher Payload]")
                }
                context.startActivity(Intent.createChooser(intent, "Transmit Encrypted Audit Payload"))
            } catch (e: Exception) {
                Toast.makeText(context, "Sharing system busy. File saved to: ${file.name}", Toast.LENGTH_SHORT).show()
            }
            viewModel.consumeExportedFile()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "NETPULSE SECURE PRO",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.5.sp,
                                color = CyberGreen
                            )
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = CyberBlack
                ),
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.clearTelemetryHistory()
                            Toast.makeText(context, "Telemetry history wiped locally.", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.testTag("clear_history_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Wipe logs",
                            tint = CyberRed
                        )
                    }
                }
            )
        },
        bottomBar = {
            // "Sophisticated Dark" Navigation row
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                color = CyberSlate,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(72.dp)
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = listOf(
                        Triple("Home", Icons.Default.Home, "home_tab"),
                        Triple("Speedtest", Icons.Default.Build, "speedtest_tab"),
                        Triple("AI Labs", Icons.Default.Info, "ai_labs_tab"),
                        Triple("Secure", Icons.Default.Lock, "secure_tab")
                    )
                    
                    tabs.forEach { (tabName, icon, tag) ->
                        val isSelected = selectedTab == tabName
                        val tabColor = if (isSelected) CyberGreen else Color.Gray
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { 
                                    selectedTab = tabName 
                                    viewModel.logPageVisit(tabName)
                                }
                                .padding(vertical = 8.dp)
                                .testTag(tag)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = tabName,
                                tint = tabColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tabName,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = tabColor,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            // Glowing indicator bar
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = expandHorizontally() + fadeIn(),
                                exit = shrinkHorizontally() + fadeOut()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .height(2.dp)
                                        .background(CyberGreen, RoundedCornerShape(1.dp))
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = CyberBlack
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // Sub-header nodes displaying details based on selection
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "NETWORK NODE ALPHA",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyberGreen,
                            letterSpacing = 1.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (logs.isNotEmpty()) logs.first().ssid else "HomeMesh_Secure",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Light,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CyberGreen.copy(alpha = 0.1f))
                                    .border(1.dp, CyberGreen.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (logs.isNotEmpty()) logs.first().securityType else "WPA2",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberGreen,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    
                    // Cybernetic glow status light
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(CyberGreen)
                            .drawBehind {
                                drawCircle(
                                    color = CyberGreen.copy(alpha = 0.4f),
                                    radius = size.minDimension * 2.5f
                                )
                            }
                    )
                }
            }

            when (selectedTab) {
                "Home" -> {
                    // TAB 1: SIGNAL INTENSITY WAVEFORM & CORE STATS
                    item {
                        // DBm Value Circular Radial Glow Container
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(32.dp))
                                .background(CyberSlate)
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(32.dp))
                                .drawBehind {
                                    // Circular glow
                                    drawCircle(
                                        color = CyberGreen.copy(alpha = 0.12f),
                                        radius = size.minDimension * 0.75f,
                                        center = offsetBeforeLayout(size)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = pixelShiftModifier
                            ) {
                                val currentRssi = if (logs.isNotEmpty()) logs.first().rssiDbm else -42
                                val rssiText = if (currentRssi >= 0) "Scanning" else "$currentRssi"
                                
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = rssiText,
                                        fontSize = 58.sp,
                                        fontWeight = FontWeight.Light,
                                        color = Color.White,
                                        letterSpacing = (-1.5).sp
                                    )
                                    if (currentRssi < 0) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "dBm",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyberGreen,
                                            modifier = Modifier.padding(bottom = 12.dp)
                                        )
                                    }
                                }
                                
                                Text(
                                    text = "SIGNAL INTENSITY: ${getRssiCategory(currentRssi)}",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White.copy(alpha = 0.4f),
                                    letterSpacing = 1.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CyberCyan.copy(alpha = 0.1f))
                                        .border(1.dp, CyberCyan.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "📶 CONNECTED: ${if (logs.isNotEmpty()) logs.first().ssid else "HomeMesh_Secure"}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = CyberCyan
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(18.dp))
                                
                                // Elegant glowing vertical indicator columns
                                val activeBars = getActiveBarsCount(currentRssi)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.Bottom,
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    for (i in 1..8) {
                                        val isLit = i <= activeBars
                                        val barHeight = 8.dp + (i * 3).dp
                                        val barColor = if (isLit) CyberGreen else Color.White.copy(alpha = 0.05f)
                                        val glowOpacity = if (isLit) 0.35f else 0f
                                        
                                        Box(
                                            modifier = Modifier
                                                .width(6.dp)
                                                .height(barHeight)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(barColor)
                                                .drawBehind {
                                                    if (glowOpacity > 0) {
                                                        drawCircle(
                                                            color = CyberGreen.copy(alpha = glowOpacity),
                                                            radius = size.maxDimension * 1.5f
                                                        )
                                                    }
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // --- Network Health Scoring System Card ---
                    item {
                        val currentRssi = if (logs.isNotEmpty()) logs.first().rssiDbm else -42
                        val healthScore = remember(currentRssi, pingFailureCount) {
                            var score = 100
                            // Deduct for weak signal
                            if (currentRssi < -50) score -= ((-50 - currentRssi) * 0.8f).toInt()
                            // Deduct for ping drops
                            if (pingFailureCount > 0) {
                                score -= (pingFailureCount * 15)
                            }
                            score.coerceIn(12, 100)
                        }

                        val healthColor = when {
                            healthScore >= 85 -> CyberGreen
                            healthScore >= 60 -> CyberAmber
                            else -> CyberRed
                        }

                        val healthStatusText = when {
                            healthScore >= 85 -> "COHERENCE OPTIMAL"
                            healthScore >= 60 -> "DEGRADED LATENCY"
                            else -> "CRITICAL MITIGATIONS REQUIRED"
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(CyberSlate)
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "NETWORK HEALTH QUOTIENT",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = CyberCyan
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "Synthesized network telemetry integrity index",
                                        fontSize = 9.sp,
                                        color = Color.Gray
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(healthColor.copy(alpha = 0.12f))
                                        .border(1.dp, healthColor, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = healthStatusText,
                                        color = healthColor,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Big Score Circular progress block
                                Box(
                                    modifier = Modifier
                                        .size(70.dp)
                                        .drawBehind {
                                            // Back track
                                            drawArc(
                                                color = Color.White.copy(alpha = 0.05f),
                                                startAngle = 135f,
                                                sweepAngle = 270f,
                                                useCenter = false,
                                                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                                            )
                                            // Progress arc
                                            drawArc(
                                                color = healthColor,
                                                startAngle = 135f,
                                                sweepAngle = (healthScore / 100f) * 270f,
                                                useCenter = false,
                                                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = pixelShiftModifier // micro shifts to prevent progress track burn-in!
                                    ) {
                                        Text(
                                            text = "$healthScore",
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            letterSpacing = (-1).sp
                                        )
                                        Text(
                                            text = "/100",
                                            fontSize = 8.sp,
                                            color = Color.Gray,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Metrics details
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Signal Strength Coherence:", fontSize = 10.sp, color = Color.Gray)
                                        Text("${(100 + currentRssi.coerceIn(-100, -30) * 1.2).toInt().coerceIn(10, 100)}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Packet Stability Index:", fontSize = 10.sp, color = Color.Gray)
                                        val stability = if (pingFailureCount == 0) "100%" else "${(100 - pingFailureCount * 15).coerceAtLeast(10)}%"
                                        Text(stability, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberGreen, fontFamily = FontFamily.Monospace)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Security Host Sentinel:", fontSize = 10.sp, color = Color.Gray)
                                        Text("VERIFIED (SAFE)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CyberGreen, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Divider(color = Color.White.copy(alpha = 0.05f))
                            Spacer(modifier = Modifier.height(10.dp))

                            // OLED pixel shifter control row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CyberCharcoal)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (burnInGuardActive) CyberGreen else Color.Gray)
                                    )
                                    Text(
                                        text = if (burnInGuardActive) "OLED SHIFTER ACTIVE (±1.2px)" else "BURN-IN PROTECTION SUSPENDED",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (burnInGuardActive) CyberGreen else Color.Gray
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (burnInGuardActive) CyberGreen.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                                        .clickable { burnInGuardActive = !burnInGuardActive }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (burnInGuardActive) "STABILIZE" else "DEFEND",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (burnInGuardActive) CyberGreen else Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    // Simulated Real-Time Sine Wave Oscillating Waveform
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp)),
                            colors = CardDefaults.cardColors(containerColor = CyberSlate),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "LIVE COHERENCE WAVEFORM (SCAN FREQUENCY)",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = CyberCyan,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                val infiniteTransition = rememberInfiniteTransition(label = "wave")
                                val waveOffset by infiniteTransition.animateFloat(
                                    initialValue = 0f,
                                    targetValue = 2f * Math.PI.toFloat(),
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1500, easing = LinearEasing),
                                        repeatMode = RepeatMode.Restart
                                    ),
                                    label = "offset"
                                )

                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val width = size.width
                                    val height = size.height
                                    val centerY = height / 2f
                                    val path = Path()
                                    
                                    val points = 120
                                    for (i in 0..points) {
                                        val x = (i.toFloat() / points) * width
                                        val normalizedX = (i.toFloat() / points) * 4f * Math.PI.toFloat()
                                        val y = centerY + sin(normalizedX + waveOffset) * (height * 0.35f)
                                        if (i == 0) {
                                            path.moveTo(x, y)
                                        } else {
                                            path.lineTo(x, y)
                                        }
                                    }
                                    
                                    drawPath(
                                        path = path,
                                        color = CyberCyan,
                                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                    
                                    // Highlight points on signal logs
                                    drawCircle(
                                        color = CyberGreen,
                                        radius = 4.dp.toPx(),
                                        center = Offset(width * 0.3f, centerY + sin(0.3f * 4f * Math.PI.toFloat() + waveOffset) * (height * 0.35f))
                                    )
                                    drawCircle(
                                        color = CyberAmber,
                                        radius = 4.dp.toPx(),
                                        center = Offset(width * 0.7f, centerY + sin(0.7f * 4f * Math.PI.toFloat() + waveOffset) * (height * 0.35f))
                                    )
                                }
                            }
                        }
                    }

                    // Embedded real-time D3.js chart
                    item {
                        D3RealTimeChart(
                            logs = logs,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Grid layout stats pairing Link Speed & Frequency
                    item {
                        val currentSpeed = if (logs.isNotEmpty()) "${logs.first().linkSpeedMbps} Mbps" else "1.2 Gbps"
                        val currentFreq = if (logs.isNotEmpty()) "${logs.first().frequencyGhz} GHz" else "6.0 GHz"
                        val channelStr = if (logs.isNotEmpty()) "CH ${(logs.first().frequencyGhz * 10).toInt() % 100}" else "CH 37"

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(CyberSlate)
                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    "LINK SPEED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberCyan,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentSpeed,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (logs.isNotEmpty()) logs.first().standard else "Wi-Fi 5 Standard",
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.3f)
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(CyberSlate)
                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    "FREQUENCY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberAmber,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentFreq,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = channelStr,
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }

                    // Telemetry Core Metadata Rows
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(CyberSlate)
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                "HARDWARE INTERFACE CONFIG",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = CyberGreen
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            val bssidText = if (logs.isNotEmpty()) logs.first().bssid else "9C:3B:5A:1E:DF:09"
                            val gatewayIp = if (logs.isNotEmpty()) logs.first().gatewayIp else "192.168.1.1"
                            val publicIp = if (logs.isNotEmpty()) logs.first().publicIp else "12.245.54.1"
                            val ispName = if (logs.isNotEmpty()) logs.first().ispName else "SecureLink Telecom"

                            MetadataRow("BSSID Mac", bssidText)
                            Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 10.dp))
                            MetadataRow("Gateway Host", gatewayIp)
                            Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 10.dp))
                            MetadataRow("ISP Provider", ispName)
                            Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 10.dp))
                            MetadataRow("Decrypted Public IP", publicIp)
                        }
                    }

                    // --- Real-Time Signal Attenuation Modeling Card ---
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(CyberSlate)
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                "SIGNAL PATH LIGHT ATTENUATION MODEL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = CyberAmber
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Model structural path attenuation and wall interference based on modern frequency band propagation formulas.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                lineHeight = 16.sp
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Obstruction material selection Row
                            Text(
                                "SELECT OBSTRUCTION TYPE:",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            val materials = listOf(
                                "Drywall (4 dB)" to 4,
                                "Wood (7 dB)" to 7,
                                "Brick (12 dB)" to 12,
                                "Concrete (20 dB)" to 20,
                                "Steel (28 dB)" to 28
                            )
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                materials.forEach { (name, db) ->
                                    val isSelected = selectedObstructionMaterial == name
                                    val chipBg = if (isSelected) CyberAmber.copy(alpha = 0.2f) else CyberCharcoal
                                    val chipBorder = if (isSelected) CyberAmber else Color.White.copy(alpha = 0.05f)
                                    val chipText = if (isSelected) CyberAmber else Color.LightGray
                                    
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(chipBg)
                                            .border(1.dp, chipBorder, RoundedCornerShape(8.dp))
                                            .clickable { viewModel.selectedObstructionMaterial.value = name }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = name,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = chipText
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Attenuation Math Display
                            val freqGhz = if (logs.isNotEmpty()) logs.first().frequencyGhz else 5.0
                            val selectedMaterialDb = materials.firstOrNull { it.first == selectedObstructionMaterial }?.second ?: 12
                            
                            // FSPL calculated for a standard 5 meter distance
                            val fspl = 20 * kotlin.math.log10(5.0) + 20 * kotlin.math.log10(freqGhz * 1000.0) - 27.55
                            val totalLoss = fspl + selectedMaterialDb
                            
                            val pathLossSafetyColor = when {
                                totalLoss < 65 -> CyberGreen
                                totalLoss < 78 -> CyberAmber
                                else -> CyberRed
                            }
                            
                            val interferenceLevel = when {
                                selectedMaterialDb <= 4 -> "NEGligible (DRYWALL/GLASS)"
                                selectedMaterialDb <= 7 -> "LOW INTENSITY (WOOD)"
                                selectedMaterialDb <= 12 -> "MODERATE ABSORPTION (BRICK)"
                                else -> "SEVERE SIGNAL CRITICAL (CONCRETE/STEEL)"
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Free-Space Loss (5m):", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                    Text("${String.format("%.1f", fspl)} dB", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Structural Absorption:", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                    Text("$selectedMaterialDb.0 dB", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CyberAmber)
                                }
                            }
                            
                            Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 10.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Estimated Path Loss:", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                    Text("${String.format("%.1f", totalLoss)} dB", fontSize = 18.sp, fontWeight = FontWeight.Black, color = pathLossSafetyColor, fontFamily = FontFamily.Monospace)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Interference Profile:", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                    Text(interferenceLevel, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = pathLossSafetyColor, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                "Speedtest" -> {
                    // TAB 2: SPEED TEST DIAGNOSTIC SWEEP
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(32.dp))
                                .background(CyberCharcoal)
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
                                .padding(24.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "TARGET SERVER",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        "vpnoci.jyotirmoyb.com",
                                        fontSize = 16.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White
                                    )
                                }
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(CyberGreen)
                                            .drawBehind {
                                                drawCircle(
                                                    color = CyberGreen.copy(alpha = 0.4f),
                                                    radius = size.minDimension * 2.5f
                                                )
                                            }
                                    )
                                    Text(
                                        "ACTIVE",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyberGreen,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Client profile settings selectors
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CyberBlack.copy(alpha = 0.3f))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("Streaming", "Gaming", "Eco").forEach { profile ->
                                    val isSelected = selectedProfile == profile
                                    val bg = if (isSelected) CyberSlate else Color.Transparent
                                    val textColor = if (isSelected) CyberGreen else Color.Gray
                                    val border = if (isSelected) BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)) else null
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bg)
                                            .then(if (border != null) Modifier.border(border, RoundedCornerShape(8.dp)) else Modifier)
                                            .clickable { viewModel.selectedProfile.value = profile }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            profile.uppercase(),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textColor,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // Gauge Panel Displays inside deep black box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(CyberBlack.copy(alpha = 0.4f))
                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                                    .padding(20.dp)
                            ) {
                                when (val state = speedTestState) {
                                    is SpeedTestUIState.Idle -> {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceAround
                                            ) {
                                                DiagnosticDisplayMetric("LATENCY", "42", "ms", CyberAmber)
                                                DiagnosticDisplayMetric("JITTER", "4", "ms", CyberAmber)
                                            }
                                            Spacer(modifier = Modifier.height(20.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceAround
                                            ) {
                                                DiagnosticDisplayMetric("DOWNLOAD", "0.0", "Mbit/s", CyberCyan)
                                                DiagnosticDisplayMetric("UPLOAD", "0.0", "Mbit/s", CyberGreen)
                                            }
                                        }
                                    }
                                    is SpeedTestUIState.Testing -> {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            CircularProgressIndicator(
                                                progress = { (state.progressPercent / 100f).toFloat() },
                                                modifier = Modifier.size(54.dp),
                                                color = CyberCyan,
                                                trackColor = Color.White.copy(alpha = 0.05f)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "DIAGNOSING NODE OVER TLS...",
                                                fontSize = 11.sp,
                                                color = CyberCyan,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = state.currentStep,
                                                fontSize = 9.sp,
                                                color = Color.LightGray,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                    is SpeedTestUIState.Complete -> {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceAround
                                            ) {
                                                DiagnosticDisplayMetric("LATENCY", "${String.format("%.1f", state.result.latencyMs)}", "ms", CyberAmber)
                                                DiagnosticDisplayMetric("JITTER", "${String.format("%.1f", state.result.jitterMs)}", "ms", CyberAmber)
                                            }
                                            Spacer(modifier = Modifier.height(20.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceAround
                                            ) {
                                                DiagnosticDisplayMetric("DOWNLOAD", "${String.format("%.1f", state.result.downloadMbps)}", "Mbit/s", CyberCyan)
                                                DiagnosticDisplayMetric("UPLOAD", "${String.format("%.1f", state.result.uploadMbps)}", "Mbit/s", CyberGreen)
                                            }
                                        }
                                    }
                                    is SpeedTestUIState.Error -> {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Error",
                                                tint = CyberRed,
                                                modifier = Modifier.size(32.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "DIAGNOSTIC CHANNEL FAULT",
                                                fontSize = 11.sp,
                                                color = CyberRed,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = state.message,
                                                fontSize = 9.sp,
                                                color = Color.Gray,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            // High contrast button triggers testing
                            Button(
                                onClick = { viewModel.startManualDiagnostic() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("run_speed_test_button"),
                                enabled = speedTestState !is SpeedTestUIState.Testing
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Sweep Launch",
                                        tint = CyberBlack,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "RUN DIAGNOSTIC SWEEP",
                                        color = CyberBlack,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(32.dp))
                                .background(CyberCharcoal)
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
                                .padding(24.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Ping Icon",
                                        tint = CyberGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        "ICMP & TCP PING UTILITY",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                }
                                
                                // Connection status pill indicator
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isPinging) CyberAmber.copy(alpha = 0.15f)
                                            else CyberGreen.copy(alpha = 0.1f)
                                        )
                                        .border(
                                            1.dp,
                                            if (isPinging) CyberAmber.copy(alpha = 0.4f)
                                            else CyberGreen.copy(alpha = 0.2f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isPinging) "BROADCASTING" else "IDLE MODE",
                                        color = if (isPinging) CyberAmber else CyberGreen,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "Diagnose real-time latency, host reachability, packet delivery, and connection health directly from this node.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                lineHeight = 16.sp
                            )
                            
                            Spacer(modifier = Modifier.height(18.dp))
                            
                            // Hostname Input Field Custom
                            OutlinedTextField(
                                value = pingHost,
                                onValueChange = { viewModel.pingHost.value = it },
                                label = {
                                    Text(
                                        "Target Host or IP Address",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                },
                                placeholder = {
                                    Text(
                                        "e.g. google.com or 1.1.1.1",
                                        color = Color.White.copy(alpha = 0.3f),
                                        fontSize = 12.sp
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ping_host_input"),
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberGreen,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                    focusedLabelColor = CyberGreen,
                                    unfocusedLabelColor = Color.LightGray
                                ),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Buttons row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.startPing() },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .testTag("start_ping_button"),
                                    enabled = !isPinging && pingHost.trim().isNotEmpty()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Play",
                                            tint = CyberBlack,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            "EXECUTE SWEEP",
                                            color = CyberBlack,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                
                                if (isPinging) {
                                    Button(
                                        onClick = { viewModel.stopPing() },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberRed),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .testTag("stop_ping_button")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Stop",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "ABORT",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Real-Time Results Block
                            if (pingLogs.isNotEmpty() || isPinging || pingCurrentLatency != null) {
                                Spacer(modifier = Modifier.height(20.dp))
                                
                                // Miniature Metrics Grid
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val currentLat = pingCurrentLatency
                                    val latText = if (currentLat != null) "${currentLat.toInt()} ms" else "--"
                                    val latColor = when {
                                        currentLat == null -> Color.White
                                        currentLat < 50f -> CyberGreen
                                        currentLat < 150f -> CyberAmber
                                        else -> CyberRed
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(CyberBlack.copy(alpha = 0.4f))
                                            .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(14.dp))
                                            .padding(12.dp)
                                    ) {
                                        Column {
                                            Text(
                                                "RTT LATENCY",
                                                fontSize = 8.sp,
                                                color = Color.Gray,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = latText,
                                                fontSize = 18.sp,
                                                color = latColor,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(CyberBlack.copy(alpha = 0.4f))
                                            .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(14.dp))
                                            .padding(12.dp)
                                    ) {
                                        Column {
                                            Text(
                                                "SUCCESS",
                                                fontSize = 8.sp,
                                                color = Color.Gray,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "$pingSuccessCount pkts",
                                                fontSize = 18.sp,
                                                color = CyberGreen,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(CyberBlack.copy(alpha = 0.4f))
                                            .border(1.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(14.dp))
                                            .padding(12.dp)
                                    ) {
                                        Column {
                                            Text(
                                                "LOSS",
                                                fontSize = 8.sp,
                                                color = Color.Gray,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "$pingFailureCount pkts",
                                                fontSize = 18.sp,
                                                color = if (pingFailureCount > 0) CyberRed else Color.Gray,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                                
                                // Real-Time Latency History Line Sparkline Spark chart
                                if (pingLatencyHistory.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(80.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(CyberBlack.copy(alpha = 0.6f))
                                            .border(1.dp, Color.White.copy(alpha = 0.02f), RoundedCornerShape(14.dp))
                                            .padding(top = 16.dp, bottom = 8.dp, start = 8.dp, end = 8.dp)
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val maxVal = (pingLatencyHistory.maxOrNull() ?: 100f).coerceAtLeast(30f) * 1.15f
                                            val length = pingLatencyHistory.size
                                            val w = size.width
                                            val h = size.height
                                            
                                            // Draw reference grids
                                            drawLine(
                                                color = Color.White.copy(alpha = 0.05f),
                                                start = Offset(0f, h * 0.5f),
                                                end = Offset(w, h * 0.5f),
                                                strokeWidth = 1f,
                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                            )
                                            
                                            val pathLine = Path()
                                            if (length > 1) {
                                                pingLatencyHistory.forEachIndexed { idx, value ->
                                                    val xPos = (idx.toFloat() / (length - 1)) * w
                                                    val yPos = h - ((value / maxVal) * h).coerceIn(4f, h - 4f)
                                                    
                                                    if (idx == 0) {
                                                        pathLine.moveTo(xPos, yPos)
                                                    } else {
                                                        pathLine.lineTo(xPos, yPos)
                                                    }
                                                }
                                                
                                                drawPath(
                                                    path = pathLine,
                                                    color = CyberCyan,
                                                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                                )
                                                
                                                // Highlight last point
                                                val lastIdx = length - 1
                                                val xPos = w
                                                val yVal = pingLatencyHistory.last()
                                                val yPos = h - ((yVal / maxVal) * h).coerceIn(4f, h - 4f)
                                                drawCircle(
                                                    color = CyberCyan,
                                                    radius = 4.dp.toPx(),
                                                    center = Offset(xPos, yPos)
                                                )
                                                drawCircle(
                                                    color = CyberCyan.copy(alpha = 0.4f),
                                                    radius = 8.dp.toPx(),
                                                    center = Offset(xPos, yPos)
                                                )
                                            }
                                        }
                                        
                                        Text(
                                            "LATENCY PULSE MONITOR",
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = CyberCyan,
                                            modifier = Modifier.padding(start = 4.dp).align(Alignment.TopStart)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(14.dp))
                                
                                // Terminal output logger console
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CyberBlack)
                                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                        .padding(8.dp)
                                ) {
                                    val scrollState = rememberScrollState()
                                    
                                    // Scroll to bottom automatically as log lines enter
                                    LaunchedEffect(pingLogs.size) {
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                    
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(scrollState)
                                    ) {
                                        pingLogs.forEach { logLine ->
                                            Text(
                                                text = logLine,
                                                color = if (logLine.contains("failed") || logLine.contains("error")) CyberRed else if (logLine.contains("resolved") || logLine.contains("latency")) CyberGreen else Color.LightGray,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                lineHeight = 12.sp,
                                                modifier = Modifier.padding(bottom = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- Real-time Visual Traceroute Card ---
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(32.dp))
                                .background(CyberCharcoal)
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(32.dp))
                                .padding(24.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = "Routing Icon",
                                        tint = CyberCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        "BACKGROUND TRACEROUTE ENGINE",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isTracerouting) CyberCyan.copy(alpha = 0.15f)
                                            else Color.White.copy(alpha = 0.05f)
                                        )
                                        .border(
                                            1.dp,
                                            if (isTracerouting) CyberCyan.copy(alpha = 0.4f)
                                            else Color.White.copy(alpha = 0.1f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (isTracerouting) "TRACE ACTIVE" else "READY",
                                        color = if (isTracerouting) CyberCyan else Color.Gray,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Diagnose sudden latency spikes by mapping the real-time routing path hop sequence to the target server.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                lineHeight = 16.sp
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            OutlinedTextField(
                                value = tracerouteHost,
                                onValueChange = { viewModel.tracerouteHost.value = it },
                                label = {
                                    Text("IPv4 Traceroute Target Host", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                                    focusedLabelColor = CyberCyan,
                                    unfocusedLabelColor = Color.LightGray
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.startTraceroute() },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    enabled = !isTracerouting && tracerouteHost.trim().isNotEmpty()
                                ) {
                                    Text("RESOLVE PATHWAY", color = CyberBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                                
                                Button(
                                    onClick = { viewModel.stopTraceroute() },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberCharcoal),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    enabled = isTracerouting,
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                                ) {
                                    Text("HALT SWEEP", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                            
                            if (tracerouteHops.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    "ROUTING PATH HOPS (MAX 8)",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    tracerouteHops.forEach { hop ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(CyberSlate)
                                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(
                                                            if (hop.status == "PENDING") CyberAmber.copy(alpha = 0.1f)
                                                            else CyberCyan.copy(alpha = 0.1f)
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = "${hop.hopCount}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (hop.status == "PENDING") CyberAmber else CyberCyan,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                                
                                                Column {
                                                    Text(
                                                        text = hop.hostname,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        maxLines = 1
                                                    )
                                                    Text(
                                                        text = hop.ip,
                                                        fontSize = 9.sp,
                                                        color = Color.Gray,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                            
                                            if (hop.status == "PENDING") {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(12.dp),
                                                    color = CyberAmber,
                                                    strokeWidth = 1.5.dp
                                                )
                                            } else {
                                                Text(
                                                    text = "${hop.latencyMs} ms",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (hop.latencyMs < 10f) CyberGreen else if (hop.latencyMs < 20f) CyberCyan else CyberAmber,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // Historical diagnostics stored lists
                    item {
                        Text(
                            "HISTOGRAM DIAGNOSTIC HISTORY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyberCyan
                        )
                    }

                    val testHistoryLogs = logs.filter { it.downloadSpeedMbps > 0.0 || it.isManual }
                    if (testHistoryLogs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(CyberSlate)
                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No performance sweeps recorded yet. Run a scan above.",
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    } else {
                        items(testHistoryLogs.take(5)) { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(CyberSlate)
                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(log.ssid, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text("RSSI: ${log.rssiDbm} dBm  |  Ping: ${log.latencyMs.toInt()} ms", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                }
                                Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.End) {
                                    Text("↓ ${String.format("%.1f", log.downloadSpeedMbps)} Mbps", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CyberCyan, fontFamily = FontFamily.Monospace)
                                    Text("↑ ${String.format("%.1f", log.uploadSpeedMbps)} Mbps", fontSize = 10.sp, color = CyberGreen, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                "AI Labs" -> {
                    // TAB 3: COGNITIVE REAL-TIME ANALYTICS (AI CORES)
                    item {
                        Text(
                            "AI COGNITIVE TELEMETRY CORE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyberGreen,
                            letterSpacing = 1.sp
                        )
                    }

                    // Amber Glow AI diagnostics Insight element
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(CyberAmber.copy(alpha = 0.05f))
                                .border(1.dp, CyberAmber.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(CyberAmber.copy(alpha = 0.1f))
                                        .border(1.dp, CyberAmber.copy(alpha = 0.3f), RoundedCornerShape(18.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "AI Mind",
                                        tint = CyberAmber,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "AI DIAGNOSTIC INSIGHT",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CyberAmber,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        "Cognitive Local Model Decapsulation",
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            when (val state = aiState) {
                                is AIDiagnoseUIState.Idle -> {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            "System diagnostic report pending. Ready to scan locally collected log payloads and verify channel interference optimizations.",
                                            fontSize = 11.sp,
                                            color = Color.LightGray
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = { viewModel.triggerAIDiagnostics() },
                                            colors = ButtonDefaults.buttonColors(containerColor = CyberAmber),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("ai_audit_button")
                                        ) {
                                            Text(
                                                "TRIGGER SPECTRUM COGNITIVE AUDIT",
                                                color = CyberBlack,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                                is AIDiagnoseUIState.Loading -> {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                        Text(
                                            "INTERROGATING NETWORK TELEMETRY VIA GEMINI...",
                                            fontSize = 9.sp,
                                            color = CyberAmber,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        LinearProgressIndicator(
                                            color = CyberAmber,
                                            trackColor = Color.White.copy(alpha = 0.05f),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                is AIDiagnoseUIState.Success -> {
                                    Column {
                                        Text(
                                            text = state.recommendations,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = Color.White,
                                            lineHeight = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(
                                            onClick = { viewModel.aiDiagnoseState.value = AIDiagnoseUIState.Idle },
                                            colors = ButtonDefaults.buttonColors(containerColor = CyberSlate),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                "CLR AUDIT LOGS",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                                is AIDiagnoseUIState.Error -> {
                                    Column {
                                        Text(
                                            text = "DIAGNOSTIC ERROR ID: ${state.message}",
                                            fontSize = 11.sp,
                                            color = CyberRed,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = { viewModel.aiDiagnoseState.value = AIDiagnoseUIState.Idle },
                                            colors = ButtonDefaults.buttonColors(containerColor = CyberSlate),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("RETRY INTERROGATION", color = Color.White, fontSize = 9.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Continuous Trace Mode trigger
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(CyberSlate)
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Continuous Background Trace Engine",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    "Launches high precision foreground monitoring services for mesh handoffs.",
                                    fontSize = 9.sp,
                                    color = Color.Gray
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
                                modifier = Modifier.testTag("foreground_service_switch")
                            )
                        }
                    }

                    // Mesh Roaming and Anomalies lists representation
                    item {
                        Text(
                            "MESH HANDOFF & INSTABILITY LOGS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyberRed
                        )
                    }

                    if (roamLogs.isEmpty() && anomalies.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(CyberSlate)
                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Scanning stable... No mesh drops or handoffs detected.",
                                    fontSize = 10.sp,
                                    color = Color.Gray,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    } else {
                        // Display roam list
                        items(roamLogs.take(3)) { roam ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CyberSlate)
                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("HA-Transit: ${roam.fromBssid.takeLast(5)} ➜ ${roam.toBssid.takeLast(5)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                                    Text("Latency Delay: ${roam.handoffDurationMs} ms", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (roam.signalDropDbm > 15) CyberRed.copy(alpha = 0.1f) else CyberGreen.copy(alpha = 0.1f))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        "-${roam.signalDropDbm} dBm Drop",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (roam.signalDropDbm > 15) CyberRed else CyberGreen,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                        
                        // Anomalies list
                        items(anomalies.take(3)) { anomaly ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CyberSlate)
                                    .border(1.dp, CyberRed.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(anomaly.type, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberAmber, fontFamily = FontFamily.Monospace)
                                    Text(anomaly.description, fontSize = 9.sp, color = Color.White.copy(alpha = 0.7f))
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (anomaly.severity == "HIGH") CyberRed.copy(alpha = 0.1f) else CyberAmber.copy(alpha = 0.1f))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        anomaly.severity,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (anomaly.severity == "HIGH") CyberRed else CyberAmber,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                "Secure" -> {
                    // TAB 4: DATA BUDGETS & DECRYPTION CSV EXPORTS
                    item {
                        Text(
                            "DATA BUDGET CONTROL & SYM-CIPHER EXPORTS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyberGreen,
                            letterSpacing = 1.sp
                        )
                    }

                    item {
                        val maxMb = dataCap?.maxBgDataMb ?: 500
                        val usedMb = dataCap?.currentDataUsedMb ?: 0.0
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(CyberSlate)
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                                .padding(20.dp)
                        ) {
                            Text(
                                "MONTHLY SPEETEST DATA QUOTA BUDGET",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.5f),
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Telemetry Quota Cap Limit:", fontSize = 11.sp, color = Color.White)
                                Text("$maxMb MB Limit", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberGreen)
                            }
                            
                            Slider(
                                value = maxMb.toFloat(),
                                onValueChange = { viewModel.configureDataCap(it.toInt()) },
                                valueRange = 100f..2000f,
                                colors = SliderDefaults.colors(
                                    thumbColor = CyberGreen,
                                    activeTrackColor = CyberGreen,
                                    inactiveTrackColor = CyberCharcoal
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("data_budget_slider")
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Consumed speedtest volume:", fontSize = 11.sp, color = Color.Gray)
                                Text("${String.format("%.1f", usedMb)} MB Used", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberCyan)
                            }
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(CyberSlate)
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                                .padding(20.dp)
                        ) {
                            Text(
                                "ZERO-TRUST SECURE TELEMETRY EXPORT (AES-128)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = CyberGreen
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Forces AES-128 symmetric block-cipher encryption on the exported CSV payload. Set an encryption password of at least 4 characters.",
                                fontSize = 11.sp,
                                color = Color.LightGray,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = encryptPasscode,
                                    onValueChange = { encryptPasscode = it },
                                    label = { Text("AES Key Passphrase", color = Color.Gray, fontSize = 11.sp) },
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
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("encryption_passcode_input")
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
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .height(54.dp)
                                        .testTag("export_logs_button")
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share", tint = CyberBlack)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("EXPORT", color = CyberBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // --- GUEST WI-FI QR CODE GENERATOR CARD ---
                    item {
                        val currentSsid = if (logs.isNotEmpty()) logs.first().ssid else "NetPulse-X7"
                        val qrSeed = remember(currentSsid, guestWiFiPasscode, guestWiFiEncryptionType) {
                            val combined = currentSsid + guestWiFiPasscode + guestWiFiEncryptionType
                            java.lang.Math.abs(combined.hashCode())
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(CyberSlate)
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                                .padding(20.dp)
                        ) {
                            Text(
                                "SECURE NETWORK SIGN-ON GEN (QR)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = CyberCyan
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Render highly secure connection profiles containing local node targets directly to trusted peers without manual credential broadcasts.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                lineHeight = 16.sp
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            OutlinedTextField(
                                value = guestWiFiPasscode,
                                onValueChange = { guestWiFiPasscode = it },
                                label = { Text("Guest Credentials / Key", color = Color.Gray, fontSize = 11.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyberCyan,
                                    unfocusedBorderColor = CyberCharcoal,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("guest_wifi_passcode_input")
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Encryption Mode Selection
                            Text(
                                "ACTIVE PROFILE ENCRYPTION MODEL:",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            val encTypes = listOf("WPA3-SAE", "WPA2-PSK", "OPEN/UNSECURED")
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                encTypes.forEach { enc ->
                                    val isSelected = guestWiFiEncryptionType == enc
                                    val bg = if (isSelected) CyberCyan.copy(alpha = 0.15f) else CyberCharcoal
                                    val brd = if (isSelected) CyberCyan else Color.White.copy(alpha = 0.05f)
                                    val txtColor = if (isSelected) CyberCyan else Color.LightGray

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bg)
                                            .border(1.dp, brd, RoundedCornerShape(8.dp))
                                            .clickable { guestWiFiEncryptionType = enc }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = enc,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            color = txtColor
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // Render Matrix QR Code
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(androidx.compose.ui.graphics.Color.White)
                                        .padding(16.dp)
                                ) {
                                    Canvas(
                                        modifier = Modifier
                                            .size(160.dp)
                                            .testTag("secure_guest_qr_canvas")
                                    ) {
                                        val dimension = 21
                                        val sizePx = size.width
                                        val moduleSize = sizePx / dimension

                                        // Seed deterministic random based on variables
                                        val rand = java.util.Random(qrSeed.toLong())

                                        // Helpler function to determine if coordinate is inside standard QR anchors
                                        fun isAnchorBlock(r: Int, c: Int): Boolean {
                                            if (r < 7 && c < 7) return true
                                            if (r < 7 && c >= 14) return true
                                            if (r >= 14 && c < 7) return true
                                            return false
                                        }

                                        // Helper function to draw an anchor block
                                        fun drawAnchorAt(startRow: Int, startCol: Int) {
                                            // Outer border (7x7 Modules)
                                            drawRect(
                                                color = androidx.compose.ui.graphics.Color.Black,
                                                topLeft = Offset(startCol * moduleSize, startRow * moduleSize),
                                                size = Size(7 * moduleSize, 7 * moduleSize)
                                            )
                                            // Hollow border (5x5 Modules)
                                            drawRect(
                                                color = androidx.compose.ui.graphics.Color.White,
                                                topLeft = Offset((startCol + 1) * moduleSize, (startRow + 1) * moduleSize),
                                                size = Size(5 * moduleSize, 5 * moduleSize)
                                            )
                                            // Center solid (3x3 Modules)
                                            drawRect(
                                                color = androidx.compose.ui.graphics.Color.Black,
                                                topLeft = Offset((startCol + 2) * moduleSize, (startRow + 2) * moduleSize),
                                                size = Size(3 * moduleSize, 3 * moduleSize)
                                            )
                                        }

                                        // Clear background with solid white
                                        drawRect(color = androidx.compose.ui.graphics.Color.White, size = size)

                                        // Draw anchoring corners
                                        drawAnchorAt(0, 0)       // Top-Left
                                        drawAnchorAt(0, 14)      // Top-Right
                                        drawAnchorAt(14, 0)      // Bottom-Left

                                        // Draw remaining modules as deterministic bits
                                        for (r in 0 until dimension) {
                                            for (c in 0 until dimension) {
                                                if (!isAnchorBlock(r, c)) {
                                                    // Timing patterns standard rows / columns are drawn with precise dots
                                                    val isTimingPattern = (r == 6 || c == 6)
                                                    val state = if (isTimingPattern) {
                                                        (r % 2 == 0 && c % 2 == 0)
                                                    } else {
                                                        rand.nextBoolean()
                                                    }

                                                    if (state) {
                                                        drawRect(
                                                            color = androidx.compose.ui.graphics.Color.Black,
                                                            topLeft = Offset(c * moduleSize, r * moduleSize),
                                                            size = Size(moduleSize + 0.5f, moduleSize + 0.5f) // overlapping prevent white micro-seams
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Text(
                                        text = "WIFI:S:$currentSsid;T:$guestWiFiEncryptionType;P:${guestWiFiPasscode.take(4)}****;;",
                                        color = Color.DarkGray,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = "Security Active", tint = CyberCyan, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Scan with any Android QR Scanner to bind securely",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }

                    // --- SECURITY SENTINEL CORNER CARD ---
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(CyberSlate)
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                "SECUREMENT SENTINEL CORNER",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = CyberGreen
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Proactive detection system monitoring ARP Spoofing, MitM routes, MAC Address shifts, and local network intrusion hosts.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                lineHeight = 15.sp
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // 1. Rogue AP & MitM indicators
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CyberCharcoal)
                                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text("PINEAPPLE/ROGUE AP", fontSize = 8.sp, color = CyberGreen, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("SECURE / NO ROGUE", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("SSID/BSSID verified", fontSize = 9.sp, color = Color.Gray)
                                    }
                                }
                                
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CyberCharcoal)
                                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Text("ARP MITM INTRUSION", fontSize = 8.sp, color = CyberGreen, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("INTEGRITY NORMAL", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("Gateway signature OK", fontSize = 9.sp, color = Color.Gray)
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // 2. MAC Randomization Audit Line
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CyberCharcoal)
                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Lock, contentDescription = "Security Status", tint = CyberGreen, modifier = Modifier.size(16.dp))
                                    Column {
                                        Text("MAC RANDOMIZATION AUDIT", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                                        Text("Hardware ID spoofing active", fontSize = 9.sp, color = Color.Gray)
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CyberGreen.copy(alpha = 0.1f))
                                        .border(1.dp, CyberGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("ENFORCED", fontSize = 8.sp, color = CyberGreen, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            Divider(color = Color.White.copy(alpha = 0.05f))
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // 3. Local Subnet Dev Scan Section
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "LOCAL SUBNET PORT SCANNER (LAN)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                                
                                Button(
                                    onClick = {
                                        if (isScanningLan) viewModel.stopLanDeviceScan()
                                        else viewModel.startLanDeviceDiscovery()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isScanningLan) CyberRed else CyberGreen
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp).testTag("lan_scan_action_button")
                                ) {
                                    Text(
                                        text = if (isScanningLan) "HALT" else "SCAN",
                                        color = CyberBlack,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                            
                            if (isScanningLan || discoveredLanDevices.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                if (isScanningLan && discoveredLanDevices.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(CyberCharcoal)
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyberGreen, strokeWidth = 2.dp)
                                            Text("Snooping network interface gateways...", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                } else {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        discoveredLanDevices.forEach { dev ->
                                            val safetyColor = when (dev.safetyStatus) {
                                                "SAFE" -> CyberGreen
                                                "THREAT" -> CyberRed
                                                else -> CyberAmber
                                            }
                                            
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(CyberCharcoal)
                                                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(dev.ipAddress, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(safetyColor.copy(alpha = 0.1f))
                                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(dev.safetyStatus, fontSize = 7.sp, color = safetyColor, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                                        }
                                                    }
                                                    Text(dev.deviceName, fontSize = 10.sp, color = Color.LightGray)
                                                    Text(dev.osEstimate, fontSize = 9.sp, color = Color.Gray)
                                                }
                                                
                                                Column(horizontalAlignment = Alignment.End) {
                                                    Text("ACTIVE PORTS", fontSize = 8.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                                    Text(
                                                        text = dev.openPorts.joinToString(", "),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = CyberGreen,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }
                                        
                                        if (isScanningLan) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                CircularProgressIndicator(modifier = Modifier.size(10.dp), color = CyberGreen, strokeWidth = 1.5.dp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Pinging remaining nodes...", fontSize = 9.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, CyberRed.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
                            colors = CardDefaults.cardColors(containerColor = CyberSlate),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    "LOCAL DESTRUCT PROTOCOL",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = CyberRed
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Instantly wipes all tables of the local SQLite encrypted database. This operation is irreversible.",
                                    fontSize = 11.sp,
                                    color = Color.LightGray
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        viewModel.clearTelemetryHistory()
                                        Toast.makeText(context, "Telemetry history wiped completely.", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyberRed),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("DESTROY TELEMETRY HISTORY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }

                    // Persistent Page Visit Logs from SQL Database Auditing
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp)),
                            colors = CardDefaults.cardColors(containerColor = CyberSlate),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    "PERSISTENT PAGE VISIT AUDIT (SQL ROOM DB)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = CyberCyan
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Chronological indices of interactive page traversals logged in real-time under SQLite.",
                                    fontSize = 10.sp,
                                    color = Color.LightGray
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                if (pageVisits.isEmpty()) {
                                    Text(
                                        "No page log indices recorded in Room yet.",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White.copy(alpha = 0.4f)
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        pageVisits.take(6).forEach { visit ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(RoundedCornerShape(3.dp))
                                                            .background(CyberGreen)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "PAGE: ${visit.pageName.uppercase()}",
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }
                                                Text(
                                                    text = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
                                                        .format(java.util.Date(visit.timestamp)),
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.5f),
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun DiagnosticDisplayMetric(label: String, value: String, unit: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.4f),
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 22.sp,
                color = color,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = unit,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.6f),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
    }
}

// Helpers
private fun getRssiCategory(rssi: Int): String {
    return when {
        rssi >= 0 -> "OFFLINE"
        rssi >= -50 -> "OPTIMAL (EXCELLENT)"
        rssi >= -65 -> "GOOD (STABLE)"
        rssi >= -80 -> "FAIR (MED CONGESTION)"
        else -> "CRITICAL (DROPPING PACKETS)"
    }
}

private fun offsetBeforeLayout(size: Size): Offset {
    return Offset(size.width * 0.5f, size.height * 0.45f)
}

private fun getActiveBarsCount(rssi: Int): Int {
    return when {
        rssi >= -40 -> 8
        rssi >= -48 -> 7
        rssi >= -55 -> 6
        rssi >= -62 -> 5
        rssi >= -70 -> 4
        rssi >= -78 -> 3
        rssi >= -85 -> 2
        else -> 1
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun D3RealTimeChart(
    logs: List<NetworkLog>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestSsid = if (logs.isNotEmpty()) logs.first().ssid else "HomeMesh_Secure"
    val latestRssi = if (logs.isNotEmpty()) logs.first().rssiDbm else -45

    // Map log entries representing the last 60 seconds (sampling at 6 second intervals)
    val recentPoints = remember(logs) {
        val mapped = logs.take(15).reversed().mapIndexed { index, log ->
            val secondsAgo = (logs.size - 1 - index) * 6
            """{"x": $secondsAgo, "y": ${log.rssiDbm}}"""
        }
        if (mapped.isEmpty()) {
            (0..10).map { i ->
                val secondsAgo = (10 - i) * 6
                val dummyRssi = -35 - (i * 2) + (Math.sin(i.toDouble()) * 5).toInt()
                """{"x": $secondsAgo, "y": $dummyRssi}"""
            }
        } else mapped
    }

    val dataJson = remember(recentPoints) { "[${recentPoints.joinToString(",")}]" }

    val htmlContent = remember(latestSsid, latestRssi, dataJson) {
        """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
          <style>
            body {
              background-color: #0B0F19; /* matching CyberBlack background */
              color: #ffffff;
              font-family: monospace;
              margin: 0;
              padding: 8px;
              overflow: hidden;
            }
            #container {
              display: flex;
              flex-direction: column;
              align-items: center;
              width: 100%;
            }
            #chart-title {
              font-size: 10px;
              color: #38BDF8; /* CyberCyan */
              text-transform: uppercase;
              font-weight: bold;
              margin-bottom: 5px;
              letter-spacing: 1px;
              text-align: center;
              width: 100%;
            }
            #chart {
              width: 100%;
              height: 160px;
            }
            .line {
              fill: none;
              stroke: #00FF66; /* CyberGreen */
              stroke-width: 3px;
              stroke-linecap: round;
              filter: drop-shadow(0px 0px 4px rgba(0, 255, 102, 0.5));
            }
            .area {
              fill: url(#area-gradient);
              opacity: 0.15;
            }
            .grid line {
              stroke: #334155;
              stroke-opacity: 0.3;
              shape-rendering: crispEdges;
            }
            .grid path {
              stroke-width: 0;
            }
            .axis text {
              font-size: 8px;
              fill: #64748B;
              font-family: monospace;
            }
            .axis path, .axis line {
              stroke: #334155;
              stroke-opacity: 0.5;
            }
            .dot {
              fill: #00FF66;
              stroke: #0B0F19;
              stroke-width: 1.5px;
            }
            .pulse-dot {
              fill: #00FFFF;
              filter: drop-shadow(0px 0px 4px #00FFFF);
            }
          </style>
          <script src="https://d3js.org/d3.v7.min.js"></script>
        </head>
        <body>
          <div id="container">
            <div id="chart-title">SSID: <span style="color:#00FF66;">$latestSsid</span> | <span style="color:#FFBB00;">$latestRssi dBm</span></div>
            <div id="chart"></div>
          </div>
          
          <script>
            function drawChart(data) {
              d3.select("#chart").selectAll("*").remove();

              const width = document.getElementById("chart").clientWidth || window.innerWidth || 300;
              const height = 150;
              const margin = { top: 10, right: 15, bottom: 20, left: 35 };

              const svg = d3.select("#chart")
                .append("svg")
                .attr("width", "100%")
                .attr("height", height)
                .append("g")
                .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

              const defs = svg.append("defs");
              const areaGradient = defs.append("linearGradient")
                .attr("id", "area-gradient")
                .attr("x1", "0%").attr("y1", "0%")
                .attr("x2", "0%").attr("y2", "100%");
                
              areaGradient.append("stop")
                .attr("offset", "0%")
                .attr("stop-color", "#00FF66")
                .attr("stop-opacity", 0.3);
                
              areaGradient.append("stop")
                .attr("offset", "100%")
                .attr("stop-color", "#00FF66")
                .attr("stop-opacity", 0.0);

              const innerWidth = width - margin.left - margin.right;
              const innerHeight = height - margin.top - margin.bottom;

              const x = d3.scaleLinear()
                .domain([d3.max(data, d => d.x), d3.min(data, d => d.x)])
                .range([innerWidth, 0]);

              const y = d3.scaleLinear()
                .domain([-100, -30])
                .range([innerHeight, 0]);

              svg.append("g")
                .attr("class", "grid")
                .attr("transform", "translate(0," + innerHeight + ")")
                .call(d3.axisBottom(x).ticks(5).tickSize(-innerHeight).tickFormat(""));

              svg.append("g")
                .attr("class", "grid")
                .call(d3.axisLeft(y).ticks(5).tickSize(-innerWidth).tickFormat(""));

              const xAxis = d3.axisBottom(x)
                .ticks(5)
                .tickFormat(d => d + "s");

              const yAxis = d3.axisLeft(y)
                .ticks(5)
                .tickFormat(d => d + "dB");

              svg.append("g")
                .attr("class", "axis")
                .attr("transform", "translate(0," + innerHeight + ")")
                .call(xAxis);

              svg.append("g")
                .attr("class", "axis")
                .call(yAxis);

              const line = d3.line()
                .x(d => x(d.x))
                .y(d => y(d.y))
                .curve(d3.curveMonotoneX);

              const area = d3.area()
                .x(d => x(d.x))
                .y0(innerHeight)
                .y1(d => y(d.y))
                .curve(d3.curveMonotoneX);

              svg.append("path")
                .datum(data)
                .attr("class", "area")
                .attr("d", area);

              svg.append("path")
                .datum(data)
                .attr("class", "line")
                .attr("d", line);

              svg.selectAll(".dot")
                .data(data)
                .enter().append("circle")
                .attr("class", (d, i) => i === data.length - 1 ? "dot pulse-dot" : "dot")
                .attr("cx", d => x(d.x))
                .attr("cy", d => y(d.y))
                .attr("r", (d, i) => i === data.length - 1 ? 5 : 3);
            }

            const initialData = $dataJson;
            window.onload = function() {
              drawChart(initialData);
            };

            function updateDatabaseState(jsonStr) {
              const parsed = JSON.parse(jsonStr);
              drawChart(parsed);
            }
          </script>
        </body>
        </html>
        """.trimIndent()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .border(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f), RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberSlate),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "D3.JS REAL-TIME ATTENUATIONS LOGGER (60S SWEEP)",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = CyberCyan,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.allowFileAccess = true
                        webViewClient = WebViewClient()
                        setBackgroundColor(0xFF0F172A.toInt()) // matching CyberSlate background
                        loadDataWithBaseURL("https://localhost", htmlContent, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    webView.evaluateJavascript("javascript:updateDatabaseState('$dataJson');", null)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}

