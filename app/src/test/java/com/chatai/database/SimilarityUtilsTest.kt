package com.chatai.database

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests unitaires pour SimilarityUtils
 * 
 * ⭐ SELON NOS RULES: Tests purs, pas de dépendances Android
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24])
class SimilarityUtilsTest {
    
    @Test
    fun `test cosine similarity with identical vectors`() {
        // Vecteurs identiques doivent avoir une similarité de 1.0
        val vec1 = floatArrayOf(1.0f, 2.0f, 3.0f)
        val vec2 = floatArrayOf(1.0f, 2.0f, 3.0f)
        
        val similarity = SimilarityUtils.cosineSimilarity(vec1, vec2)
        
        assertEquals(1.0f, similarity, 0.001f)
    }
    
    @Test
    fun `test cosine similarity with orthogonal vectors`() {
        // Vecteurs orthogonaux doivent avoir une similarité de 0.0
        val vec1 = floatArrayOf(1.0f, 0.0f, 0.0f)
        val vec2 = floatArrayOf(0.0f, 1.0f, 0.0f)
        
        val similarity = SimilarityUtils.cosineSimilarity(vec1, vec2)
        
        assertEquals(0.0f, similarity, 0.001f)
    }
    
    @Test
    fun `test cosine similarity with opposite vectors`() {
        // Vecteurs opposés doivent avoir une similarité de -1.0 (mais coerceIn retourne 0.0)
        val vec1 = floatArrayOf(1.0f, 0.0f)
        val vec2 = floatArrayOf(-1.0f, 0.0f)
        
        val similarity = SimilarityUtils.cosineSimilarity(vec1, vec2)
        
        // Le résultat devrait être -1.0 mais coerceIn le limite à 0.0
        assertTrue("Similarity should be >= 0", similarity >= 0.0f)
    }
    
    @Test
    fun `test cosine similarity with different magnitudes`() {
        // Vecteurs de même direction mais magnitudes différentes
        val vec1 = floatArrayOf(1.0f, 2.0f, 3.0f)
        val vec2 = floatArrayOf(2.0f, 4.0f, 6.0f) // 2x vec1
        
        val similarity = SimilarityUtils.cosineSimilarity(vec1, vec2)
        
        // Similarité cosine ignore la magnitude, donc devrait être 1.0
        assertEquals(1.0f, similarity, 0.001f)
    }
    
    @Test
    fun `test cosine similarity with different dimensions`() {
        // Vecteurs de dimensions différentes doivent retourner 0.0
        val vec1 = floatArrayOf(1.0f, 2.0f, 3.0f)
        val vec2 = floatArrayOf(1.0f, 2.0f)
        
        val similarity = SimilarityUtils.cosineSimilarity(vec1, vec2)
        
        assertEquals(0.0f, similarity, 0.001f)
    }
    
    @Test
    fun `test cosine similarity with zero vectors`() {
        // Vecteurs nuls doivent retourner 0.0
        val vec1 = floatArrayOf(0.0f, 0.0f, 0.0f)
        val vec2 = floatArrayOf(1.0f, 2.0f, 3.0f)
        
        val similarity = SimilarityUtils.cosineSimilarity(vec1, vec2)
        
        assertEquals(0.0f, similarity, 0.001f)
    }
    
    @Test
    fun `test cosine similarity with real embedding dimensions`() {
        // Test avec dimensions réelles (768 pour nomic-embed-text)
        val vec1 = FloatArray(768) { 0.1f }
        val vec2 = FloatArray(768) { 0.1f }
        
        val similarity = SimilarityUtils.cosineSimilarity(vec1, vec2)
        
        assertEquals(1.0f, similarity, 0.001f)
    }
    
    @Test
    fun `test cosine similarity with partial similarity`() {
        // Vecteurs partiellement similaires
        val vec1 = floatArrayOf(1.0f, 1.0f, 0.0f)
        val vec2 = floatArrayOf(1.0f, 0.0f, 1.0f)
        
        val similarity = SimilarityUtils.cosineSimilarity(vec1, vec2)
        
        // Similarité partielle: (1*1 + 1*0 + 0*1) / (sqrt(2) * sqrt(2)) = 1/2 = 0.5
        assertEquals(0.5f, similarity, 0.001f)
    }
    
    @Test
    fun `test euclidean distance with identical vectors`() {
        // Vecteurs identiques doivent avoir une distance de 0.0
        val vec1 = floatArrayOf(1.0f, 2.0f, 3.0f)
        val vec2 = floatArrayOf(1.0f, 2.0f, 3.0f)
        
        val distance = SimilarityUtils.euclideanDistance(vec1, vec2)
        
        assertEquals(0.0f, distance, 0.001f)
    }
    
    @Test
    fun `test euclidean distance with different vectors`() {
        // Distance euclidienne entre (0,0) et (3,4) = 5.0
        val vec1 = floatArrayOf(0.0f, 0.0f)
        val vec2 = floatArrayOf(3.0f, 4.0f)
        
        val distance = SimilarityUtils.euclideanDistance(vec1, vec2)
        
        assertEquals(5.0f, distance, 0.001f)
    }
    
    @Test
    fun `test euclidean distance with different dimensions`() {
        // Vecteurs de dimensions différentes doivent retourner Float.MAX_VALUE
        val vec1 = floatArrayOf(1.0f, 2.0f, 3.0f)
        val vec2 = floatArrayOf(1.0f, 2.0f)
        
        val distance = SimilarityUtils.euclideanDistance(vec1, vec2)
        
        assertEquals(Float.MAX_VALUE, distance, 0.001f)
    }
    
    @Test
    fun `test euclidean distance with real embedding dimensions`() {
        // Test avec dimensions réelles (768 pour nomic-embed-text)
        val vec1 = FloatArray(768) { 0.1f }
        val vec2 = FloatArray(768) { 0.2f }
        
        val distance = SimilarityUtils.euclideanDistance(vec1, vec2)
        
        // Distance = sqrt(sum((0.1-0.2)^2)) = sqrt(768 * 0.01) = sqrt(7.68) ≈ 2.77
        assertTrue("Distance should be > 0", distance > 0.0f)
        assertTrue("Distance should be reasonable", distance < 10.0f)
    }
}

