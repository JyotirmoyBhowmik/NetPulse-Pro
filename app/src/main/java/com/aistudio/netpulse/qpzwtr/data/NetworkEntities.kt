package com.aistudio.netpulse.qpzwtr.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_logs")
data class NetworkLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val ssid: String,
    val bssid: String,
    val rssiDbm: Int,
    val linkSpeedMbps: Int,
    val standard: String,
    val frequencyGhz: Double,
    val downloadSpeedMbps: Double = 0.0,
    val uploadSpeedMbps: Double = 0.0,
    val latencyMs: Double = 0.0,
    val jitterMs: Double = 0.0,
    val isAnomaly: Boolean = false,
    val isManual: Boolean = false,
    val gatewayIp: String = "192.168.1.1",
    val publicIp: String = "127.0.0.1",
    val ispName: String = "Default ISP",
    val securityType: String = "WPA2"
)

@Entity(tableName = "roaming_logs")
data class RoamingLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val fromBssid: String,
    val toBssid: String,
    val signalDropDbm: Int,
    val packetLossPercentage: Double,
    val handoffDurationMs: Long
)

@Entity(tableName = "anomaly_logs")
data class AnomalyLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String, // "DISCONNECT", "LOW_SIGNAL", "LATENCY_SPIKE", "CONGESTION"
    val description: String,
    val severity: String // "LOW", "MEDIUM", "HIGH"
)

@Entity(tableName = "data_caps")
data class DataCapConfig(
    @PrimaryKey val id: Int = 1,
    val maxBgDataMb: Int = 500,
    val currentDataUsedMb: Double = 0.0,
    val lastResetTime: Long = System.currentTimeMillis()
)

@Entity(tableName = "page_visits")
data class PageVisit(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pageName: String,
    val timestamp: Long = System.currentTimeMillis()
)
