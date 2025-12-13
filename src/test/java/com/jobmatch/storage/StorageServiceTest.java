package com.jobmatch.storage;

import com.jobmatch.model.match.MatchReport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StorageService.
 */
class StorageServiceTest {

    @TempDir
    Path tempDir;

    private StorageService storageService;

    @BeforeEach
    void setUp() throws IOException {
        // Create a test config that uses temp directories
        Path dataDir = tempDir.resolve("data");
        Path cacheDir = tempDir.resolve("cache");
        Files.createDirectories(dataDir);
        Files.createDirectories(cacheDir);

        // Note: StorageService is a singleton, so we can't easily inject temp dirs
        // For this test, we'll use the actual StorageService but test feedback functionality
        storageService = StorageService.getInstance();
    }

    // ==================== Feedback Tests ====================

    @Test
    @DisplayName("Should save and retrieve feedback")
    void shouldSaveAndRetrieveFeedback() throws IOException {
        StorageService.Feedback feedback = StorageService.Feedback.builder()
                .analysisId("test-123")
                .rating(1)
                .comment("Very helpful")
                .timestamp(Instant.now())
                .build();

        storageService.saveFeedback(feedback);

        List<StorageService.Feedback> feedbacks = storageService.listFeedback();
        assertNotNull(feedbacks);
        // At least one feedback should exist (from this test)
        assertTrue(feedbacks.size() >= 1);
    }

    @Test
    @DisplayName("Should calculate feedback statistics")
    void shouldCalculateFeedbackStats() {
        StorageService.FeedbackStats stats = storageService.getFeedbackStats();

        assertNotNull(stats);
        assertTrue(stats.getTotal() >= 0);
        assertTrue(stats.getHelpful() >= 0);
        assertTrue(stats.getNeutral() >= 0);
        assertTrue(stats.getNotHelpful() >= 0);
    }

    @Test
    @DisplayName("Should calculate helpful rate correctly")
    void shouldCalculateHelpfulRate() {
        StorageService.FeedbackStats stats = StorageService.FeedbackStats.builder()
                .total(10)
                .helpful(7)
                .neutral(2)
                .notHelpful(1)
                .build();

        assertEquals(70.0, stats.getHelpfulRate(), 0.01);
    }

    @Test
    @DisplayName("Should handle zero total for helpful rate")
    void shouldHandleZeroTotalForHelpfulRate() {
        StorageService.FeedbackStats stats = StorageService.FeedbackStats.builder()
                .total(0)
                .helpful(0)
                .neutral(0)
                .notHelpful(0)
                .build();

        assertEquals(0.0, stats.getHelpfulRate(), 0.01);
    }

    // ==================== Cache Tests ====================

    @Test
    @DisplayName("Should get cache statistics")
    void shouldGetCacheStats() {
        StorageService.CacheStats stats = storageService.getCacheStats();

        assertNotNull(stats);
        assertNotNull(stats.getCacheDir());
        assertTrue(stats.getFileCount() >= 0);
        assertTrue(stats.getTotalSizeBytes() >= 0);
    }

    @Test
    @DisplayName("Should format total size in MB")
    void shouldFormatTotalSizeInMB() {
        StorageService.CacheStats stats = StorageService.CacheStats.builder()
                .fileCount(10)
                .totalSizeBytes(5 * 1024 * 1024) // 5 MB
                .expiredCount(0)
                .cacheDir("/tmp/cache")
                .enabled(true)
                .ttlDays(7)
                .build();

        assertEquals("5.00", stats.getTotalSizeMb());
    }

    // ==================== History Tests ====================

    @Test
    @DisplayName("Should list history entries")
    void shouldListHistoryEntries() {
        List<StorageService.HistoryEntry> entries = storageService.listHistory();
        assertNotNull(entries);
        // List may be empty or have entries from previous tests
    }

    @Test
    @DisplayName("Should return empty optional for non-existent report")
    void shouldReturnEmptyForNonExistentReport() {
        Optional<MatchReport> result = storageService.loadReport("non-existent-id");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should delete non-existent report gracefully")
    void shouldDeleteNonExistentReportGracefully() {
        boolean result = storageService.deleteReport("non-existent-id");
        assertFalse(result);
    }

    // ==================== HistoryEntry Tests ====================

    @Test
    @DisplayName("Should create history entry with builder")
    void shouldCreateHistoryEntry() {
        Instant now = Instant.now();
        StorageService.HistoryEntry entry = StorageService.HistoryEntry.builder()
                .id("20231212_120000")
                .timestamp(now)
                .matchLevel("Good Match")
                .score(85)
                .oneLine("Skills match well")
                .build();

        assertEquals("20231212_120000", entry.getId());
        assertEquals(now, entry.getTimestamp());
        assertEquals("Good Match", entry.getMatchLevel());
        assertEquals(85, entry.getScore());
        assertEquals("Skills match well", entry.getOneLine());
    }

    // ==================== Feedback Data Class Tests ====================

    @Test
    @DisplayName("Should create feedback with builder")
    void shouldCreateFeedback() {
        Instant now = Instant.now();
        StorageService.Feedback feedback = StorageService.Feedback.builder()
                .analysisId("test-123")
                .rating(1)
                .comment("Great analysis")
                .timestamp(now)
                .build();

        assertEquals("test-123", feedback.getAnalysisId());
        assertEquals(1, feedback.getRating());
        assertEquals("Great analysis", feedback.getComment());
        assertEquals(now, feedback.getTimestamp());
    }
}
