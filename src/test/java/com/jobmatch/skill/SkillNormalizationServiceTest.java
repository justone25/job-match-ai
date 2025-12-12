package com.jobmatch.skill;

import com.jobmatch.llm.LlmClient;
import com.jobmatch.llm.LlmRequest;
import com.jobmatch.llm.LlmResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for SkillNormalizationService.
 */
class SkillNormalizationServiceTest {

    private SkillDictionaryService dictionaryService;
    private LlmClient mockLlmClient;
    private SkillNormalizationService service;

    @BeforeEach
    void setUp() {
        dictionaryService = new SkillDictionaryService();
        mockLlmClient = mock(LlmClient.class);
        service = new SkillNormalizationService(dictionaryService, mockLlmClient);
    }

    @Test
    void testDictionaryHit_NoLlmCall() {
        // Dictionary hit should not call LLM
        SkillLookupResult result = service.normalize("SpringBoot");

        assertTrue(result.isFound());
        assertEquals("Spring Boot", result.getStandardName());
        assertEquals(SkillLookupResult.SOURCE_ALIAS, result.getMatchSource());
        assertEquals(1.0, result.getConfidence());

        // Verify LLM was never called
        verifyNoInteractions(mockLlmClient);
    }

    @Test
    void testDictionaryMiss_LlmFallback() throws Exception {
        // Setup mock to return normalized name
        LlmResponse mockResponse = LlmResponse.builder()
                .content("Spring Framework")
                .build();
        when(mockLlmClient.chat(any(LlmRequest.class))).thenReturn(mockResponse);

        // Use a skill not in dictionary
        SkillLookupResult result = service.normalize("spring框架");

        assertTrue(result.isFound());
        assertEquals("Spring Framework", result.getStandardName());
        assertEquals(SkillLookupResult.SOURCE_LLM, result.getMatchSource());
        assertEquals(0.8, result.getConfidence());

        // Verify LLM was called
        verify(mockLlmClient, times(1)).chat(any(LlmRequest.class));
    }

    @Test
    void testLlmReturnsUnknown() throws Exception {
        LlmResponse mockResponse = LlmResponse.builder()
                .content("UNKNOWN")
                .build();
        when(mockLlmClient.chat(any(LlmRequest.class))).thenReturn(mockResponse);

        SkillLookupResult result = service.normalize("xyztechnology123");

        assertFalse(result.isFound());
        assertEquals("xyztechnology123", result.getStandardName());
        assertEquals(SkillLookupResult.SOURCE_NONE, result.getMatchSource());
    }

    @Test
    void testNullInput() {
        SkillLookupResult result = service.normalize(null);
        assertFalse(result.isFound());
        verifyNoInteractions(mockLlmClient);
    }

    @Test
    void testEmptyInput() {
        SkillLookupResult result = service.normalize("   ");
        assertFalse(result.isFound());
        verifyNoInteractions(mockLlmClient);
    }

    @Test
    void testNormalizeDictionaryOnly() {
        // Should not call LLM even for unknown skill
        SkillLookupResult result = service.normalizeDictionaryOnly("unknownskill123");

        assertFalse(result.isFound());
        verifyNoInteractions(mockLlmClient);
    }

    @Test
    void testLlmCaching() throws Exception {
        LlmResponse mockResponse = LlmResponse.builder()
                .content("New Technology")
                .build();
        when(mockLlmClient.chat(any(LlmRequest.class))).thenReturn(mockResponse);

        // First call
        service.normalize("newtechnology");
        // Second call with same skill
        service.normalize("newtechnology");
        // Third call with different case
        service.normalize("NewTechnology");

        // LLM should only be called once due to caching
        verify(mockLlmClient, times(1)).chat(any(LlmRequest.class));
    }

    @Test
    void testBatchNormalize() {
        List<SkillLookupResult> results = service.normalizeAll(
                List.of("SpringBoot", "K8s", "redis"));

        assertEquals(3, results.size());
        assertEquals("Spring Boot", results.get(0).getStandardName());
        assertEquals("Kubernetes", results.get(1).getStandardName());
        assertEquals("Redis", results.get(2).getStandardName());

        verifyNoInteractions(mockLlmClient);
    }

    @Test
    void testStandardize() {
        assertEquals("Spring Boot", service.standardize("springboot"));
        assertEquals("JavaScript", service.standardize("js"));
        assertEquals("Kubernetes", service.standardize("k8s"));
    }

    @Test
    void testClearCache() throws Exception {
        LlmResponse mockResponse = LlmResponse.builder()
                .content("Cached Skill")
                .build();
        when(mockLlmClient.chat(any(LlmRequest.class))).thenReturn(mockResponse);

        // First call
        service.normalize("cachedskill");
        assertEquals(1, service.getLlmCacheSize());

        // Clear and verify
        service.clearCache();
        assertEquals(0, service.getLlmCacheSize());

        // Call again - should hit LLM
        service.normalize("cachedskill");
        verify(mockLlmClient, times(2)).chat(any(LlmRequest.class));
    }

    @Test
    void testServiceWithoutLlmClient() {
        SkillNormalizationService noLlmService =
                new SkillNormalizationService(dictionaryService, null);

        // Dictionary hit works
        SkillLookupResult result1 = noLlmService.normalize("SpringBoot");
        assertTrue(result1.isFound());
        assertEquals("Spring Boot", result1.getStandardName());

        // Dictionary miss returns original
        SkillLookupResult result2 = noLlmService.normalize("unknownskill");
        assertFalse(result2.isFound());
        assertEquals("unknownskill", result2.getStandardName());
    }

    @Test
    void testLlmResponseWithQuotes() throws Exception {
        LlmResponse mockResponse = LlmResponse.builder()
                .content("\"Spring Boot\"")
                .build();
        when(mockLlmClient.chat(any(LlmRequest.class))).thenReturn(mockResponse);

        SkillLookupResult result = service.normalize("springboot框架");

        assertEquals("Spring Boot", result.getStandardName());
    }

    @Test
    void testLlmResponseMultiline() throws Exception {
        LlmResponse mockResponse = LlmResponse.builder()
                .content("Spring Boot\nThis is the standard name for SpringBoot.")
                .build();
        when(mockLlmClient.chat(any(LlmRequest.class))).thenReturn(mockResponse);

        SkillLookupResult result = service.normalize("springboot开发");

        assertEquals("Spring Boot", result.getStandardName());
    }

    @Test
    void testLlmResultCategory() throws Exception {
        // LLM returns a skill that is in dictionary
        LlmResponse mockResponse = LlmResponse.builder()
                .content("Java")
                .build();
        when(mockLlmClient.chat(any(LlmRequest.class))).thenReturn(mockResponse);

        SkillLookupResult result = service.normalize("Java编程");

        assertTrue(result.isFound());
        assertEquals("Java", result.getStandardName());
        assertEquals("backend", result.getCategory());  // Verified against dictionary
    }
}
