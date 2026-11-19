package com.example.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RabtaAiAssistantTest {

    @Test
    fun defaultModelIsGemini38FlashAndPresetsIncludeModernModels() {
        assertEquals("gemini-3.8-flash", RabtaAiAssistant.DEFAULT_MODEL_ID)
        val ids = RabtaAiAssistant.PRESET_MODELS.map { it.id }
        assertTrue(ids.contains("gemini-3.8-flash"))
        assertTrue(ids.contains("gemini-3.5-flash-lite"))
        assertTrue(ids.contains("gemini-4-argon"))
    }

    @Test
    fun candidateOrderPrioritizesPreferredModelThenFallbacksWithoutDuplicates() {
        assertEquals(
            listOf("gemini-4-argon", "gemini-3.8-flash", "gemini-3.5-flash-lite"),
            RabtaAiAssistant.candidateOrder("gemini-4-argon"),
        )
        assertEquals(
            listOf("gemini-3.8-flash", "gemini-3.5-flash-lite"),
            RabtaAiAssistant.candidateOrder("gemini-3.8-flash"),
        )
        assertEquals(
            listOf("gemini-3.5-flash-lite", "gemini-3.8-flash"),
            RabtaAiAssistant.candidateOrder("gemini-3.5-flash-lite"),
        )
        assertEquals(
            listOf("gemini-3.8-flash", "gemini-3.5-flash-lite"),
            RabtaAiAssistant.candidateOrder("   "),
        )
    }

    @Test
    fun retryableWithAlternateModelDetectsModelAndQuotaErrorsOnly() {
        assertTrue(RabtaAiAssistant.isRetryableWithAlternateModel(IllegalStateException("429 RESOURCE_EXHAUSTED quota exceeded")))
        assertTrue(RabtaAiAssistant.isRetryableWithAlternateModel(IllegalStateException("Model not found (404)")))
        assertTrue(RabtaAiAssistant.isRetryableWithAlternateModel(IllegalStateException("503 Service unavailable")))
        assertFalse(RabtaAiAssistant.isRetryableWithAlternateModel(IllegalStateException("App Check token is invalid (403)")))
    }

    @Test
    fun parseIsoDateToEpochMsHandlesSupportedFormatsAndRejectsBlank() {
        assertNull(RabtaAiAssistant.parseIsoDateToEpochMs(""))
        assertNotNull(RabtaAiAssistant.parseIsoDateToEpochMs("2026-11-18 14:30"))
        assertNotNull(RabtaAiAssistant.parseIsoDateToEpochMs("2026-11-18"))
    }
}
