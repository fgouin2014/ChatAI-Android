package com.chatai.activities

import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.chatai.R
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.*
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections

/**
 * Classe pour représenter un endpoint
 */
data class EndpointInfo(
    val method: String,
    val description: String,
    val url: String,
    val isClickable: Boolean
)

/**
 * Activité pour afficher la liste des endpoints API
 */
class EndpointsListActivity : AppCompatActivity() {
    
    private lateinit var scrollView: ScrollView
    private lateinit var contentLayout: LinearLayout
    private lateinit var refreshButton: Button
    private lateinit var testButton: Button
    
    private var useLocalhost = false // Variable pour basculer entre localhost et IP réelle
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_endpoints_list)
        
        // Initialiser les vues
        scrollView = findViewById(R.id.scrollView)
        contentLayout = findViewById(R.id.contentLayout)
        refreshButton = findViewById(R.id.refreshButton)
        testButton = findViewById(R.id.testButton)
        
        // Bouton retour
        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }
        
        // Configurer les boutons
        refreshButton.setOnClickListener {
            populateEndpoints()
        }
        
        // Bouton TESTER pour basculer entre localhost/IP et tester les endpoints
        testButton.setOnClickListener {
            // Basculer entre localhost et IP réelle
            useLocalhost = !useLocalhost
            
            val currentMode = if (useLocalhost) "localhost" else "IP réseau"
            android.widget.Toast.makeText(this, "Mode: $currentMode", android.widget.Toast.LENGTH_SHORT).show()
            
            // Actualiser et recharger les endpoints
            populateEndpoints()
            
            // Tester les endpoints
            testAllEndpoints()
        }
        
        // Charger les endpoints au démarrage
        populateEndpoints()
    }
    
    private fun populateEndpoints() {
        contentLayout.removeAllViews()
        
        val deviceIP = if (useLocalhost) {
            "localhost"
        } else {
            val detectedIP = getDeviceIP()
            if (detectedIP == "NO_NETWORK") {
                addNoNetworkMessage()
                return
            }
            detectedIP
        }
        
        // ChatAI HTTP Server (port 8080)
        addServerSection("ChatAI HTTP Server", "Port 8080", listOf(
            EndpointInfo("GET /api/status", "Statut du serveur", "http://$deviceIP:8080/api/status", false),
            EndpointInfo("GET /api/files/storage/info", "Informations de stockage", "http://$deviceIP:8080/api/files/storage/info", false),
            EndpointInfo("GET /api/files/list", "Liste des fichiers", "http://$deviceIP:8080/api/files/list", true),
            EndpointInfo("GET /sites/", "Sites utilisateur (redirection vers WebServer)", "http://$deviceIP:8080/sites/", true),
            EndpointInfo("GET /user-sites/", "Sites utilisateur (redirection vers WebServer)", "http://$deviceIP:8080/user-sites/", true),
            EndpointInfo("GET /browse", "Explorateur de fichiers", "http://$deviceIP:8080/browse", true),
            EndpointInfo("GET /kitt", "Interface KITT", "http://$deviceIP:8080/kitt", true)
        ))
        
        // WebServer (port 8888)
        addServerSection("WebServer", "Port 8888", listOf(
            EndpointInfo("GET /", "Page d'accueil", "http://$deviceIP:8888/", true),
            EndpointInfo("GET /web2/", "Site utilisateur web2", "http://$deviceIP:8888/web2/", true),
            EndpointInfo("GET /web2/nes/", "Sous-répertoire nes", "http://$deviceIP:8888/web2/nes/", true),
            EndpointInfo("GET /web2/nes/media/", "Médias", "http://$deviceIP:8888/web2/nes/media/", true),
            EndpointInfo("GET /config.html", "Configuration WebServer", "http://$deviceIP:8888/config.html", true),
            EndpointInfo("GET /{path}", "Fichiers statiques", "http://$deviceIP:8888/", true)
        ))
        
        // FileServer (port 8082)
        addServerSection("FileServer", "Port 8082", listOf(
            EndpointInfo("GET /api/files/storage/info", "Informations de stockage", "http://$deviceIP:8082/api/files/storage/info", false),
            EndpointInfo("GET /api/files/list", "Liste des fichiers", "http://$deviceIP:8082/api/files/list", true),
            EndpointInfo("GET /api/files/upload", "Upload de fichiers", "http://$deviceIP:8082/api/files/upload", true),
            EndpointInfo("GET /api/files/download/{filename}", "Téléchargement", "http://$deviceIP:8082/api/files/download/", true)
        ))
        
        // WebSocket Server (port 8081)
        addServerSection("WebSocket Server", "Port 8081", listOf(
            EndpointInfo("WS /ws", "Connexion WebSocket", "ws://$deviceIP:8081/ws", true),
            EndpointInfo("WS /ws/chat", "Chat en temps réel", "ws://$deviceIP:8081/ws/chat", true),
            EndpointInfo("WS /ws/ai", "Communication IA", "ws://$deviceIP:8081/ws/ai", true)
        ))
    }
    
    private fun addServerSection(title: String, port: String, endpoints: List<EndpointInfo>) {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 8)
            }
            radius = 8f
            setCardBackgroundColor(resources.getColor(R.color.kitt_black_alpha, null))
        }
        
        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        
        // Titre du serveur avec indicateur de mode
        val titleView = TextView(this).apply {
            val modeIndicator = if (useLocalhost) " (localhost)" else " (réseau)"
            text = title + modeIndicator
            textSize = 16f
            setTextColor(resources.getColor(R.color.kitt_red, null))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        
        // Port
        val portView = TextView(this).apply {
            text = port
            textSize = 12f
            setTextColor(resources.getColor(R.color.kitt_gray, null))
        }
        
        // Endpoints
        val endpointsView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 0)
        }
        
        endpoints.forEach { endpoint ->
            val endpointView = TextView(this).apply {
                text = "• ${endpoint.method} - ${endpoint.description}"
                textSize = 11f
                setTextColor(resources.getColor(R.color.dark_white, null))
                setPadding(0, 2, 0, 2)
                
                // Si l'endpoint est cliquable, ajouter un listener
                if (endpoint.isClickable) {
                    setOnClickListener {
                        openInBrowser(endpoint.url)
                    }
                    setTextColor(resources.getColor(R.color.amber_primary, null))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
            }
            endpointsView.addView(endpointView)
        }
        
        cardContent.addView(titleView)
        cardContent.addView(portView)
        cardContent.addView(endpointsView)
        card.addView(cardContent)
        contentLayout.addView(card)
    }
    
    private fun addNoNetworkMessage() {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 8)
            }
            radius = 8f
            setCardBackgroundColor(resources.getColor(R.color.kitt_black_alpha, null))
        }
        
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        
        val titleView = TextView(this).apply {
            text = "⚠️ Aucune connexion réseau détectée"
            textSize = 18f
            setTextColor(resources.getColor(R.color.kitt_red, null))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
        }
        
        val messageView = TextView(this).apply {
            text = "Connectez-vous à un réseau WiFi ou mobile pour voir les endpoints disponibles."
            textSize = 14f
            setTextColor(resources.getColor(R.color.dark_white, null))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }
        
        layout.addView(titleView)
        layout.addView(messageView)
        card.addView(layout)
        contentLayout.addView(card)
    }
    
    private fun openInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            // En cas d'erreur, afficher un message
            android.widget.Toast.makeText(this, "Impossible d'ouvrir: $url", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getDeviceIP(): String {
        return try {
            // Vérifier d'abord si le WiFi est connecté
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val wifiInfo = wifiManager.connectionInfo
            
            // Vérifier si le WiFi est connecté et a une IP valide
            @Suppress("DEPRECATION")
            if (wifiInfo != null && wifiInfo.ipAddress != 0) {
                @Suppress("DEPRECATION")
                val ip = String.format(
                    "%d.%d.%d.%d",
                    wifiInfo.ipAddress and 0xff,
                    wifiInfo.ipAddress shr 8 and 0xff,
                    wifiInfo.ipAddress shr 16 and 0xff,
                    wifiInfo.ipAddress shr 24 and 0xff
                )
                if (isValidIP(ip)) {
                    android.util.Log.d("EndpointsList", "IP WiFi détectée: $ip")
                    return ip
                }
            }
            
            // Si pas de WiFi, vérifier les autres interfaces réseau
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    val addresses = Collections.list(networkInterface.inetAddresses)
                    for (address in addresses) {
                        if (!address.isLoopbackAddress && address.isSiteLocalAddress) {
                            val ip = address.hostAddress
                            if (ip != null && isValidIP(ip)) {
                                android.util.Log.d("EndpointsList", "IP détectée via ${networkInterface.name}: $ip")
                                return ip
                            }
                        }
                    }
                }
            }
            
            // Si aucune IP n'est trouvée, retourner une indication d'erreur
            android.util.Log.w("EndpointsList", "Aucune connexion réseau détectée")
            "NO_NETWORK" // Indicateur spécial pour pas de réseau
        } catch (e: Exception) {
            android.util.Log.e("EndpointsList", "Erreur lors de la détection d'IP", e)
            "NO_NETWORK" // Indicateur spécial pour erreur
        }
    }
    
    private fun isValidIP(ip: String?): Boolean {
        if (ip.isNullOrEmpty()) return false
        return try {
            val parts = ip.split(".")
            if (parts.size != 4) return false
            parts.all { part ->
                val num = part.toIntOrNull()
                num != null && num in 0..255
            } && !ip.startsWith("127.") && !ip.startsWith("169.254.")
        } catch (e: Exception) {
            false
        }
    }
    
    private fun testAllEndpoints() {
        // Test simple de connectivité
        val testResults = mutableListOf<String>()
        
        // Test HTTP Server
        testEndpoint("http://localhost:8080/api/status", "ChatAI HTTP Server") { result ->
            testResults.add("ChatAI HTTP Server: $result")
        }
        
        // Test WebServer
        testEndpoint("http://localhost:8888/", "WebServer") { result ->
            testResults.add("WebServer: $result")
        }
        
        // Test FileServer
        testEndpoint("http://localhost:8082/api/files/storage/info", "FileServer") { result ->
            testResults.add("FileServer: $result")
        }
        
        // Afficher les résultats après un délai
        CoroutineScope(Dispatchers.Main).launch {
            delay(2000)
            showTestResults(testResults)
        }
    }
    
    private fun testEndpoint(url: String, @Suppress("UNUSED_PARAMETER") name: String, callback: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = java.net.URL(url).openConnection()
                response.connectTimeout = 5000
                response.readTimeout = 5000
                response.connect()
                callback("✅ Connecté")
            } catch (e: Exception) {
                callback("❌ Erreur: ${e.message}")
            }
        }
    }
    
    private fun showTestResults(results: List<String>) {
        val resultsCard = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 8)
            }
            radius = 8f
            setCardBackgroundColor(resources.getColor(R.color.kitt_dark_red, null))
        }
        
        val resultsContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        
        val titleView = TextView(this).apply {
            text = "RÉSULTATS DES TESTS"
            textSize = 14f
            setTextColor(resources.getColor(R.color.kitt_red, null))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        
        resultsContent.addView(titleView)
        
        results.forEach { result ->
            val resultView = TextView(this).apply {
                text = result
                textSize = 11f
                setTextColor(resources.getColor(R.color.dark_white, null))
                setPadding(0, 2, 0, 2)
            }
            resultsContent.addView(resultView)
        }
        
        resultsCard.addView(resultsContent)
        contentLayout.addView(resultsCard)
    }
}
