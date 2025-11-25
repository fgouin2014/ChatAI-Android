package com.chatai.services

import android.content.Context
import android.content.SharedPreferences
import com.chatai.services.EmbeddingService
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.test.runTest
import java.io.IOException

/**
 * Tests unitaires pour EmbeddingService
 * 
 * ⭐ SELON NOS RULES: Tests avec mocks pour éviter dépendances Android réelles
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class EmbeddingServiceTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockSharedPreferences: SharedPreferences
    
    private lateinit var embeddingService: EmbeddingService
    private lateinit var mockWebServer: MockWebServer
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        // Mock SharedPreferences
        `when`(mockContext.getSharedPreferences("chatai_ai_config", Context.MODE_PRIVATE))
            .thenReturn(mockSharedPreferences)
        
        // Configuration par défaut
        `when`(mockSharedPreferences.getBoolean("use_ollama_cloud", false)).thenReturn(false)
        `when`(mockSharedPreferences.getString("embedding_model", "nomic-embed-text"))
            .thenReturn("nomic-embed-text")
        
        // MockWebServer pour simuler les réponses HTTP
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Configurer l'URL du serveur local pour pointer vers MockWebServer
        val serverUrl = mockWebServer.url("/").toString().removeSuffix("/")
        `when`(mockSharedPreferences.getString("local_server_url", null))
            .thenReturn(serverUrl)
        
        embeddingService = EmbeddingService(mockContext)
    }
    
    @After
    fun tearDown() {
        try {
            mockWebServer.shutdown()
        } catch (e: IOException) {
            // Ignorer les erreurs de shutdown
        }
    }
    
    @Test
    fun `test embedding service initialization`() {
        // Vérifier que le service est initialisé correctement
        assertNotNull(embeddingService)
    }
    
    @Test
    fun `test isAvailable returns false when Ollama Cloud is enabled`() = runTest {
        // Si Ollama Cloud est activé, embeddings ne sont pas disponibles
        `when`(mockSharedPreferences.getBoolean("use_ollama_cloud", false)).thenReturn(true)
        
        val service = EmbeddingService(mockContext)
        val isAvailable = service.isAvailable()
        
        assertFalse("Embeddings should not be available with Ollama Cloud", isAvailable)
    }
    
    @Test
    fun `test isAvailable returns true when Ollama local responds`() = runTest {
        // Si Ollama local répond, embeddings sont disponibles
        `when`(mockSharedPreferences.getBoolean("use_ollama_cloud", false)).thenReturn(false)
        
        // Mock réponse /api/tags (ping)
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("{\"models\":[]}"))
        
        val service = EmbeddingService(mockContext)
        val isAvailable = service.isAvailable()
        
        assertTrue("Embeddings should be available when Ollama local responds", isAvailable)
    }
    
    @Test
    fun `test isAvailable returns false when Ollama local does not respond`() = runTest {
        // Si Ollama local ne répond pas, embeddings ne sont pas disponibles
        `when`(mockSharedPreferences.getBoolean("use_ollama_cloud", false)).thenReturn(false)
        
        // Mock réponse d'erreur
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"))
        
        val service = EmbeddingService(mockContext)
        val isAvailable = service.isAvailable()
        
        assertFalse("Embeddings should not be available when Ollama local does not respond", isAvailable)
    }
    
    @Test
    fun `test jsonToEmbedding with valid JSON`() {
        // Test conversion JSON → FloatArray
        val embedding = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val json = embeddingService.embeddingToJson(embedding)
        
        val result = embeddingService.jsonToEmbedding(json)
        
        assertNotNull("Embedding should not be null", result)
        assertEquals("Embedding size should match", embedding.size, result!!.size)
        assertArrayEquals("Embedding values should match", embedding, result, 0.001f)
    }
    
    @Test
    fun `test jsonToEmbedding with invalid JSON`() {
        // Test avec JSON invalide
        val invalidJson = "invalid json"
        
        val result = embeddingService.jsonToEmbedding(invalidJson)
        
        assertNull("Embedding should be null for invalid JSON", result)
    }
    
    @Test
    fun `test jsonToEmbedding with empty JSON`() {
        // Test avec JSON vide
        val emptyJson = "[]"
        
        val result = embeddingService.jsonToEmbedding(emptyJson)
        
        // JSON vide devrait retourner un FloatArray vide, pas null
        assertNotNull("Embedding should not be null for empty JSON", result)
        assertEquals("Embedding should be empty", 0, result!!.size)
    }
    
    @Test
    fun `test jsonToEmbedding with null JSON`() {
        // Test avec JSON null
        val nullJson = "null"
        
        val result = embeddingService.jsonToEmbedding(nullJson)
        
        assertNull("Embedding should be null for null JSON", result)
    }
    
    @Test
    fun `test embeddingToJson and jsonToEmbedding roundtrip`() {
        // Test roundtrip: FloatArray → JSON → FloatArray
        val original = FloatArray(768) { (it * 0.001f) }
        
        val json = embeddingService.embeddingToJson(original)
        val restored = embeddingService.jsonToEmbedding(json)
        
        assertNotNull("Restored embedding should not be null", restored)
        assertEquals("Restored embedding size should match", original.size, restored!!.size)
        assertArrayEquals("Restored embedding should match original", original, restored, 0.001f)
    }
    
    @Test
    fun `test embeddingToJson with real embedding dimensions`() {
        // Test avec dimensions réelles (768 pour nomic-embed-text)
        val embedding = FloatArray(768) { 0.1f }
        
        val json = embeddingService.embeddingToJson(embedding)
        
        assertNotNull("JSON should not be null", json)
        assertTrue("JSON should be valid", json.isNotEmpty())
        assertTrue("JSON should be array", json.startsWith("[") && json.endsWith("]"))
        
        // Vérifier que le JSON contient 768 valeurs
        val jsonArray = JSONArray(json)
        assertEquals("JSON should contain 768 values", 768, jsonArray.length())
    }
    
    @Test
    fun `test embed returns null for blank text`() = runTest {
        // Test que embed() retourne null pour texte vide
        val result = embeddingService.embed("")
        
        assertNull("Embedding should be null for blank text", result)
    }
    
    @Test
    fun `test embed returns embedding for valid text`() = runTest {
        // Test que embed() retourne un embedding valide
        // Mock réponse Ollama avec embedding
        val mockEmbedding = FloatArray(768) { 0.1f }
        val jsonArray = JSONArray()
        mockEmbedding.forEach { jsonArray.put(it.toDouble()) }
        
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("{\"embedding\":$jsonArray}"))
        
        val result = embeddingService.embed("Test text")
        
        assertNotNull("Embedding should not be null", result)
        assertEquals("Embedding should have 768 dimensions", 768, result!!.size)
    }
    
    @Test
    fun `test embed returns null on HTTP error`() = runTest {
        // Test que embed() retourne null en cas d'erreur HTTP
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(500)
            .setBody("Internal Server Error"))
        
        val result = embeddingService.embed("Test text")
        
        assertNull("Embedding should be null on HTTP error", result)
    }
    
    @Test
    fun `test embedConversation combines user and AI messages`() = runTest {
        // Test que embedConversation combine userMessage et aiResponse
        val mockEmbedding = FloatArray(768) { 0.1f }
        val jsonArray = JSONArray()
        mockEmbedding.forEach { jsonArray.put(it.toDouble()) }
        
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("{\"embedding\":$jsonArray}"))
        
        val userMessage = "Quelle heure est-il?"
        val aiResponse = "Il est 14h30"
        
        val result = embeddingService.embedConversation(userMessage, aiResponse)
        
        assertNotNull("Embedding should not be null", result)
        
        // Vérifier que la requête contient les deux messages
        val request = mockWebServer.takeRequest()
        val requestBody = request.body.readUtf8()
        assertTrue("Request should contain user message", requestBody.contains(userMessage))
        assertTrue("Request should contain AI response", requestBody.contains(aiResponse))
    }
}
