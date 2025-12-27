package com.jobmatch.model.monitor;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data model for a BOSS job posting.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BossJob {

    /**
     * Unique job ID from BOSS (e.g., job_xxx)
     */
    private String jobId;

    /**
     * Job title
     */
    private String title;

    /**
     * Company name
     */
    private String company;

    /**
     * Company size (e.g., "100-499人")
     */
    private String companySize;

    /**
     * Industry (e.g., "互联网")
     */
    private String industry;

    /**
     * City (e.g., "北京")
     */
    private String city;

    /**
     * District (e.g., "朝阳区")
     */
    private String district;

    /**
     * Salary range (e.g., "25-50K")
     */
    private String salary;

    /**
     * Experience requirement (e.g., "5-10年")
     */
    private String experience;

    /**
     * Education requirement (e.g., "本科")
     */
    private String education;

    /**
     * Job description full text
     */
    private String description;

    /**
     * Skill tags extracted from JD
     */
    private List<String> skillTags;

    /**
     * Job URL on BOSS
     */
    private String url;

    /**
     * When this job was published on BOSS
     */
    private LocalDateTime publishedAt;

    /**
     * When this job was first seen
     */
    private LocalDateTime firstSeenAt;

    /**
     * When this job was last updated
     */
    private LocalDateTime lastUpdatedAt;

    /**
     * Job status: ACTIVE, CLOSED, EXPIRED
     */
    @Builder.Default
    private String status = "ACTIVE";
}
