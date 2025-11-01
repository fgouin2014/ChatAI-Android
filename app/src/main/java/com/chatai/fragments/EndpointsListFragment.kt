package com.chatai.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.chatai.R
import com.google.android.material.button.MaterialButton
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fragment pour afficher tous les endpoints API de tous les systèmes
 */
class EndpointsListFragment : Fragment() {
    
    private lateinit var chatAIEndpoints: TextView
    private lateinit var webServerEndpoints: TextView
    private lateinit var fileServerEndpoints: TextView
    private lateinit var webSocketEndpoints: TextView
    private lateinit var refreshButton: MaterialButton
    private lateinit var testButton: MaterialButton
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_endpoints_list, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialiser les vues
        initViews(view)
        
        // Configurer les listeners
        setupListeners()
        
        // Charger les endpoints
        loadAllEndpoints()
    }
    
    private fun initViews(view: View) {
        chatAIEndpoints = view.findViewById(R.id.chatAIEndpoints)
        webServerEndpoints = view.findViewById(R.id.webServerEndpoints)
        fileServerEndpoints = view.findViewById(R.id.fileServerEndpoints)
        webSocketEndpoints = view.findViewById(R.id.webSocketEndpoints)
        refreshButton = view.findViewById(R.id.refreshEndpointsButton)
        testButton = view.findViewById(R.id.testEndpointsButton)
    }
    
    private fun setupListeners() {
        refreshButton.setOnClickListener {
            loadAllEndpoints()
        }
        
        testButton.setOnClickListener {
            testAllEndpoints()
        }
    }
    
    private fun loadAllEndpoints() {
        // ChatAI HTTP Server (Port 8080)
        loadChatAIEndpoints()
        
        // WebServer (Port 8888)
        loadWebServerEndpoints()
        
        // FileServer (Port 8082)
        loadFileServerEndpoints()
        
        // WebSocket Server (Port 8081)
        loadWebSocketEndpoints()
    }
    
    private fun loadChatAIEndpoints() {
        val endpoints = """
            GET  /api/status              - Statut du serveur
            GET  /api/plugins             - Liste des plugins
            GET  /api/weather/{city}      - Météo par ville
            GET  /api/jokes/random        - Blague aléatoire
            GET  /api/tips/{category}     - Conseils par catégorie
            GET  /api/health              - Santé du système
            POST /api/translate           - Traduction de texte
            POST /api/chat                - Chat avec IA
            POST /api/ai/query            - Requête IA avec cache
            GET  /api/files/list          - Liste des fichiers
            GET  /api/files/storage/info  - Infos stockage
            GET  /api/files/download/{file} - Télécharger fichier
            GET  /api/files/info/{file}   - Infos fichier
            POST /api/files/upload        - Upload fichier
            DELETE /api/files/delete/{file} - Supprimer fichier
            GET  /browse                  - Explorateur de fichiers
            GET  /sites/{site}            - Sites utilisateur
        """.trimIndent()
        
        chatAIEndpoints.text = endpoints
    }
    
    private fun loadWebServerEndpoints() {
        val endpoints = """
            GET  /                        - Root des sites
            GET  /{path}                  - Fichiers statiques
            GET  /{directory}/            - Listing de répertoire
            GET  /web2/                   - Site web2
            GET  /web2/{file}             - Fichiers du site web2
            GET  /web2/nes/               - Répertoire nes
            GET  /web2/assets/            - Assets du site
            GET  /web2/emulatorjs/        - Scripts emulatorjs
            GET  /web2/games/             - Jeux
        """.trimIndent()
        
        webServerEndpoints.text = endpoints
    }
    
    private fun loadFileServerEndpoints() {
        val endpoints = """
            GET  /api/files/list          - Liste des fichiers
            GET  /api/files/storage/info  - Infos stockage
            GET  /api/files/download/{file} - Télécharger fichier
            GET  /api/files/info/{file}   - Infos fichier
            POST /api/files/upload        - Upload fichier
            POST /api/files/storage/change - Changer répertoire
            DELETE /api/files/delete/{file} - Supprimer fichier
        """.trimIndent()
        
        fileServerEndpoints.text = endpoints
    }
    
    private fun loadWebSocketEndpoints() {
        val endpoints = """
            WebSocket /ws                 - Connexion WebSocket
            WebSocket /ws/chat            - Chat temps réel
            WebSocket /ws/ai              - IA temps réel
            WebSocket /ws/typing          - Indicateur de frappe
            WebSocket /ws/ping            - Ping/Pong
            WebSocket /ws/broadcast       - Diffusion de messages
        """.trimIndent()
        
        webSocketEndpoints.text = endpoints
    }
    
    private fun testAllEndpoints() {
        Toast.makeText(context, "Test des endpoints en cours...", Toast.LENGTH_SHORT).show()
        
        Thread {
            val results = mutableListOf<String>()
            
            // Test ChatAI HTTP Server
            testEndpoint("http://10.19.95.217:8080/api/status", "ChatAI HTTP Server", results)
            
            // Test WebServer
            testEndpoint("http://10.19.95.217:8888/", "WebServer", results)
            
            // Test FileServer
            testEndpoint("http://10.19.95.217:8082/api/files/storage/info", "FileServer", results)
            
            // Afficher les résultats
            mainHandler.post {
                val resultText = results.joinToString("\n")
                Toast.makeText(context, "Résultats:\n$resultText", Toast.LENGTH_LONG).show()
            }
        }.start()
    }
    
    private fun testEndpoint(url: String, name: String, results: MutableList<String>) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            
            val responseCode = connection.responseCode
            results.add("✅ $name: $responseCode")
            
        } catch (e: Exception) {
            results.add("❌ $name: ${e.message}")
        }
    }
}
