package com.jobmatch.matcher;

import com.jobmatch.config.MatcherConfigLoader;
import com.jobmatch.config.SkillImplicationLoader;
import com.jobmatch.model.common.ParseMeta;
import com.jobmatch.model.jd.JDParsed;
import com.jobmatch.model.match.*;
import com.jobmatch.model.resume.ResumeParsed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Main service for matching resume against JD.
 * Orchestrates hard gate checking, soft scoring, gap analysis, and action generation.
 * Based on PRD v3.2 section 13.
 */
public class MatchingService {

    private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

    private final HardGateChecker hardGateChecker;
    private final SoftScoreCalculator softScoreCalculator;
    private final GapAnalyzer gapAnalyzer;
    private final ActionGenerator actionGenerator;

    public MatchingService() {
        this.hardGateChecker = new HardGateChecker();
        this.softScoreCalculator = new SoftScoreCalculator();
        this.gapAnalyzer = new GapAnalyzer();
        this.actionGenerator = new ActionGenerator();
    }

    public MatchingService(HardGateChecker hardGateChecker, SoftScoreCalculator softScoreCalculator,
                           GapAnalyzer gapAnalyzer, ActionGenerator actionGenerator) {
        this.hardGateChecker = hardGateChecker;
        this.softScoreCalculator = softScoreCalculator;
        this.gapAnalyzer = gapAnalyzer;
        this.actionGenerator = actionGenerator;
    }

    /**
     * Perform complete match analysis.
     */
    public MatchReport match(ResumeParsed resume, JDParsed jd) {
        long startTime = System.currentTimeMillis();
        log.info("Starting match analysis...");

        // Step 1: Hard gate check
        log.debug("Checking hard gates...");
        HardGateResult hardGate = hardGateChecker.check(resume, jd);

        // Step 2: Soft score calculation (even if hard gate fails, for reference)
        log.debug("Calculating soft scores...");
        SoftScoreResult scores = softScoreCalculator.calculate(resume, jd);

        // Step 3: Gap analysis
        log.debug("Analyzing gaps...");
        GapAnalysis gaps = gapAnalyzer.analyze(resume, jd);

        // Step 4: Generate action suggestions
        log.debug("Generating action suggestions...");
        ActionSuggestion actions = actionGenerator.generate(hardGate, scores, gaps);

        // Build summary
        String recommendation = MatchReport.createRecommendation(hardGate.getStatus(), scores.getMatchLevel());
        String oneLine = buildOneLine(hardGate, scores, gaps);

        MatchReport.Summary summary = MatchReport.Summary.builder()
                .recommendation(recommendation)
                .overallScore(scores.getFinalScore())
                .hardGateStatus(hardGate.getStatus().name().toLowerCase())
                .matchLevel(scores.getMatchLevel().name())
                .oneLine(oneLine)
                .build();

        // Build metadata with config versions for reproducibility
        long latencyMs = System.currentTimeMillis() - startTime;
        ParseMeta meta = ParseMeta.builder()
                .parseTime(Instant.now())
                .modelVersion("v0.1")
                .latencyMs(latencyMs)
                .configVersion(MatcherConfigLoader.getInstance().getVersion())
                .rulesVersion(SkillImplicationLoader.getInstance().getVersion())
                .build();

        log.info("Match analysis completed in {}ms: recommendation={}, score={}, level={}",
                latencyMs, recommendation, scores.getFinalScore(), scores.getMatchLevel());

        return MatchReport.builder()
                .summary(summary)
                .hardGate(hardGate)
                .scores(scores)
                .gaps(gaps)
                .actions(actions)
                .meta(meta)
                .build();
    }

    /**
     * Build one-line summary.
     */
    private String buildOneLine(HardGateResult hardGate, SoftScoreResult scores, GapAnalysis gaps) {
        StringBuilder sb = new StringBuilder();

        // Match level description
        MatchLevel level = scores.getMatchLevel();
        if (level == MatchLevel.A) {
            sb.append("高度匹配");
        } else if (level == MatchLevel.B) {
            sb.append("较好匹配");
        } else if (level == MatchLevel.C) {
            sb.append("基本匹配");
        } else {
            sb.append("匹配度一般");
        }

        // Add skill highlights
        if (scores.getSkillScore() != null && scores.getSkillScore().getScore() >= 80) {
            sb.append("，技能覆盖度高");
        }

        // Add gap hints
        if (!gaps.getMissing().isEmpty()) {
            int highImpact = gaps.getHighImpactGapCount();
            if (highImpact > 0) {
                sb.append("，").append(highImpact).append("项核心技能需补充");
            }
        }

        // Add strength hints
        if (!gaps.getStrengths().isEmpty()) {
            long highRelevance = gaps.getStrengths().stream()
                    .filter(s -> "high".equals(s.getRelevance()))
                    .count();
            if (highRelevance > 0) {
                sb.append("，").append(highRelevance).append("项核心优势可突出");
            }
        }

        // Add hard gate warning
        if (hardGate.getBorderlineCount() > 0) {
            sb.append("，").append(hardGate.getBorderlineCount()).append("项边界情况需关注");
        }

        return sb.toString();
    }
}
