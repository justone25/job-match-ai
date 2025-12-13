package com.jobmatch.model.match;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Action suggestions for the candidate.
 * Based on PRD v3.2 section 13.1.3.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionSuggestion {

    /**
     * Suggested resume edits.
     */
    @JsonProperty("resume_edits")
    @Builder.Default
    private List<ResumeEdit> resumeEdits = new ArrayList<>();

    /**
     * Interview preparation focus points.
     */
    @JsonProperty("interview_focus")
    @Builder.Default
    private List<InterviewFocus> interviewFocus = new ArrayList<>();

    /**
     * One-week learning plan.
     */
    @JsonProperty("learning_plan_1w")
    private LearningPlan learningPlan1w;

    /**
     * Resume edit suggestion.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResumeEdit {
        /**
         * Type: add, modify, highlight, remove.
         */
        private String type;

        /**
         * Section of resume to edit.
         */
        private String section;

        /**
         * Current content (if modifying).
         */
        @JsonProperty("current_content")
        private String currentContent;

        /**
         * Suggested content.
         */
        @JsonProperty("suggested_content")
        private String suggestedContent;

        /**
         * Reason for the edit.
         */
        private String reason;

        /**
         * Priority (1=highest).
         */
        private int priority;
    }

    /**
     * Interview focus point.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterviewFocus {
        /**
         * Topic to prepare for.
         */
        private String topic;

        /**
         * Why this topic is important.
         */
        private String importance;

        /**
         * Key points to cover.
         */
        @JsonProperty("key_points")
        @Builder.Default
        private List<String> keyPoints = new ArrayList<>();

        /**
         * Sample question that might be asked.
         */
        @JsonProperty("sample_question")
        private String sampleQuestion;

        /**
         * Suggested answer approach.
         */
        @JsonProperty("answer_approach")
        private String answerApproach;
    }

    /**
     * Learning plan for skill improvement.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LearningPlan {
        /**
         * Duration of the plan.
         */
        private String duration;

        /**
         * Focus areas.
         */
        @JsonProperty("focus_areas")
        @Builder.Default
        private List<String> focusAreas = new ArrayList<>();

        /**
         * Daily tasks.
         */
        @Builder.Default
        private List<DailyTask> tasks = new ArrayList<>();

        /**
         * Resources to use.
         */
        @Builder.Default
        private List<String> resources = new ArrayList<>();

        /**
         * Expected outcome.
         */
        @JsonProperty("expected_outcome")
        private String expectedOutcome;
    }

    /**
     * Daily learning task.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyTask {
        /**
         * Day number.
         */
        private int day;

        /**
         * Task description.
         */
        private String task;

        /**
         * Estimated hours.
         */
        @JsonProperty("estimated_hours")
        private double estimatedHours;

        /**
         * Deliverable/outcome.
         */
        private String deliverable;
    }

    /**
     * Get total number of suggestions.
     */
    public int getTotalSuggestionCount() {
        return resumeEdits.size() + interviewFocus.size() +
                (learningPlan1w != null && learningPlan1w.getTasks() != null ?
                        learningPlan1w.getTasks().size() : 0);
    }
}
