package com.chatai.services

import android.content.Context
import com.chatai.database.ConversationDao
import com.chatai.database.ConversationEntity
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

/**
 * Tests unitaires pour RAGService
 * 
 * ⭐ SELON NOS RULES: Tests avec mocks pour éviter dépendances Android réelles
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class RAGServiceTest {
    
    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockConversationDao: ConversationDao
    
    @Mock
    private lateinit var mockEmbeddingService: EmbeddingService
    
    private lateinit var ragService: RAGService
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        ragService = RAGService(mockContext, mockConversationDao, mockEmbeddingService)
    }
    
    @Test
    fun `test RAG service initialization`() {
        // Vérifier que le service est initialisé correctement
        assertNotNull(ragService)
    }
    
    @Test
    fun `test buildRAGContext with empty results`() {
        // Contexte vide doit retourner chaîne vide
        val emptyResults = emptyList<ConversationSearchResult>()
        
        val context = ragService.buildRAGContext(emptyResults)
        
        assertEquals("Context should be empty", "", context)
    }
    
    @Test
    fun `test buildRAGContext with single conversation`() {
        // Test construction contexte avec une conversation
        val conversation = ConversationEntity(
            conversationId = "test-1",
            userMessage = "Quelle heure est-il?",
            aiResponse = "Il est 14h30",
            thinkingTrace = null,
            personality = "KITT",
            apiUsed = "ollama_pc",
            responseTimeMs = 1000,
            platform = "webapp",
            sessionId = "session-1",
            timestamp = System.currentTimeMillis()
        )
        
        val searchResult = ConversationSearchResult(
            conversation = conversation,
            relevanceScore = 0.85f,
            matchedTerms = emptyList()
        )
        
        val context = ragService.buildRAGContext(listOf(searchResult))
        
        assertTrue("Context should not be empty", context.isNotEmpty())
        assertTrue("Context should contain user message", context.contains("Quelle heure est-il?"))
        assertTrue("Context should contain AI response", context.contains("Il est 14h30"))
        assertTrue("Context should contain relevance score", context.contains("0.85"))
    }
    
    @Test
    fun `test buildRAGContext limits to 5 conversations`() {
        // Test que le contexte limite à 5 conversations maximum
        val conversations = (1..10).map { i ->
            ConversationEntity(
                conversationId = "test-$i",
                userMessage = "Question $i",
                aiResponse = "Réponse $i",
                thinkingTrace = null,
                personality = "KITT",
                apiUsed = "ollama_pc",
                responseTimeMs = 1000,
                platform = "webapp",
                sessionId = "session-1",
                timestamp = System.currentTimeMillis()
            )
        }
        
        val searchResults = conversations.map { conv ->
            ConversationSearchResult(
                conversation = conv,
                relevanceScore = 0.8f,
                matchedTerms = emptyList()
            )
        }
        
        val context = ragService.buildRAGContext(searchResults)
        
        // Vérifier que seulement 5 conversations sont incluses
        // Le format est "Conversation X (Pertinence: Y):" où X commence à 1
        var conversationCount = 0
        for (i in 1..10) {
            if (context.contains("Conversation $i (")) {
                conversationCount++
            }
        }
        // Le contexte devrait limiter à 5 conversations (take(5) dans buildRAGContext)
        assertEquals("Context should limit to exactly 5 conversations", 5, conversationCount)
    }
    
    @Test
    fun `test buildRAGContext truncates long responses`() {
        // Test que les réponses longues sont tronquées
        val longResponse = "A".repeat(500) // 500 caractères
        val conversation = ConversationEntity(
            conversationId = "test-1",
            userMessage = "Question",
            aiResponse = longResponse,
            thinkingTrace = null,
            personality = "KITT",
            apiUsed = "ollama_pc",
            responseTimeMs = 1000,
            platform = "webapp",
            sessionId = "session-1",
            timestamp = System.currentTimeMillis()
        )
        
        val searchResult = ConversationSearchResult(
            conversation = conversation,
            relevanceScore = 0.85f,
            matchedTerms = emptyList()
        )
        
        val context = ragService.buildRAGContext(listOf(searchResult))
        
        // La réponse devrait être tronquée à 200 caractères + "..."
        assertTrue("Context should contain truncated response", context.contains("..."))
        val responsePart = context.substringAfter("R: ").substringBefore("\n")
        assertTrue("Response should be truncated", responsePart.length <= 203) // 200 + "..."
    }
    
    @Test
    fun `test buildRAGContext includes thinking trace if short`() {
        // Test que le thinking trace est inclus s'il est court
        val conversation = ConversationEntity(
            conversationId = "test-1",
            userMessage = "Question",
            aiResponse = "Réponse",
            thinkingTrace = "Raisonnement court",
            personality = "KITT",
            apiUsed = "ollama_pc",
            responseTimeMs = 1000,
            platform = "webapp",
            sessionId = "session-1",
            timestamp = System.currentTimeMillis()
        )
        
        val searchResult = ConversationSearchResult(
            conversation = conversation,
            relevanceScore = 0.85f,
            matchedTerms = emptyList()
        )
        
        val context = ragService.buildRAGContext(listOf(searchResult))
        
        assertTrue("Context should contain thinking trace", context.contains("Raisonnement"))
    }
    
    @Test
    fun `test buildRAGContext excludes long thinking trace`() {
        // Test que le thinking trace long est exclu
        val longThinking = "A".repeat(200) // 200 caractères (dépassement limite 150)
        val conversation = ConversationEntity(
            conversationId = "test-1",
            userMessage = "Question",
            aiResponse = "Réponse",
            thinkingTrace = longThinking,
            personality = "KITT",
            apiUsed = "ollama_pc",
            responseTimeMs = 1000,
            platform = "webapp",
            sessionId = "session-1",
            timestamp = System.currentTimeMillis()
        )
        
        val searchResult = ConversationSearchResult(
            conversation = conversation,
            relevanceScore = 0.85f,
            matchedTerms = emptyList()
        )
        
        val context = ragService.buildRAGContext(listOf(searchResult))
        
        // Le thinking trace ne devrait pas être inclus (trop long)
        assertFalse("Context should not contain long thinking trace", 
            context.contains("Raisonnement:"))
    }
    
    @Test
    fun `test buildRAGContext limits total length`() {
        // Test que le contexte total est limité à ~1500 caractères
        val conversations = (1..20).map { i ->
            ConversationEntity(
                conversationId = "test-$i",
                userMessage = "Question $i",
                aiResponse = "Réponse très longue ".repeat(50), // ~1000 caractères par réponse
                thinkingTrace = null,
                personality = "KITT",
                apiUsed = "ollama_pc",
                responseTimeMs = 1000,
                platform = "webapp",
                sessionId = "session-1",
                timestamp = System.currentTimeMillis()
            )
        }
        
        val searchResults = conversations.map { conv ->
            ConversationSearchResult(
                conversation = conv,
                relevanceScore = 0.8f,
                matchedTerms = emptyList()
            )
        }
        
        val context = ragService.buildRAGContext(searchResults)
        
        // Le contexte devrait être tronqué à ~1500 caractères
        assertTrue("Context should be truncated if too long", 
            context.length <= 1600) // 1500 + marge pour "[CONTEXTE TRONQUÉ]"
        if (context.length > 1500) {
            assertTrue("Context should indicate truncation", 
                context.contains("[CONTEXTE TRONQUÉ]"))
        }
    }
    
    @Test
    fun `test buildRAGContext formats correctly`() {
        // Test que le format du contexte est correct
        val conversation = ConversationEntity(
            conversationId = "test-1",
            userMessage = "Quelle heure est-il?",
            aiResponse = "Il est 14h30",
            thinkingTrace = null,
            personality = "KITT",
            apiUsed = "ollama_pc",
            responseTimeMs = 1000,
            platform = "webapp",
            sessionId = "session-1",
            timestamp = System.currentTimeMillis()
        )
        
        val searchResult = ConversationSearchResult(
            conversation = conversation,
            relevanceScore = 0.85f,
            matchedTerms = emptyList()
        )
        
        val context = ragService.buildRAGContext(listOf(searchResult))
        
        // Vérifier la structure
        assertTrue("Context should start with header", 
            context.contains("[CONTEXTE - Conversations similaires"))
        assertTrue("Context should contain Q: prefix", context.contains("Q:"))
        assertTrue("Context should contain R: prefix", context.contains("R:"))
        assertTrue("Context should end with footer", context.contains("[FIN CONTEXTE]"))
    }
}

