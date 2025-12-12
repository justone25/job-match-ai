package com.jobmatch.cli;

import com.jobmatch.skill.SkillDictionary;
import com.jobmatch.skill.SkillDictionaryService;
import com.jobmatch.skill.SkillLookupResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Command for managing and querying the skill dictionary.
 * Based on PRD v3.2 section 13.3.
 */
@Command(
        name = "dictionary",
        aliases = {"dict"},
        description = "Manage and query the skill dictionary",
        subcommands = {
                DictionaryCommand.LookupCommand.class,
                DictionaryCommand.StatsCommand.class,
                DictionaryCommand.CategoriesCommand.class,
                DictionaryCommand.SearchCommand.class
        }
)
public class DictionaryCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        // Default: show stats
        return new StatsCommand().call();
    }

    /**
     * Look up a skill and return its standardized name.
     */
    @Command(name = "lookup", aliases = {"l"}, description = "Look up a skill and get its standard name")
    static class LookupCommand implements Callable<Integer> {

        @Parameters(index = "0", arity = "1..*", description = "Skill name(s) to look up")
        private List<String> skills;

        @Option(names = {"-v", "--verbose"}, description = "Show detailed information")
        private boolean verbose;

        @Override
        public Integer call() {
            SkillDictionaryService service = SkillDictionaryService.getInstance();

            System.out.println();
            for (String skill : skills) {
                SkillLookupResult result = service.lookup(skill);

                if (verbose) {
                    System.out.println("Input:      " + result.getOriginalName());
                    System.out.println("Standard:   " + result.getStandardName());
                    System.out.println("Found:      " + result.isFound());
                    System.out.println("Source:     " + result.getMatchSource());
                    System.out.println("Category:   " + (result.getCategory() != null ? result.getCategory() : "-"));
                    System.out.println("Confidence: " + result.getConfidence());
                    System.out.println();
                } else {
                    String status = result.isFound() ? "✓" : "✗";
                    String category = result.getCategory() != null ? " [" + result.getCategory() + "]" : "";
                    System.out.println(String.format("[%s] %s → %s%s",
                            status, skill, result.getStandardName(), category));
                }
            }

            return 0;
        }
    }

    /**
     * Show dictionary statistics.
     */
    @Command(name = "stats", aliases = {"s"}, description = "Show dictionary statistics")
    static class StatsCommand implements Callable<Integer> {

        @Override
        public Integer call() {
            SkillDictionaryService service = SkillDictionaryService.getInstance();
            Map<String, Object> stats = service.getStats();

            System.out.println();
            System.out.println("Skill Dictionary Statistics");
            System.out.println("===========================");
            System.out.println();
            System.out.println("Version:           " + stats.get("version"));
            System.out.println("Updated:           " + stats.get("updatedAt"));
            System.out.println("Alias Entries:     " + stats.get("aliasCount"));
            System.out.println("Translations:      " + stats.get("translationCount"));
            System.out.println("Categories:        " + stats.get("categoryCount"));
            System.out.println("Standard Names:    " + stats.get("standardNameCount"));
            System.out.println("Cache Size:        " + stats.get("cacheSize"));
            System.out.println();

            return 0;
        }
    }

    /**
     * List all categories and their skills.
     */
    @Command(name = "categories", aliases = {"cat", "c"}, description = "List skill categories")
    static class CategoriesCommand implements Callable<Integer> {

        @Option(names = {"-l", "--list"}, description = "List skills in each category")
        private boolean listSkills;

        @Parameters(index = "0", arity = "0..1", description = "Category name to show")
        private String category;

        @Override
        public Integer call() {
            SkillDictionaryService service = SkillDictionaryService.getInstance();
            Set<String> categories = service.getCategories();

            System.out.println();

            if (category != null) {
                // Show specific category
                List<String> skills = service.getSkillsByCategory(category);
                if (skills == null || skills.isEmpty()) {
                    System.out.println("Category '" + category + "' not found.");
                    System.out.println();
                    System.out.println("Available categories: " + String.join(", ", categories));
                    return 1;
                }

                System.out.println("Category: " + category);
                System.out.println("Skills (" + skills.size() + "):");
                for (String skill : skills) {
                    System.out.println("  - " + skill);
                }
            } else if (listSkills) {
                // Show all categories with skills
                for (String cat : categories) {
                    List<String> skills = service.getSkillsByCategory(cat);
                    System.out.println(cat + " (" + (skills != null ? skills.size() : 0) + " skills):");
                    if (skills != null) {
                        for (String skill : skills) {
                            System.out.println("  - " + skill);
                        }
                    }
                    System.out.println();
                }
            } else {
                // Just list category names
                System.out.println("Skill Categories:");
                System.out.println("-----------------");
                for (String cat : categories) {
                    List<String> skills = service.getSkillsByCategory(cat);
                    int count = skills != null ? skills.size() : 0;
                    System.out.println(String.format("  %-20s %3d skills", cat, count));
                }
                System.out.println();
                System.out.println("Use 'dictionary categories -l' to list all skills");
                System.out.println("Use 'dictionary categories <name>' to show skills in a category");
            }

            System.out.println();
            return 0;
        }
    }

    /**
     * Search for skills matching a pattern.
     */
    @Command(name = "search", aliases = {"find"}, description = "Search for skills by pattern")
    static class SearchCommand implements Callable<Integer> {

        @Parameters(index = "0", description = "Search pattern (case-insensitive)")
        private String pattern;

        @Option(names = {"-a", "--aliases"}, description = "Search in aliases only")
        private boolean aliasesOnly;

        @Option(names = {"-t", "--translations"}, description = "Search in translations only")
        private boolean translationsOnly;

        @Override
        public Integer call() {
            SkillDictionaryService service = SkillDictionaryService.getInstance();
            SkillDictionary dict = service.getDictionary();

            String lowerPattern = pattern.toLowerCase();
            int matchCount = 0;

            System.out.println();
            System.out.println("Searching for: " + pattern);
            System.out.println();

            // Search in aliases
            if (!translationsOnly) {
                Map<String, String> aliases = dict.getAliases();
                if (aliases != null) {
                    boolean headerPrinted = false;
                    for (Map.Entry<String, String> entry : aliases.entrySet()) {
                        if (entry.getKey().toLowerCase().contains(lowerPattern) ||
                                entry.getValue().toLowerCase().contains(lowerPattern)) {
                            if (!headerPrinted) {
                                System.out.println("Aliases:");
                                headerPrinted = true;
                            }
                            System.out.println("  " + entry.getKey() + " → " + entry.getValue());
                            matchCount++;
                        }
                    }
                    if (headerPrinted) System.out.println();
                }
            }

            // Search in translations
            if (!aliasesOnly) {
                Map<String, String> translations = dict.getTranslations();
                if (translations != null) {
                    boolean headerPrinted = false;
                    for (Map.Entry<String, String> entry : translations.entrySet()) {
                        if (entry.getKey().contains(pattern) ||
                                entry.getValue().toLowerCase().contains(lowerPattern)) {
                            if (!headerPrinted) {
                                System.out.println("Translations:");
                                headerPrinted = true;
                            }
                            System.out.println("  " + entry.getKey() + " → " + entry.getValue());
                            matchCount++;
                        }
                    }
                    if (headerPrinted) System.out.println();
                }
            }

            // Search in categories
            if (!aliasesOnly && !translationsOnly) {
                Map<String, List<String>> categories = dict.getCategories();
                if (categories != null) {
                    boolean headerPrinted = false;
                    for (Map.Entry<String, List<String>> entry : categories.entrySet()) {
                        if (entry.getValue() != null) {
                            for (String skill : entry.getValue()) {
                                if (skill.toLowerCase().contains(lowerPattern)) {
                                    if (!headerPrinted) {
                                        System.out.println("Standard Skills:");
                                        headerPrinted = true;
                                    }
                                    System.out.println("  " + skill + " [" + entry.getKey() + "]");
                                    matchCount++;
                                }
                            }
                        }
                    }
                    if (headerPrinted) System.out.println();
                }
            }

            if (matchCount == 0) {
                System.out.println("No matches found.");
            } else {
                System.out.println("Found " + matchCount + " matches.");
            }
            System.out.println();

            return 0;
        }
    }
}
