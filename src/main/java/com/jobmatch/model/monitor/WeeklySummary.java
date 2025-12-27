package com.jobmatch.model.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Data model for weekly JD summary report.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklySummary {

    /**
     * Report ID (format: weekly_yyyyMMdd)
     */
    private String reportId;

    /**
     * Week start date (Monday)
     */
    private LocalDate weekStart;

    /**
     * Week end date (Sunday)
     */
    private LocalDate weekEnd;

    /**
     * Total number of jobs analyzed
     */
    private int totalJobs;

    /**
     * Number of new jobs this week
     */
    private int newJobs;

    /**
     * Top skills ranked by frequency
     */
    private List<SkillRank> topSkills;

    /**
     * Trend insights from LLM
     */
    private String trendInsights;

    /**
     * Learning suggestions from LLM
     */
    private List<LearningSuggestion> learningSuggestions;

    /**
     * Notable jobs worth attention
     */
    private List<NotableJob> notableJobs;

    /**
     * Full LLM response (raw markdown)
     */
    private String rawContent;

    /**
     * When this report was generated
     */
    private LocalDateTime generatedAt;

    /**
     * Skill frequency ranking
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillRank {
        private String skill;
        private int count;
        private String priority; // MUST, PREFERRED, BONUS
    }

    /**
     * Learning suggestion item
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearningSuggestion {
        private int priority;
        private String topic;
        private String description;
        private String resources;
        private String estimatedTime;
    }

    /**
     * Notable job worth attention
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotableJob {
        private String jobId;
        private String title;
        private String company;
        private String reason;
    }
}
