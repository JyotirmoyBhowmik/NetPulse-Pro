package com.aistudio.netpulse.qpzwtr.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkDao {

    // --- Network Logs Queries ---
    @Query("SELECT * FROM network_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<NetworkLog>>

    @Query("SELECT * FROM network_logs WHERE timestamp >= :sinceMs ORDER BY timestamp ASC")
    fun getLogsSince(sinceMs: Long): Flow<List<NetworkLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: NetworkLog)

    @Query("DELETE FROM network_logs")
    suspend fun clearLogs()

    // --- Roaming Logs Queries ---
    @Query("SELECT * FROM roaming_logs ORDER BY timestamp DESC")
    fun getAllRoamLogs(): Flow<List<RoamingLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoamLog(log: RoamingLog)

    // --- Anomaly Logs Queries ---
    @Query("SELECT * FROM anomaly_logs ORDER BY timestamp DESC LIMIT 100")
    fun getRecentAnomalies(): Flow<List<AnomalyLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnomaly(log: AnomalyLog)

    @Query("DELETE FROM anomaly_logs")
    suspend fun clearAnomalies()

    // --- Data Cap Configuration Queries ---
    @Query("SELECT * FROM data_caps WHERE id = 1 LIMIT 1")
    fun getDataCap(): Flow<DataCapConfig?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateDataCap(config: DataCapConfig)

    // --- Page Visit Queries ---
    @Query("SELECT * FROM page_visits ORDER BY timestamp DESC")
    fun getAllPageVisits(): Flow<List<PageVisit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPageVisit(pageVisit: PageVisit)

    @Query("DELETE FROM page_visits")
    suspend fun clearPageVisits()
}

