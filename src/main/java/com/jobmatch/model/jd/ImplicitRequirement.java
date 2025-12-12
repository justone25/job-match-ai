package com.jobmatch.model.jd;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Implicit requirement inferred from JD context.
 * Based on PRD v3.2 section 13.1.2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImplicitRequirement {

    /**
     * Requirement type: level, management, culture, workstyle.
     */
    private String type;

    /**
     * Inferred requirement.
     */
    private String inference;

    /**
     * Reasoning for the inference.
     */
    private String reasoning;

    /**
     * Evidence from original JD text.
     */
    @Builder.Default
    private List<String> evidence = new ArrayList<>();

    /**
     * Confidence score (0.0-1.0).
     * Typically lower than hard/soft requirements.
     */
    @Builder.Default
    private Double confidence = 0.0;

    // Type constants
    public static final String TYPE_LEVEL = "level";
    public static final String TYPE_MANAGEMENT = "management";
    public static final String TYPE_CULTURE = "culture";
    public static final String TYPE_WORKSTYLE = "workstyle";
}
