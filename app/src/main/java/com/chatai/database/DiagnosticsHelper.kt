package com.chatai.database

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.speech.tts.TextToSpeech
import android.util.Log
import com.chatai.HttpServer
import com.chatai.RealtimeAIService
import com.chatai.WebSocketServer
import com.chatai.services.KittAIService
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileWriter
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Helper class pour collecter les diagnostics et générer la page HTML complète
 * Expose les méthodes de manière Java-friendly pour WebAppInterface
 */
class DiagnosticsHelper(private val context: Context) {

    companion object {
        private const val TAG = "DiagnosticsHelper"
        private const val LOGS_DIR = "/storage/emulated/0/ChatAI-Files/logs"
        private const val LOG_FILE_NAME = "chatai.log"
        private const val DIAGNOSTICS_HTML_NAME = "diagnostics.html"
        private const val MAX_LOG_LINES = 1000 // Limiter les logs pour éviter surcharge mémoire
        
        // ⭐ NOUVEAU: Logs partagés en mémoire (accessible depuis toutes les instances)
        private val sharedDiagnosticLogs = Collections.synchronizedList(mutableListOf<String>())
        private const val MAX_SHARED_LOG_ENTRIES = 100 // Limiter à 100 entrées pour éviter surcharge mémoire
    }

    /**
     * Initialise le répertoire et le fichier de log s'ils n'existent pas
     * À appeler au démarrage de l'application pour garantir que le fichier existe
     */
    fun initializeLogFile(): Boolean {
        return try {
            val logsDir = File(LOGS_DIR)
            if (!logsDir.exists()) {
                val created = logsDir.mkdirs()
                Log.d(TAG, "Répertoire logs créé: $created -> ${logsDir.absolutePath}")
            }
            
            val logFile = File(logsDir, LOG_FILE_NAME)
            if (!logFile.exists()) {
                val created = logFile.createNewFile()
                if (created) {
                    // Ajouter un en-tête initial au fichier
                    FileWriter(logFile, true).use { writer ->
                        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                        writer.appendLine("═══════════════════════════════════════════════════════════")
                        writer.appendLine("📅 Fichier de log initialisé: $timestamp")
                        writer.appendLine("═══════════════════════════════════════════════════════════")
                        writer.appendLine("")
                    }
                    Log.i(TAG, "Fichier de log créé: ${logFile.absolutePath}")
                } else {
                    Log.w(TAG, "Impossible de créer le fichier de log: ${logFile.absolutePath}")
                    return false
                }
            }
            
            // Vérifier que le fichier est accessible en écriture
            if (!logFile.canWrite()) {
                Log.w(TAG, "Fichier de log non accessible en écriture: ${logFile.absolutePath}")
                return false
            }
            
            Log.d(TAG, "Fichier de log initialisé avec succès: ${logFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de l'initialisation du fichier de log", e)
            false
        }
    }

    /**
     * Lit le contenu du fichier de logs (dernières N lignes)
     * @return Liste des lignes de logs, ou liste vide en cas d'erreur
     */
    fun readLogFileContent(): List<String> {
        return try {
            val logFile = File("$LOGS_DIR/$LOG_FILE_NAME")
            if (!logFile.exists() || !logFile.canRead()) {
                Log.w(TAG, "Log file does not exist or cannot be read: ${logFile.absolutePath}")
                return emptyList()
            }

            // Lire toutes les lignes
            val allLines = logFile.readLines()
            
            // Retourner seulement les N dernières lignes
            if (allLines.size <= MAX_LOG_LINES) {
                allLines
            } else {
                allLines.takeLast(MAX_LOG_LINES)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading log file", e)
            emptyList()
        }
    }

    /**
     * ⭐ NOUVEAU: Ajoute un log de diagnostic au système partagé
     * Peut être appelé depuis n'importe quelle instance de KittAIService
     */
    fun addDiagnosticLog(message: String) {
        sharedDiagnosticLogs.add(message)
        if (sharedDiagnosticLogs.size > MAX_SHARED_LOG_ENTRIES) {
            sharedDiagnosticLogs.removeAt(0)
        }
    }
    
    /**
     * Récupère les logs en mémoire depuis le système partagé
     * @return Liste des logs de diagnostic, ou liste vide en cas d'erreur
     */
    fun getDiagnosticLogs(): List<String> {
        return sharedDiagnosticLogs.toList()
    }
    
    /**
     * Efface les logs de diagnostic partagés
     */
    fun clearDiagnosticLogs() {
        sharedDiagnosticLogs.clear()
    }

    /**
     * Collecte les informations système (batterie, RAM, stockage, réseau, device)
     * @return Map avec toutes les informations système
     */
    fun getSystemInfo(): Map<String, Any> {
        val systemInfo = mutableMapOf<String, Any>()
        
        try {
            // Date et heure
            val currentTime = Calendar.getInstance()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            
            systemInfo["currentDate"] = dateFormat.format(currentTime.time)
            systemInfo["currentTime"] = timeFormat.format(currentTime.time)
            systemInfo["timezone"] = TimeZone.getDefault().id

            // Batterie
            try {
                val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                    context.registerReceiver(null, ifilter)
                }
                val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                                status == BatteryManager.BATTERY_STATUS_FULL
                val chargingStatus = when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "En charge"
                    BatteryManager.BATTERY_STATUS_FULL -> "Plein"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Décharge"
                    else -> "Inconnu"
                }
                
                systemInfo["batteryLevel"] = batteryLevel
                systemInfo["batteryCharging"] = isCharging
                systemInfo["batteryStatus"] = chargingStatus
            } catch (e: Exception) {
                Log.e(TAG, "Error getting battery info", e)
                systemInfo["batteryLevel"] = -1
                systemInfo["batteryCharging"] = false
                systemInfo["batteryStatus"] = "Erreur"
            }

            // RAM
            try {
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val memInfo = ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memInfo)
                
                val totalRamMB = memInfo.totalMem / (1024 * 1024)
                val availRamMB = memInfo.availMem / (1024 * 1024)
                val usedRamMB = totalRamMB - availRamMB
                val ramUsagePercent = (usedRamMB.toFloat() / totalRamMB.toFloat() * 100).toInt()
                
                systemInfo["ramTotalMB"] = totalRamMB
                systemInfo["ramAvailableMB"] = availRamMB
                systemInfo["ramUsedMB"] = usedRamMB
                systemInfo["ramUsagePercent"] = ramUsagePercent
            } catch (e: Exception) {
                Log.e(TAG, "Error getting RAM info", e)
                systemInfo["ramTotalMB"] = -1
                systemInfo["ramAvailableMB"] = -1
                systemInfo["ramUsedMB"] = -1
                systemInfo["ramUsagePercent"] = -1
            }

            // Stockage
            try {
                val statFs = StatFs(Environment.getDataDirectory().path)
                val totalBytes = statFs.blockCountLong * statFs.blockSizeLong
                val availBytes = statFs.availableBlocksLong * statFs.blockSizeLong
                val usedBytes = totalBytes - availBytes
                
                val df = DecimalFormat("#.##")
                val totalGB = totalBytes / (1024.0 * 1024.0 * 1024.0)
                val availGB = availBytes / (1024.0 * 1024.0 * 1024.0)
                val usedGB = usedBytes / (1024.0 * 1024.0 * 1024.0)
                val storageUsagePercent = (usedGB / totalGB * 100).toInt()
                
                systemInfo["storageTotalGB"] = df.format(totalGB)
                systemInfo["storageAvailableGB"] = df.format(availGB)
                systemInfo["storageUsedGB"] = df.format(usedGB)
                systemInfo["storageUsagePercent"] = storageUsagePercent
            } catch (e: Exception) {
                Log.e(TAG, "Error getting storage info", e)
                systemInfo["storageTotalGB"] = "Erreur"
                systemInfo["storageAvailableGB"] = "Erreur"
                systemInfo["storageUsedGB"] = "Erreur"
                systemInfo["storageUsagePercent"] = -1
            }

            // Réseau
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                
                val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                val networkType = when {
                    capabilities == null -> "Aucun"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellulaire"
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    else -> "Autre"
                }
                
                systemInfo["networkType"] = networkType
                systemInfo["hasInternet"] = hasInternet
            } catch (e: Exception) {
                Log.e(TAG, "Error getting network info", e)
                systemInfo["networkType"] = "Erreur"
                systemInfo["hasInternet"] = false
            }

            // Device
            systemInfo["deviceModel"] = Build.MODEL
            systemInfo["androidVersion"] = Build.VERSION.RELEASE
            systemInfo["sdkInt"] = Build.VERSION.SDK_INT
            systemInfo["manufacturer"] = Build.MANUFACTURER
            systemInfo["device"] = Build.DEVICE
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting system info", e)
        }
        
        return systemInfo
    }

    /**
     * Collecte les statuts de tous les services
     * @param httpServer Référence au HttpServer (peut être null)
     * @param webSocketServer Référence au WebSocketServer (peut être null)
     * @param aiService Référence au RealtimeAIService (peut être null)
     * @return Map avec les statuts de tous les services
     */
    fun getServicesStatus(
        httpServer: HttpServer?,
        webSocketServer: WebSocketServer?,
        aiService: RealtimeAIService?
    ): Map<String, Any> {
        val servicesStatus = mutableMapOf<String, Any>()
        
        try {
            // HTTP Server
            servicesStatus["httpServer"] = mapOf(
                "running" to (httpServer?.isRunning() ?: false),
                "port" to (httpServer?.getPort() ?: -1),
                "status" to if (httpServer?.isRunning() == true) "Actif" else "Inactif"
            )
            
            // WebSocket Server
            servicesStatus["webSocketServer"] = mapOf(
                "running" to (webSocketServer?.isRunning() ?: false),
                "clientsCount" to (webSocketServer?.getConnectedClientsCount() ?: 0),
                "status" to if (webSocketServer?.isRunning() == true) "Actif" else "Inactif"
            )
            
            // AI Service
            servicesStatus["aiService"] = mapOf(
                "healthy" to (aiService?.isHealthy() ?: false),
                "status" to if (aiService?.isHealthy() == true) "Sain" else "Problème"
            )
            
            // Hotword Detection (via HotwordDetectionManager)
            try {
                val hotwordManager = com.chatai.hotword.HotwordDetectionManager(context)
                servicesStatus["hotwordService"] = mapOf(
                    "running" to hotwordManager.isRunning(),
                    "status" to if (hotwordManager.isRunning()) "Actif" else "Inactif"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error getting hotword status", e)
                servicesStatus["hotwordService"] = mapOf(
                    "running" to false,
                    "status" to "Erreur"
                )
            }
            
            // STT (Speech-to-Text) - Whisper Server (vérifier via ping HTTP)
            try {
                val audioConfig = com.chatai.audio.AudioEngineConfig.fromContext(context)
                val engine = audioConfig.engine
                
                val sttAvailable = if (engine == "whisper_server") {
                    // Vérifier si le serveur Whisper répond via ping HTTP
                    try {
                        val endpoint = audioConfig.endpoint.ifBlank { com.chatai.audio.AudioEngineConfig.DEFAULT_ENDPOINT }
                        val baseUrl = endpoint.substringBefore("/inference")
                        val pingUrl = "$baseUrl/health"
                        
                        val client = OkHttpClient.Builder()
                            .connectTimeout(5, TimeUnit.SECONDS)
                            .readTimeout(5, TimeUnit.SECONDS)
                            .build()
                        
                        val request = Request.Builder()
                            .url(pingUrl)
                            .head()
                            .build()
                        
                        val response = client.newCall(request).execute()
                        val available = response.code in 200..499 // Accepte 2xx, 3xx, 4xx (serveur répond)
                        response.close()
                        available
                    } catch (e: Exception) {
                        Log.w(TAG, "Whisper server ping failed: ${e.message}")
                        false
                    }
                } else {
                    // Google Speech : vérifier si SpeechRecognizer est disponible
                    android.speech.SpeechRecognizer.isRecognitionAvailable(context)
                }
                
                servicesStatus["sttService"] = mapOf(
                    "available" to sttAvailable,
                    "type" to if (engine == "whisper_server") "Whisper Server" else "Google Speech",
                    "status" to if (sttAvailable) "Disponible" else "Indisponible"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error getting STT status", e)
                servicesStatus["sttService"] = mapOf(
                    "available" to false,
                    "type" to "Inconnu",
                    "status" to "Erreur"
                )
            }
            
            // TTS (Text-to-Speech) - Vérifier si TextToSpeech est disponible
            try {
                // Vérifier simplement si le service TTS est disponible via Intent
                val ttsAvailable = try {
                    val intent = Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA)
                    val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    resolveInfo != null
                } catch (e: Exception) {
                    Log.w(TAG, "TTS availability check failed: ${e.message}")
                    false
                }
                
                servicesStatus["ttsService"] = mapOf(
                    "available" to ttsAvailable,
                    "status" to if (ttsAvailable) "Disponible" else "Indisponible"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error getting TTS status", e)
                servicesStatus["ttsService"] = mapOf(
                    "available" to false,
                    "status" to "Erreur"
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting services status", e)
        }
        
        return servicesStatus
    }

    /**
     * Génère la page HTML complète avec tous les diagnostics et le thème de la webapp
     * @param logFileContent Contenu du fichier de logs
     * @param diagnosticLogs Logs en mémoire
     * @param systemInfo Informations système
     * @param servicesStatus Statuts des services
     * @return Contenu HTML complet
     */
    fun generateDiagnosticsHtml(
        logFileContent: List<String>,
        diagnosticLogs: List<String>,
        systemInfo: Map<String, Any>,
        servicesStatus: Map<String, Any>
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date())
        
        val html = StringBuilder()
        html.append("""<!DOCTYPE html>
<html lang="fr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Diagnostics ChatAI</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
            background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
            color: #e2e8f0;
            line-height: 1.6;
            padding: 20px;
            min-height: 100vh;
        }
        
        .container {
            max-width: 1200px;
            margin: 0 auto;
        }
        
        .header {
            background: rgba(15, 23, 42, 0.8);
            backdrop-filter: blur(10px);
            border: 1px solid rgba(148, 163, 184, 0.2);
            border-radius: 12px;
            padding: 24px;
            margin-bottom: 24px;
            box-shadow: 0 4px 6px rgba(0, 0, 0, 0.3);
        }
        
        .header h1 {
            font-size: 28px;
            color: #667eea;
            margin-bottom: 8px;
        }
        
        .header p {
            color: #94a3b8;
            font-size: 14px;
        }
        
        .section {
            background: rgba(15, 23, 42, 0.6);
            backdrop-filter: blur(10px);
            border: 1px solid rgba(148, 163, 184, 0.2);
            border-radius: 12px;
            padding: 20px;
            margin-bottom: 20px;
            box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
        }
        
        .section h2 {
            font-size: 20px;
            color: #667eea;
            margin-bottom: 16px;
            padding-bottom: 8px;
            border-bottom: 1px solid rgba(148, 163, 184, 0.2);
        }
        
        .status-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 16px;
            margin-bottom: 20px;
        }
        
        .status-card {
            background: rgba(30, 41, 59, 0.5);
            border: 1px solid rgba(148, 163, 184, 0.2);
            border-radius: 8px;
            padding: 16px;
        }
        
        .status-card h3 {
            font-size: 14px;
            color: #94a3b8;
            margin-bottom: 8px;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        
        .status-card .value {
            font-size: 18px;
            color: #e2e8f0;
            font-weight: 600;
        }
        
        .status-card .status {
            font-size: 12px;
            color: #64748b;
            margin-top: 4px;
        }
        
        .status-active {
            color: #10b981 !important;
        }
        
        .status-inactive {
            color: #ef4444 !important;
        }
        
        .status-available {
            color: #3b82f6 !important;
        }
        
        .logs-container {
            background: rgba(15, 23, 42, 0.9);
            border: 1px solid rgba(148, 163, 184, 0.2);
            border-radius: 8px;
            padding: 16px;
            max-height: 600px;
            overflow-y: auto;
            font-family: 'Courier New', monospace;
            font-size: 12px;
            line-height: 1.4;
        }
        
        .log-line {
            color: #94a3b8;
            margin-bottom: 4px;
            word-wrap: break-word;
        }
        
        .log-line:last-child {
            margin-bottom: 0;
        }
        
        .info-row {
            display: flex;
            justify-content: space-between;
            padding: 8px 0;
            border-bottom: 1px solid rgba(148, 163, 184, 0.1);
        }
        
        .info-row:last-child {
            border-bottom: none;
        }
        
        .info-label {
            color: #94a3b8;
            font-weight: 500;
        }
        
        .info-value {
            color: #e2e8f0;
            font-weight: 600;
        }
        
        .refresh-btn {
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            border: none;
            border-radius: 8px;
            padding: 12px 24px;
            font-size: 14px;
            font-weight: 600;
            cursor: pointer;
            margin-top: 16px;
            transition: transform 0.2s, box-shadow 0.2s;
        }
        
        .refresh-btn:hover {
            transform: translateY(-2px);
            box-shadow: 0 4px 8px rgba(102, 126, 234, 0.4);
        }
        
        .collapsible {
            cursor: pointer;
            user-select: none;
        }
        
        .collapsible:hover {
            color: #8b5cf6;
        }
        
        .collapsible-content {
            display: none;
            margin-top: 16px;
        }
        
        .collapsible-content.active {
            display: block;
        }
        
        ::-webkit-scrollbar {
            width: 8px;
        }
        
        ::-webkit-scrollbar-track {
            background: rgba(15, 23, 42, 0.5);
        }
        
        ::-webkit-scrollbar-thumb {
            background: rgba(148, 163, 184, 0.3);
            border-radius: 4px;
        }
        
        ::-webkit-scrollbar-thumb:hover {
            background: rgba(148, 163, 184, 0.5);
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>📊 Diagnostics ChatAI</h1>
            <p>Généré le: $timestamp</p>
            <button class="refresh-btn" onclick="location.reload()">🔄 Actualiser</button>
        </div>
""")

        // Section Services
        html.append("""        <div class="section">
            <h2>🔌 Services</h2>
            <div class="status-grid">
""")
        
        val httpStatus = servicesStatus["httpServer"] as? Map<*, *>
        val wsStatus = servicesStatus["webSocketServer"] as? Map<*, *>
        val aiStatus = servicesStatus["aiService"] as? Map<*, *>
        val hotwordStatus = servicesStatus["hotwordService"] as? Map<*, *>
        val sttStatus = servicesStatus["sttService"] as? Map<*, *>
        val ttsStatus = servicesStatus["ttsService"] as? Map<*, *>
        
        // HTTP Server
        val httpRunning = httpStatus?.get("running") == true
        val httpStatusText = httpStatus?.get("status")?.toString() ?: "Inconnu"
        val httpPort = httpStatus?.get("port")?.toString() ?: "-"
        html.append("""                <div class="status-card">
                    <h3>HTTP Server</h3>
                    <div class="value ${if (httpRunning) "status-active" else "status-inactive"}">$httpStatusText</div>
                    <div class="status">Port: $httpPort</div>
                </div>
""")
        
        // WebSocket Server
        val wsRunning = wsStatus?.get("running") == true
        val wsStatusText = wsStatus?.get("status")?.toString() ?: "Inconnu"
        val wsClients = wsStatus?.get("clientsCount")?.toString() ?: "0"
        html.append("""                <div class="status-card">
                    <h3>WebSocket Server</h3>
                    <div class="value ${if (wsRunning) "status-active" else "status-inactive"}">$wsStatusText</div>
                    <div class="status">Clients: $wsClients</div>
                </div>
""")
        
        // AI Service
        val aiHealthy = aiStatus?.get("healthy") == true
        val aiStatusText = aiStatus?.get("status")?.toString() ?: "Inconnu"
        html.append("""                <div class="status-card">
                    <h3>AI Service</h3>
                    <div class="value ${if (aiHealthy) "status-active" else "status-inactive"}">$aiStatusText</div>
                </div>
""")
        
        // Hotword Service
        val hotwordRunning = hotwordStatus?.get("running") == true
        val hotwordStatusText = hotwordStatus?.get("status")?.toString() ?: "Inconnu"
        html.append("""                <div class="status-card">
                    <h3>Hotword Service</h3>
                    <div class="value ${if (hotwordRunning) "status-active" else "status-inactive"}">$hotwordStatusText</div>
                </div>
""")
        
        // STT Service
        val sttAvailable = sttStatus?.get("available") == true
        val sttStatusText = sttStatus?.get("status")?.toString() ?: "Inconnu"
        val sttType = sttStatus?.get("type")?.toString() ?: "-"
        html.append("""                <div class="status-card">
                    <h3>STT Service</h3>
                    <div class="value ${if (sttAvailable) "status-available" else "status-inactive"}">$sttStatusText</div>
                    <div class="status">Type: $sttType</div>
                </div>
""")
        
        // TTS Service
        val ttsAvailable = ttsStatus?.get("available") == true
        val ttsStatusText = ttsStatus?.get("status")?.toString() ?: "Inconnu"
        html.append("""                <div class="status-card">
                    <h3>TTS Service</h3>
                    <div class="value ${if (ttsAvailable) "status-available" else "status-inactive"}">$ttsStatusText</div>
                </div>
""")
        
        html.append("""            </div>
        </div>
""")

        // Section Système
        val batteryLevel = systemInfo["batteryLevel"]?.toString() ?: "N/A"
        val batteryStatus = systemInfo["batteryStatus"]?.toString() ?: "Inconnu"
        val ramUsagePercent = systemInfo["ramUsagePercent"]?.toString() ?: "N/A"
        val ramUsedMB = systemInfo["ramUsedMB"]?.toString() ?: "N/A"
        val ramTotalMB = systemInfo["ramTotalMB"]?.toString() ?: "N/A"
        val storageUsagePercent = systemInfo["storageUsagePercent"]?.toString() ?: "N/A"
        val storageUsedGB = systemInfo["storageUsedGB"]?.toString() ?: "N/A"
        val storageTotalGB = systemInfo["storageTotalGB"]?.toString() ?: "N/A"
        val hasInternet = systemInfo["hasInternet"] == true
        val networkType = systemInfo["networkType"]?.toString() ?: "N/A"
        val deviceModel = systemInfo["deviceModel"]?.toString() ?: "N/A"
        val androidVersion = systemInfo["androidVersion"]?.toString() ?: "N/A"
        val sdkInt = systemInfo["sdkInt"]?.toString() ?: "N/A"
        val manufacturer = systemInfo["manufacturer"]?.toString() ?: "N/A"
        val currentDate = systemInfo["currentDate"]?.toString() ?: "N/A"
        
        html.append("""        <div class="section">
            <h2>💻 Système</h2>
            <div class="status-grid">
                <div class="status-card">
                    <h3>Batterie</h3>
                    <div class="value">$batteryLevel%</div>
                    <div class="status">$batteryStatus</div>
                </div>
                <div class="status-card">
                    <h3>RAM</h3>
                    <div class="value">$ramUsagePercent%</div>
                    <div class="status">$ramUsedMB MB / $ramTotalMB MB</div>
                </div>
                <div class="status-card">
                    <h3>Stockage</h3>
                    <div class="value">$storageUsagePercent%</div>
                    <div class="status">$storageUsedGB GB / $storageTotalGB GB</div>
                </div>
                <div class="status-card">
                    <h3>Réseau</h3>
                    <div class="value ${if (hasInternet) "status-active" else "status-inactive"}">$networkType</div>
                    <div class="status">Internet: ${if (hasInternet) "Disponible" else "Indisponible"}</div>
                </div>
            </div>
            <div style="margin-top: 16px;">
                <div class="info-row">
                    <span class="info-label">Modèle:</span>
                    <span class="info-value">$deviceModel</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Android:</span>
                    <span class="info-value">$androidVersion (SDK $sdkInt)</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Fabricant:</span>
                    <span class="info-value">$manufacturer</span>
                </div>
                <div class="info-row">
                    <span class="info-label">Date/Heure:</span>
                    <span class="info-value">$currentDate</span>
                </div>
            </div>
        </div>
""")

        // Section Logs Fichier
        val logFileSize = logFileContent.size
        html.append("""        <div class="section">
            <h2 class="collapsible" onclick="toggleSection('file-logs')">📄 Logs Fichier ($logFileSize lignes) ▼</h2>
            <div id="file-logs" class="collapsible-content active">
                <div class="logs-container">
""")
        
        logFileContent.forEach { line ->
            val escapedLine = escapeHtml(line)
            html.append("                    <div class=\"log-line\">$escapedLine</div>\n")
        }
        
        html.append("""                </div>
            </div>
        </div>
""")

        // Section Logs Mémoire
        val diagnosticLogsSize = diagnosticLogs.size
        html.append("""        <div class="section">
            <h2 class="collapsible" onclick="toggleSection('memory-logs')">🧠 Logs Mémoire ($diagnosticLogsSize entrées) ▼</h2>
            <div id="memory-logs" class="collapsible-content active">
                <div class="logs-container">
""")
        
        if (diagnosticLogs.isEmpty()) {
            html.append("""                    <div class="log-line" style="color: #64748b; font-style: italic;">Aucun log en mémoire</div>
""")
        } else {
            diagnosticLogs.forEach { log ->
                val escapedLog = escapeHtml(log)
                html.append("                    <div class=\"log-line\">$escapedLog</div>\n")
            }
        }
        
        html.append("""                </div>
            </div>
        </div>
""")

        // Footer avec script
        html.append("""    </div>
    <script>
        function toggleSection(id) {
            const content = document.getElementById(id);
            content.classList.toggle('active');
        }
    </script>
</body>
</html>""")
        
        return html.toString()
    }

    /**
     * Échappe les caractères HTML dans une chaîne
     */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
    }

    /**
     * Sauvegarde la page HTML dans le répertoire de logs
     * @param htmlContent Contenu HTML à sauvegarder
     * @return Chemin absolu du fichier sauvegardé, ou null en cas d'erreur
     */
    fun saveDiagnosticsHtml(htmlContent: String): String? {
        return try {
            val logsDir = File(LOGS_DIR)
            if (!logsDir.exists()) {
                val created = logsDir.mkdirs()
                Log.d(TAG, "Logs directory created: $created -> ${logsDir.absolutePath}")
            }
            
            // Vérifier que le répertoire est accessible en écriture
            if (!logsDir.canWrite()) {
                Log.e(TAG, "Logs directory is not writable: ${logsDir.absolutePath}")
                return null
            }
            
            val htmlFile = File(logsDir, DIAGNOSTICS_HTML_NAME)
            
            // Écrire le contenu
            FileWriter(htmlFile).use { writer ->
                writer.write(htmlContent)
            }
            
            // Vérifier que le fichier a été créé et est lisible
            if (!htmlFile.exists()) {
                Log.e(TAG, "HTML file was not created: ${htmlFile.absolutePath}")
                return null
            }
            
            if (!htmlFile.canRead()) {
                Log.e(TAG, "HTML file is not readable: ${htmlFile.absolutePath}")
                return null
            }
            
            // Forcer la synchronisation sur le disque
            htmlFile.setReadable(true, false)
            htmlFile.setWritable(true, false)
            
            Log.i(TAG, "✅ Diagnostics HTML saved: ${htmlFile.absolutePath} (${htmlFile.length()} bytes, readable: ${htmlFile.canRead()})")
            htmlFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error saving diagnostics HTML", e)
            e.printStackTrace()
            null
        }
    }
}

