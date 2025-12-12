package com.jobmatch.skill;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SkillDictionaryService.
 */
class SkillDictionaryServiceTest {

    private static SkillDictionaryService service;

    @BeforeAll
    static void setUp() {
        service = new SkillDictionaryService();
    }

    @Test
    void testLoadDictionary() {
        Map<String, Object> stats = service.getStats();
        assertNotNull(stats);
        assertTrue((int) stats.get("aliasCount") > 100, "Should have 100+ aliases");
        assertTrue((int) stats.get("translationCount") > 30, "Should have 30+ translations");
        assertTrue((int) stats.get("categoryCount") > 5, "Should have 5+ categories");
    }

    @Test
    void testAliasLookup_ExactMatch() {
        SkillLookupResult result = service.lookup("SpringBoot");
        assertTrue(result.isFound());
        assertEquals("Spring Boot", result.getStandardName());
        assertEquals(SkillLookupResult.SOURCE_ALIAS, result.getMatchSource());
        assertEquals(1.0, result.getConfidence());
    }

    @Test
    void testAliasLookup_CaseInsensitive() {
        SkillLookupResult result = service.lookup("springboot");
        assertTrue(result.isFound());
        assertEquals("Spring Boot", result.getStandardName());
    }

    @Test
    void testAliasLookup_K8s() {
        SkillLookupResult result = service.lookup("K8s");
        assertTrue(result.isFound());
        assertEquals("Kubernetes", result.getStandardName());
    }

    @Test
    void testAliasLookup_JavaScript() {
        SkillLookupResult result = service.lookup("JS");
        assertTrue(result.isFound());
        assertEquals("JavaScript", result.getStandardName());
    }

    @Test
    void testTranslationLookup() {
        SkillLookupResult result = service.lookup("消息队列");
        assertTrue(result.isFound());
        assertEquals("Message Queue", result.getStandardName());
        assertEquals(SkillLookupResult.SOURCE_TRANSLATION, result.getMatchSource());
    }

    @Test
    void testTranslationLookup_Microservices() {
        SkillLookupResult result = service.lookup("微服务");
        assertTrue(result.isFound());
        assertEquals("Microservices", result.getStandardName());
    }

    @Test
    void testStandardNameMatch() {
        // A skill that is already the standard name (in categories)
        SkillLookupResult result = service.lookup("Java");
        assertTrue(result.isFound());
        assertEquals("Java", result.getStandardName());
    }

    @Test
    void testNotFound() {
        SkillLookupResult result = service.lookup("SomeUnknownTech123");
        assertFalse(result.isFound());
        assertEquals("SomeUnknownTech123", result.getStandardName());
        assertEquals(SkillLookupResult.SOURCE_NONE, result.getMatchSource());
    }

    @Test
    void testNullInput() {
        SkillLookupResult result = service.lookup(null);
        assertFalse(result.isFound());
    }

    @Test
    void testEmptyInput() {
        SkillLookupResult result = service.lookup("  ");
        assertFalse(result.isFound());
    }

    @Test
    void testStandardize() {
        assertEquals("Spring Boot", service.standardize("springboot"));
        assertEquals("Kubernetes", service.standardize("k8s"));
        assertEquals("JavaScript", service.standardize("js"));
    }

    @Test
    void testBatchLookup() {
        List<SkillLookupResult> results = service.lookupAll(List.of("SpringBoot", "K8s", "消息队列"));
        assertEquals(3, results.size());
        assertEquals("Spring Boot", results.get(0).getStandardName());
        assertEquals("Kubernetes", results.get(1).getStandardName());
        assertEquals("Message Queue", results.get(2).getStandardName());
    }

    @Test
    void testIsKnown() {
        assertTrue(service.isKnown("SpringBoot"));
        assertTrue(service.isKnown("微服务"));
        assertFalse(service.isKnown("RandomTech999"));
    }

    @Test
    void testGetCategories() {
        Set<String> categories = service.getCategories();
        assertNotNull(categories);
        assertTrue(categories.contains("backend"));
        assertTrue(categories.contains("frontend"));
        assertTrue(categories.contains("database"));
    }

    @Test
    void testGetSkillsByCategory() {
        List<String> backendSkills = service.getSkillsByCategory("backend");
        assertNotNull(backendSkills);
        assertTrue(backendSkills.contains("Java"));
        assertTrue(backendSkills.contains("Spring Boot"));
        assertTrue(backendSkills.contains("Python"));
    }

    @Test
    void testCategoryInResult() {
        SkillLookupResult result = service.lookup("SpringBoot");
        assertEquals("backend", result.getCategory());
    }

    @Test
    void testDetectProficiencyLevel_Expert() {
        String level = service.detectProficiencyLevel("精通Java开发");
        assertEquals("expert", level);
    }

    @Test
    void testDetectProficiencyLevel_Proficient() {
        String level = service.detectProficiencyLevel("熟练掌握Python");
        assertEquals("proficient", level);
    }

    @Test
    void testDetectProficiencyLevel_Familiar() {
        String level = service.detectProficiencyLevel("了解Docker容器技术");
        assertEquals("familiar", level);
    }

    @Test
    void testDetectProficiencyLevel_Null() {
        String level = service.detectProficiencyLevel("使用Spring开发过项目");
        assertNull(level);
    }

    @Test
    void testPreprocessing_Whitespace() {
        SkillLookupResult result = service.lookup("  SpringBoot  ");
        assertTrue(result.isFound());
        assertEquals("Spring Boot", result.getStandardName());
    }

    @Test
    void testCaching() {
        // Clear and check
        service.clearCache();
        Map<String, Object> statsBefore = service.getStats();
        assertEquals(0, statsBefore.get("cacheSize"));

        // Lookup twice
        service.lookup("SpringBoot");
        service.lookup("SpringBoot");

        Map<String, Object> statsAfter = service.getStats();
        assertEquals(1, statsAfter.get("cacheSize"));
    }

    @Test
    void testDatabaseSkills() {
        assertEquals("MySQL", service.standardize("mysql"));
        assertEquals("PostgreSQL", service.standardize("postgres"));
        assertEquals("Redis", service.standardize("redis"));
        assertEquals("MongoDB", service.standardize("mongo"));
    }

    @Test
    void testCloudSkills() {
        assertEquals("Amazon Web Services", service.standardize("AWS"));
        assertEquals("Google Cloud Platform", service.standardize("GCP"));
        assertEquals("Microsoft Azure", service.standardize("azure"));
        assertEquals("Alibaba Cloud", service.standardize("阿里云"));
    }

    @Test
    void testBigDataSkills() {
        assertEquals("Apache Kafka", service.standardize("kafka"));
        assertEquals("Apache Spark", service.standardize("spark"));
        assertEquals("Apache Hadoop", service.standardize("hadoop"));
    }
}
