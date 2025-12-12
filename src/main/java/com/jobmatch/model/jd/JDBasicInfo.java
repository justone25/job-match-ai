package com.jobmatch.model.jd;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Basic information from JD.
 * Based on PRD v3.2 section 13.1.2.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JDBasicInfo {

    /**
     * Job title.
     */
    private String title;

    /**
     * Company name.
     */
    private String company;

    /**
     * Work location.
     */
    private String location;

    /**
     * Salary range (e.g., "40-60K").
     */
    @JsonProperty("salary_range")
    private String salaryRange;

    /**
     * Employment type (full-time, part-time, contract, etc.).
     */
    @JsonProperty("employment_type")
    private String employmentType;
}
