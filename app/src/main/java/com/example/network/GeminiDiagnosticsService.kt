package com.example.network

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object GeminiDiagnosticsService {
    private const val MODEL = "gemini-3.5-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    suspend fun analyzeNetworkLogs(
        logSummary: String,
        anomalySummary: String
    ): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "SECURE_INFO: Please input your personal GEMINI_API_KEY in the AI Studio Secrets panel. This activates real-time, zero-trust cybersecurity mapping and signal optimization analysis."
        }

        try {
            // Build the standard REST JSON structure safely using the native SDK org.json arrays
            val partsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("text", """
                        You are NetPulse Pro's advanced AI Core. You analyze on-device network telemetry and wifi anomalies.
                        
                        --- TELEMETRY SCAN SUMMARY ---
                        $logSummary
                        
                        --- CAPTURED INSTABILITY ANOMALIES ---
                        $anomalySummary
                        
                        Give an ultra-precise, cyber-styled executive audit.
                        Structure your response strictly into these three concise sections:
                        
                        [ANOMALY ANALYSIS]
                        Identify patterns, signal loss clusters (dBm levels), or access point roaming handoff collisions.
                        
                        [ISP & ROUTING EFFICIENCY]
                        Verify gateway stability and potential throughput limits.
                        
                        [SECURE REMEDIAL MEASURES]
                        Provide 3 direct cyberpunk bullet points (e.g., using [+] symbols) to optimize local configurations.
                        
                        Style: Clear, professional, concise, with minimal conversational padding.
                    """.trimIndent())
                })
            }

            val contentObject = JSONObject().apply {
                put("parts", partsArray)
            }

            val contentsArray = JSONArray().apply {
                put(contentObject)
            }

            val requestBodyJson = JSONObject().apply {
                put("contents", contentsArray)
            }

            val requestBody = requestBodyJson.toString().toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(requestBody)
                .build()

            NetworkClient.okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    return@withContext "[CORE ERROR] AI Engine returned status code $code. Please check API quota or secrets configuration."
                }
                
                val bodyStr = response.body?.string() ?: return@withContext "[SECURE ERROR] Intercepted zero bytes from AI analysis stream."
                
                val parsedJson = JSONObject(bodyStr)
                val candidates = parsedJson.getJSONArray("candidates")
                val candidateObj = candidates.getJSONObject(0)
                val contentObj = candidateObj.getJSONObject("content")
                val parts = contentObj.getJSONArray("parts")
                val text = parts.getJSONObject(0).getString("text")
                
                text
            }
        } catch (e: Exception) {
            Log.e("GeminiDiagnostics", "Direct API execution failure", e)
            "[SYSTEM ALERT] Data transmission interrupted. Verify your internet link and make sure fine location is granted. Error: ${e.localizedMessage}"
        }
    }
}
