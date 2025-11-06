package com.chatai.services

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

/**
 * Exemple d'utilisation de KittAIService
 * 
 * NOTE: Ces tests sont des EXEMPLES d'utilisation, pas de vrais tests unitaires.
 * Pour de vrais tests, il faudrait mocker les appels HTTP.
 */
class KittAIServiceExample {
    
    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences
    
    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        // Mock SharedPreferences
        `when`(mockContext.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE))
            .thenReturn(mockSharedPreferences)
        `when`(mockSharedPreferences.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(any(), any())).thenReturn(mockEditor)
    }
    
    /**
     * Exemple 1 : Utilisation basique avec fallback local
     */
    @Test
    fun exampleBasicUsageWithLocalFallback() = runBlocking {
        // Sans clé API configurée
        `when`(mockSharedPreferences.getString("openai_api_key", null)).thenReturn(null)
        `when`(mockSharedPreferences.getString("anthropic_api_key", null)).thenReturn(null)
        `when`(mockSharedPreferences.getString("huggingface_api_key", null)).thenReturn(null)
        
        val service = KittAIService(mockContext)
        
        // Test de conversation simple
        println("\n=== Test 1: Fallback Local ===")
        
        val tests = listOf(
            "Bonjour",
            "Active le scanner",
            "Turbo boost",
            "Système",
            "Qui es-tu ?"
        )
        
        tests.forEach { input ->
            val response = service.processUserInput(input)
            println("User: $input")
            println("KITT: $response\n")
        }
    }
    
    /**
     * Exemple 2 : Utilisation avec OpenAI (simulation)
     * 
     * NOTE: Pour vraiment tester avec OpenAI, il faut une vraie clé API
     */
    @Test
    fun exampleUsageWithOpenAI() {
        println("\n=== Test 2: Avec OpenAI (Simulé) ===")
        println("Pour tester réellement avec OpenAI:")
        println("1. Obtenir une clé API sur https://platform.openai.com/api-keys")
        println("2. Configurer dans SharedPreferences: 'openai_api_key'")
        println("3. Lancer l'application et tester vocalement")
        println("\nExemple de code:")
        println("""
            val prefs = context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
            prefs.edit().putString("openai_api_key", "sk-...").apply()
            
            val service = KittAIService(context)
            lifecycleScope.launch {
                val response = service.processUserInput("Bonjour KITT")
                println("KITT: ${'$'}response")
            }
        """.trimIndent())
    }
    
    /**
     * Exemple 3 : Test du cache
     */
    @Test
    fun exampleCacheFunctionality() = runBlocking {
        `when`(mockSharedPreferences.getString("openai_api_key", null)).thenReturn(null)
        
        val service = KittAIService(mockContext)
        
        println("\n=== Test 3: Cache ===")
        
        val input = "Bonjour"
        
        // Première requête (pas de cache)
        val start1 = System.currentTimeMillis()
        val response1 = service.processUserInput(input)
        val time1 = System.currentTimeMillis() - start1
        
        println("Première requête:")
        println("Temps: ${time1}ms")
        println("Réponse: $response1\n")
        
        // Deuxième requête (avec cache)
        val start2 = System.currentTimeMillis()
        val response2 = service.processUserInput(input)
        val time2 = System.currentTimeMillis() - start2
        
        println("Deuxième requête (cache):")
        println("Temps: ${time2}ms")
        println("Réponse: $response2\n")
        
        println("Amélioration: ${(time1 - time2)}ms plus rapide avec le cache")
    }
    
    /**
     * Exemple 4 : Test de la personnalité KITT
     */
    @Test
    fun exampleKittPersonality() = runBlocking {
        `when`(mockSharedPreferences.getString("openai_api_key", null)).thenReturn(null)
        
        val service = KittAIService(mockContext)
        
        println("\n=== Test 4: Personnalité KITT ===")
        
        val personalityTests = mapOf(
            "Qui es-tu ?" to "Doit mentionner 'Knight Industries Two Thousand' ou 'KITT'",
            "Comment vas-tu ?" to "Doit mentionner 'systèmes' et 'opérationnel'",
            "Active le scanner" to "Doit mentionner 'scanner' et 'surveillance'",
            "Turbo boost" to "Doit mentionner 'turbo' et 'énergie'",
            "Merci" to "Doit répondre poliment"
        )
        
        personalityTests.forEach { (input, expected) ->
            val response = service.processUserInput(input)
            println("User: $input")
            println("KITT: $response")
            println("Attendu: $expected\n")
        }
    }
    
    /**
     * Exemple 5 : Vérification de la configuration
     */
    @Test
    fun exampleConfigurationCheck() {
        println("\n=== Test 5: Configuration ===")
        
        // Sans configuration
        `when`(mockSharedPreferences.getString("openai_api_key", null)).thenReturn(null)
        `when`(mockSharedPreferences.getString("anthropic_api_key", null)).thenReturn(null)
        `when`(mockSharedPreferences.getString("huggingface_api_key", null)).thenReturn(null)
        
        val service1 = KittAIService(mockContext)
        println("Sans API configurée:")
        println("isConfigured: ${service1.isConfigured()}")
        println("Status:\n${service1.getConfigurationStatus()}\n")
        
        // Avec OpenAI
        `when`(mockSharedPreferences.getString("openai_api_key", null)).thenReturn("sk-test123")
        
        val service2 = KittAIService(mockContext)
        println("Avec OpenAI configuré:")
        println("isConfigured: ${service2.isConfigured()}")
        println("Status:\n${service2.getConfigurationStatus()}\n")
    }
    
    /**
     * Exemple 6 : Gestion d'erreurs
     */
    @Test
    fun exampleErrorHandling() = runBlocking {
        `when`(mockSharedPreferences.getString("openai_api_key", null)).thenReturn(null)
        
        val service = KittAIService(mockContext)
        
        println("\n=== Test 6: Gestion d'erreurs ===")
        
        try {
            // Même avec des erreurs, le service doit toujours retourner une réponse (fallback)
            val response = service.processUserInput("Test d'erreur")
            println("Réponse reçue (fallback): $response")
            println("✓ Le service gère les erreurs correctement")
        } catch (e: Exception) {
            println("✗ Exception non gérée: ${e.message}")
        }
    }
    
    /**
     * Exemple 7 : Effacer le cache
     */
    @Test
    fun exampleClearCache() = runBlocking {
        `when`(mockSharedPreferences.getString("openai_api_key", null)).thenReturn(null)
        
        val service = KittAIService(mockContext)
        
        println("\n=== Test 7: Effacer le cache ===")
        
        // Remplir le cache
        service.processUserInput("Test 1")
        service.processUserInput("Test 2")
        service.processUserInput("Test 3")
        println("Cache rempli avec 3 entrées")
        
        // Effacer le cache
        service.clearCache()
        println("Cache effacé")
        
        // Les prochaines requêtes ne viendront pas du cache
        println("Les prochaines requêtes seront recalculées")
    }
    
    /**
     * Helper function pour les mocks
     */
    private fun <T> any(): T {
        return null as T
    }
}

/**
 * GUIDE D'UTILISATION PRATIQUE
 * 
 * Pour utiliser KittAIService dans votre application:
 * 
 * 1. INITIALISATION:
 * ```kotlin
 * val kittAIService = KittAIService(context)
 * ```
 * 
 * 2. VÉRIFIER LA CONFIGURATION:
 * ```kotlin
 * if (!kittAIService.isConfigured()) {
 *     // Demander à l'utilisateur de configurer les API
 *     showConfigurationDialog()
 * }
 * ```
 * 
 * 3. TRAITER UNE REQUÊTE:
 * ```kotlin
 * lifecycleScope.launch {
 *     try {
 *         val response = kittAIService.processUserInput(userInput)
 *         // Utiliser la réponse
 *         showMessage(response)
 *         speakWithTTS(response)
 *     } catch (e: Exception) {
 *         Log.e("KITT", "Error", e)
 *     }
 * }
 * ```
 * 
 * 4. CONFIGURER UNE CLÉ API:
 * ```kotlin
 * val prefs = context.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE)
 * prefs.edit().apply {
 *     putString("openai_api_key", "sk-...")
 *     apply()
 * }
 * ```
 * 
 * 5. EFFACER LE CACHE (optionnel):
 * ```kotlin
 * kittAIService.clearCache()
 * ```
 * 
 * 6. OBTENIR LE STATUT:
 * ```kotlin
 * val status = kittAIService.getConfigurationStatus()
 * Log.d("KITT", status)
 * ```
 */

