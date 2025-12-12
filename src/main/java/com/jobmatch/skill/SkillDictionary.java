package com.jobmatch.skill;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data model for skills dictionary YAML file.
 * Based on PRD v3.2 section 13.3.1.
 */
@Data
public class SkillDictionary {

    /**
     * Dictionary version.
     */
    private String version;

    /**
     * Last updated date.
     */
    private String updatedAt;

    /**
     * Alias mappings: variant -> standard name.
     * e.g., "SpringBoot" -> "Spring Boot"
     */
    private Map<String, String> aliases = new HashMap<>();

    /**
     * Chinese to English translations.
     * e.g., "消息队列" -> "Message Queue"
     */
    private Map<String, String> translations = new HashMap<>();

    /**
     * Skill categories by domain.
     */
    private Map<String, List<String>> categories = new HashMap<>();

    /**
     * Proficiency keywords for level detection.
     */
    private Map<String, List<String>> proficiencyKeywords = new HashMap<>();

    /**
     * Get total number of alias entries.
     */
    public int getAliasCount() {
        return aliases != null ? aliases.size() : 0;
    }

    /**
     * Get total number of translation entries.
     */
    public int getTranslationCount() {
        return translations != null ? translations.size() : 0;
    }

    /**
     * Get total number of categories.
     */
    public int getCategoryCount() {
        return categories != null ? categories.size() : 0;
    }

    /**
     * Get all skills in a category.
     */
    public List<String> getSkillsByCategory(String category) {
        return categories != null ? categories.get(category) : null;
    }
}
