package com.jobmatch.model.match;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete hard gate check result.
 * Based on PRD v3.2 section 13.2.1.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HardGateResult {

    /**
     * Overall gate status.
     */
    private OverallGateStatus status;

    /**
     * Individual gate check items.
     */
    @Builder.Default
    private List<HardGateItem> items = new ArrayList<>();

    /**
     * Summary explanation.
     */
    private String summary;

    /**
     * List of borderline items that need attention.
     */
    @JsonProperty("borderline_warnings")
    @Builder.Default
    private List<String> borderlineWarnings = new ArrayList<>();

    /**
     * Number of passed items.
     */
    public int getPassedCount() {
        return (int) items.stream().filter(HardGateItem::isPassed).count();
    }

    /**
     * Number of failed items.
     */
    public int getFailedCount() {
        return (int) items.stream().filter(HardGateItem::isFailed).count();
    }

    /**
     * Number of borderline items.
     */
    public int getBorderlineCount() {
        return (int) items.stream().filter(HardGateItem::isBorderline).count();
    }

    /**
     * Number of unknown items.
     */
    public int getUnknownCount() {
        return (int) items.stream().filter(HardGateItem::isUnknown).count();
    }

    /**
     * Check if overall status is passed.
     */
    public boolean isPassed() {
        return status == OverallGateStatus.PASSED;
    }

    /**
     * Check if overall status is failed.
     */
    public boolean isFailed() {
        return status == OverallGateStatus.FAILED;
    }

    /**
     * Check if overall status is uncertain.
     */
    public boolean isUncertain() {
        return status == OverallGateStatus.UNCERTAIN;
    }
}
