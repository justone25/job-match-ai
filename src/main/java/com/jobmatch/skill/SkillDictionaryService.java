package com.jobmatch.skill;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for loading and querying skill dictionary.
 * Based on PRD v3.2 section 13.3.2 standardization flow.
 *
 * Lookup priority:
 * 1. Exact match in aliases
 * 2. Case-insensitive match in aliases
 * 3. Exact match in translations
 * 4. Case-insensitive match in translations
 * 5. Exact match in category skills
 * 6. Not found (return original)
 */
public class SkillDictionaryService {

    private static final Logger log = LoggerFactory.getLogger(SkillDictionaryService.class);
    private static final String DEFAULT_DICTIONARY_PATH = "/skills_dictionary.yaml";

    private final SkillDictionary dictionary;

    // Indexes for fast lookup
    private final Map<String, String> aliasIndex;
    private final Map<String, String> aliasLowerIndex;
    private final Map<String, String> translationIndex;
    private final Map<String, String> translationLowerIndex;
    private final Set<String> standardNames;
    private final Map<String, String> skillToCategory;

    // Cache for lookup results
    private final Map<String, SkillLookupResult> lookupCache;

    private static volatile SkillDictionaryService instance;

    /**
     * Get singleton instance with default dictionary.
     */
    public static SkillDictionaryService getInstance() {
        if (instance == null) {
            synchronized (SkillDictionaryService.class) {
                if (instance == null) {
                    instance = new SkillDictionaryService();
                }
            }
        }
        return instance;
    }

    /**
     * Create service with default dictionary from classpath.
     */
    public SkillDictionaryService() {
        this(DEFAULT_DICTIONARY_PATH);
    }

    /**
     * Create service with dictionary from specified classpath resource.
     */
    public SkillDictionaryService(String resourcePath) {
        this.dictionary = loadDictionary(resourcePath);
        this.aliasIndex = new HashMap<>();
        this.aliasLowerIndex = new HashMap<>();
        this.translationIndex = new HashMap<>();
        this.translationLowerIndex = new HashMap<>();
        this.standardNames = new HashSet<>();
        this.skillToCategory = new HashMap<>();
        this.lookupCache = new ConcurrentHashMap<>();

        buildIndexes();

        log.info("Skill dictionary loaded: {} aliases, {} translations, {} categories",
                dictionary.getAliasCount(),
                dictionary.getTranslationCount(),
                dictionary.getCategoryCount());
    }

    /**
     * Look up a skill name and return standardized result.
     *
     * @param skillName raw skill name to look up
     * @return lookup result with standardized name and metadata
     */
    public SkillLookupResult lookup(String skillName) {
        if (skillName == null || skillName.trim().isEmpty()) {
            return SkillLookupResult.notFound(skillName);
        }

        // Preprocess
        String trimmed = preprocess(skillName);

        // Check cache
        SkillLookupResult cached = lookupCache.get(trimmed);
        if (cached != null) {
            return cached;
        }

        // Perform lookup
        SkillLookupResult result = doLookup(trimmed, skillName);

        // Find category if result found
        if (result.isFound() && result.getCategory() == null) {
            result.setCategory(findCategory(result.getStandardName()));
        }

        // Cache result
        lookupCache.put(trimmed, result);

        return result;
    }

    /**
     * Standardize a skill name, returning just the standard name.
     *
     * @param skillName raw skill name
     * @return standardized name (original if not found)
     */
    public String standardize(String skillName) {
        return lookup(skillName).getStandardName();
    }

    /**
     * Batch lookup multiple skills.
     */
    public List<SkillLookupResult> lookupAll(List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) {
            return Collections.emptyList();
        }

        List<SkillLookupResult> results = new ArrayList<>(skillNames.size());
        for (String name : skillNames) {
            results.add(lookup(name));
        }
        return results;
    }

    /**
     * Check if a skill name is known in the dictionary.
     */
    public boolean isKnown(String skillName) {
        return lookup(skillName).isFound();
    }

    /**
     * Get all skills in a category.
     */
    public List<String> getSkillsByCategory(String category) {
        return dictionary.getSkillsByCategory(category);
    }

    /**
     * Get all available categories.
     */
    public Set<String> getCategories() {
        return dictionary.getCategories() != null
                ? dictionary.getCategories().keySet()
                : Collections.emptySet();
    }

    /**
     * Get proficiency level from keywords.
     */
    public String detectProficiencyLevel(String description) {
        if (description == null || description.isEmpty()) {
            return null;
        }

        String lower = description.toLowerCase();

        Map<String, List<String>> keywords = dictionary.getProficiencyKeywords();
        if (keywords == null) {
            return null;
        }

        // Check in order: expert, proficient, familiar, beginner
        String[] levels = {"expert", "proficient", "familiar", "beginner"};
        for (String level : levels) {
            List<String> levelKeywords = keywords.get(level);
            if (levelKeywords != null) {
                for (String keyword : levelKeywords) {
                    if (lower.contains(keyword.toLowerCase())) {
                        return level;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Get dictionary statistics.
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("version", dictionary.getVersion());
        stats.put("updatedAt", dictionary.getUpdatedAt());
        stats.put("aliasCount", dictionary.getAliasCount());
        stats.put("translationCount", dictionary.getTranslationCount());
        stats.put("categoryCount", dictionary.getCategoryCount());
        stats.put("standardNameCount", standardNames.size());
        stats.put("cacheSize", lookupCache.size());
        return stats;
    }

    /**
     * Clear the lookup cache.
     */
    public void clearCache() {
        lookupCache.clear();
        log.debug("Lookup cache cleared");
    }

    /**
     * Get the underlying dictionary.
     */
    public SkillDictionary getDictionary() {
        return dictionary;
    }

    // Private methods

    private SkillLookupResult doLookup(String trimmed, String original) {
        // 1. Exact match in aliases
        String standard = aliasIndex.get(trimmed);
        if (standard != null) {
            return SkillLookupResult.found(original, standard, SkillLookupResult.SOURCE_ALIAS);
        }

        // 2. Case-insensitive match in aliases
        String lower = trimmed.toLowerCase();
        standard = aliasLowerIndex.get(lower);
        if (standard != null) {
            return SkillLookupResult.found(original, standard, SkillLookupResult.SOURCE_ALIAS);
        }

        // 3. Exact match in translations
        standard = translationIndex.get(trimmed);
        if (standard != null) {
            return SkillLookupResult.found(original, standard, SkillLookupResult.SOURCE_TRANSLATION);
        }

        // 4. Case-insensitive match in translations
        standard = translationLowerIndex.get(lower);
        if (standard != null) {
            return SkillLookupResult.found(original, standard, SkillLookupResult.SOURCE_TRANSLATION);
        }

        // 5. Check if it's already a standard name
        if (standardNames.contains(trimmed)) {
            return SkillLookupResult.found(original, trimmed, SkillLookupResult.SOURCE_EXACT);
        }

        // 5b. Case-insensitive standard name check
        for (String std : standardNames) {
            if (std.equalsIgnoreCase(trimmed)) {
                return SkillLookupResult.found(original, std, SkillLookupResult.SOURCE_EXACT);
            }
        }

        // Not found
        return SkillLookupResult.notFound(original);
    }

    private String preprocess(String skillName) {
        // Trim whitespace
        String result = skillName.trim();

        // Normalize multiple spaces to single space
        result = result.replaceAll("\\s+", " ");

        return result;
    }

    private String findCategory(String standardName) {
        return skillToCategory.get(standardName);
    }

    private void buildIndexes() {
        // Build alias indexes
        if (dictionary.getAliases() != null) {
            for (Map.Entry<String, String> entry : dictionary.getAliases().entrySet()) {
                String alias = entry.getKey();
                String standard = entry.getValue();

                aliasIndex.put(alias, standard);
                aliasLowerIndex.put(alias.toLowerCase(), standard);
                standardNames.add(standard);
            }
        }

        // Build translation indexes
        if (dictionary.getTranslations() != null) {
            for (Map.Entry<String, String> entry : dictionary.getTranslations().entrySet()) {
                String chinese = entry.getKey();
                String english = entry.getValue();

                translationIndex.put(chinese, english);
                translationLowerIndex.put(chinese.toLowerCase(), english);
                standardNames.add(english);
            }
        }

        // Build category index
        if (dictionary.getCategories() != null) {
            for (Map.Entry<String, List<String>> entry : dictionary.getCategories().entrySet()) {
                String category = entry.getKey();
                List<String> skills = entry.getValue();
                if (skills != null) {
                    for (String skill : skills) {
                        skillToCategory.put(skill, category);
                        standardNames.add(skill);
                    }
                }
            }
        }

        log.debug("Indexes built: {} alias entries, {} translation entries, {} standard names",
                aliasIndex.size(), translationIndex.size(), standardNames.size());
    }

    private SkillDictionary loadDictionary(String resourcePath) {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.warn("Dictionary not found at {}, using empty dictionary", resourcePath);
                return new SkillDictionary();
            }
            return yamlMapper.readValue(is, SkillDictionary.class);
        } catch (IOException e) {
            log.error("Failed to load dictionary from {}: {}", resourcePath, e.getMessage());
            return new SkillDictionary();
        }
    }
}
