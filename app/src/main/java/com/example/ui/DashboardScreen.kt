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
import com.example.MainActivity
import com.example.data.AnomalyLog
import com.example.data.NetworkLog
import com.example.data.RoamingLog
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.sin

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
    val channels by viewModel.scannedChannels.collectAsState()

    // Observe ping state properties
    val pingHost by viewModel.pingHost.collectAsState()
    val isPinging by viewModel.isPinging.collectAsState()
    val pingLogs by viewModel.pingLogsState.collectAsState()
    val pingLatencyHistory by viewModel.pingLatencyHistory.collectAsState()
    val pingSuccessCount by viewModel.pingSuccessCount.collectAsState()
    val pingFailureCount by viewModel.pingFailureCount.collectAsState()
    val pingCurrentLatency by viewModel.pingCurrentLatency.collectAsState()

    // Form inputs
    var encryptPasscode by remember { mutableStateOf("") }
    
    // Bottom tab navigation state
    var selectedTab by remember { mutableStateOf("Home") } // Home, Speedtest, AI Labs, Secure

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
                                .clickable { selectedTab = tabName }
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
                                text = if (logs.isNotEmpty()) logs.first().ssid else "CORP_SECURE_WIFI_7",
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
                                    "WPA3",
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
                                horizontalAlignment = Alignment.CenterHorizontally
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
                                    text = "Wi-Fi 7 Standard",
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
fun DiagnosticDisplayMetric(label: String, value: String, unit: String, color: Color) {
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
