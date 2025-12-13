package com.jobmatch.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jobmatch.model.match.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Formats MatchReport into various output formats.
 * Supports markdown, json, and simple text formats.
 */
public class ReportFormatter {

    private static final Logger log = LoggerFactory.getLogger(ReportFormatter.class);

    private final ObjectMapper objectMapper;

    public ReportFormatter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Format report based on specified format.
     */
    public String format(MatchReport report, String formatType) {
        return switch (formatType.toLowerCase()) {
            case "json" -> formatJson(report);
            case "markdown", "md" -> formatMarkdown(report);
            case "simple", "text" -> formatSimple(report);
            default -> formatMarkdown(report);
        };
    }

    /**
     * Format as JSON.
     */
    public String formatJson(MatchReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception e) {
            log.error("Failed to format report as JSON", e);
            return "{\"error\": \"Failed to format report\"}";
        }
    }

    /**
     * Format as Markdown.
     */
    public String formatMarkdown(MatchReport report) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("# JobMatch AI 分析报告\n\n");

        // Summary section
        appendSummarySection(sb, report.getSummary());

        // Hard Gate section
        appendHardGateSection(sb, report.getHardGate());

        // Scores section
        appendScoresSection(sb, report.getScores());

        // Gaps section
        appendGapsSection(sb, report.getGaps());

        // Actions section
        appendActionsSection(sb, report.getActions());

        // Metadata
        if (report.getMeta() != null) {
            sb.append("---\n\n");
            sb.append("*分析时间: ").append(report.getMeta().getParseTime()).append("*\n");
            sb.append("*处理耗时: ").append(report.getMeta().getLatencyMs()).append("ms*\n");
        }

        return sb.toString();
    }

    /**
     * Format as simple text (one-line summary).
     */
    public String formatSimple(MatchReport report) {
        if (report.getSummary() != null) {
            return report.getSummary().getOneLine();
        }
        return "分析完成";
    }

    private void appendSummarySection(StringBuilder sb, MatchReport.Summary summary) {
        if (summary == null) return;

        sb.append("## 📊 总体评估\n\n");
        sb.append("| 项目 | 结果 |\n");
        sb.append("|------|------|\n");
        sb.append("| **推荐等级** | ").append(summary.getMatchLevel()).append(" |\n");
        sb.append("| **综合得分** | ").append(summary.getOverallScore()).append("/100 |\n");
        sb.append("| **硬性门槛** | ").append(formatGateStatus(summary.getHardGateStatus())).append(" |\n");
        sb.append("| **建议** | ").append(summary.getRecommendation()).append(" |\n");
        sb.append("\n");

        if (summary.getOneLine() != null) {
            sb.append("> ").append(summary.getOneLine()).append("\n\n");
        }
    }

    private void appendHardGateSection(StringBuilder sb, HardGateResult hardGate) {
        if (hardGate == null || hardGate.getItems() == null || hardGate.getItems().isEmpty()) {
            return;
        }

        sb.append("## 🚦 硬性门槛检查\n\n");
        sb.append("**状态**: ").append(formatOverallGateStatus(hardGate.getStatus())).append("\n\n");

        sb.append("| 要求 | 状态 | 候选人情况 | 说明 |\n");
        sb.append("|------|------|------------|------|\n");

        for (HardGateItem item : hardGate.getItems()) {
            sb.append("| ").append(item.getRequirement())
                    .append(" | ").append(formatGateItemStatus(item.getStatus()))
                    .append(" | ").append(item.getCandidateValue() != null ? item.getCandidateValue() : "-")
                    .append(" | ").append(item.getExplanation() != null ? item.getExplanation() : "-")
                    .append(" |\n");
        }
        sb.append("\n");

        // Borderline warnings
        if (hardGate.getBorderlineWarnings() != null && !hardGate.getBorderlineWarnings().isEmpty()) {
            sb.append("### ⚠️ 边界情况提醒\n\n");
            for (String warning : hardGate.getBorderlineWarnings()) {
                sb.append("- ").append(warning).append("\n");
            }
            sb.append("\n");
        }
    }

    private void appendScoresSection(StringBuilder sb, SoftScoreResult scores) {
        if (scores == null) return;

        sb.append("## 📈 软性评分\n\n");

        // Overall score
        if (scores.getOverall() != null) {
            sb.append("**综合得分**: ").append(scores.getFinalScore())
                    .append("/100 (").append(scores.getMatchLevel().name()).append("级)\n\n");
        }

        // Skill score
        if (scores.getSkillScore() != null) {
            appendScoreDetail(sb, "技能匹配", scores.getSkillScore());
        }

        // Experience score
        if (scores.getExperienceScore() != null) {
            appendScoreDetail(sb, "经验匹配", scores.getExperienceScore());
        }

        // Bonus score
        if (scores.getBonusScore() != null) {
            appendScoreDetail(sb, "加分项", scores.getBonusScore());
        }
    }

    private void appendScoreDetail(StringBuilder sb, String name, ScoreDetail detail) {
        sb.append("### ").append(name).append("\n\n");
        sb.append("**得分**: ").append(detail.getScore())
                .append("/100 (权重: ").append((int)(detail.getWeight() * 100)).append("%)\n\n");

        if (detail.getItems() != null && !detail.getItems().isEmpty()) {
            sb.append("| 项目 | 状态 | 得分 |\n");
            sb.append("|------|------|------|\n");
            for (ScoreDetail.ScoreItem item : detail.getItems()) {
                sb.append("| ").append(item.getName())
                        .append(" | ").append(formatScoreStatus(item.getStatus()))
                        .append(" | ").append(item.getPoints()).append("/").append(item.getMaxPoints())
                        .append(" |\n");
            }
            sb.append("\n");
        }
    }

    private void appendGapsSection(StringBuilder sb, GapAnalysis gaps) {
        if (gaps == null) return;

        sb.append("## 🔍 差距分析\n\n");

        // Missing skills
        if (gaps.getMissing() != null && !gaps.getMissing().isEmpty()) {
            sb.append("### 缺失技能\n\n");
            for (GapAnalysis.GapItem gap : gaps.getMissing()) {
                sb.append("- **").append(gap.getName()).append("**");
                if (gap.getImpact() != null) {
                    sb.append(" (影响: ").append(formatImpact(gap.getImpact())).append(")");
                }
                sb.append("\n");
                if (gap.getSuggestion() != null) {
                    sb.append("  - 建议: ").append(gap.getSuggestion()).append("\n");
                }
            }
            sb.append("\n");
        }

        // Insufficient skills
        if (gaps.getInsufficient() != null && !gaps.getInsufficient().isEmpty()) {
            sb.append("### 待提升技能\n\n");
            for (GapAnalysis.GapItem gap : gaps.getInsufficient()) {
                sb.append("- **").append(gap.getName()).append("**");
                if (gap.getCurrentLevel() != null && gap.getRequiredLevel() != null) {
                    sb.append(" (当前: ").append(gap.getCurrentLevel())
                            .append(" → 要求: ").append(gap.getRequiredLevel()).append(")");
                }
                sb.append("\n");
                if (gap.getSuggestion() != null) {
                    sb.append("  - 建议: ").append(gap.getSuggestion()).append("\n");
                }
            }
            sb.append("\n");
        }

        // Strengths
        if (gaps.getStrengths() != null && !gaps.getStrengths().isEmpty()) {
            sb.append("### 核心优势\n\n");
            for (GapAnalysis.StrengthItem strength : gaps.getStrengths()) {
                sb.append("- **").append(strength.getName()).append("**");
                if (strength.getRelevance() != null) {
                    sb.append(" (相关性: ").append(formatRelevance(strength.getRelevance())).append(")");
                }
                sb.append("\n");
                if (strength.getHighlightSuggestion() != null) {
                    sb.append("  - ").append(strength.getHighlightSuggestion()).append("\n");
                }
            }
            sb.append("\n");
        }
    }

    private void appendActionsSection(StringBuilder sb, ActionSuggestion actions) {
        if (actions == null) return;

        sb.append("## 💡 行动建议\n\n");

        // Resume edits
        if (actions.getResumeEdits() != null && !actions.getResumeEdits().isEmpty()) {
            sb.append("### 简历优化建议\n\n");
            for (ActionSuggestion.ResumeEdit edit : actions.getResumeEdits()) {
                sb.append(edit.getPriority()).append(". **").append(edit.getSection()).append("**\n");
                sb.append("   - 类型: ").append(formatEditType(edit.getType())).append("\n");
                sb.append("   - 建议: ").append(edit.getSuggestedContent()).append("\n");
                if (edit.getReason() != null) {
                    sb.append("   - 原因: ").append(edit.getReason()).append("\n");
                }
            }
            sb.append("\n");
        }

        // Interview focus
        if (actions.getInterviewFocus() != null && !actions.getInterviewFocus().isEmpty()) {
            sb.append("### 面试准备重点\n\n");
            for (ActionSuggestion.InterviewFocus focus : actions.getInterviewFocus()) {
                sb.append("#### ").append(focus.getTopic()).append("\n\n");
                if (focus.getImportance() != null) {
                    sb.append("**重要性**: ").append(focus.getImportance()).append("\n\n");
                }
                if (focus.getKeyPoints() != null && !focus.getKeyPoints().isEmpty()) {
                    sb.append("**关键点**:\n");
                    for (String point : focus.getKeyPoints()) {
                        sb.append("- ").append(point).append("\n");
                    }
                    sb.append("\n");
                }
                if (focus.getSampleQuestion() != null) {
                    sb.append("**示例问题**: ").append(focus.getSampleQuestion()).append("\n\n");
                }
                if (focus.getAnswerApproach() != null) {
                    sb.append("**回答思路**: ").append(focus.getAnswerApproach()).append("\n\n");
                }
            }
        }

        // Learning plan
        if (actions.getLearningPlan1w() != null) {
            ActionSuggestion.LearningPlan plan = actions.getLearningPlan1w();
            sb.append("### 一周学习计划\n\n");

            if (plan.getFocusAreas() != null && !plan.getFocusAreas().isEmpty()) {
                sb.append("**重点领域**: ").append(String.join("、", plan.getFocusAreas())).append("\n\n");
            }

            if (plan.getTasks() != null && !plan.getTasks().isEmpty()) {
                sb.append("| 天数 | 任务 | 预计时长 | 产出 |\n");
                sb.append("|------|------|----------|------|\n");
                for (ActionSuggestion.DailyTask task : plan.getTasks()) {
                    sb.append("| Day ").append(task.getDay())
                            .append(" | ").append(task.getTask())
                            .append(" | ").append(task.getEstimatedHours()).append("小时")
                            .append(" | ").append(task.getDeliverable() != null ? task.getDeliverable() : "-")
                            .append(" |\n");
                }
                sb.append("\n");
            }

            if (plan.getResources() != null && !plan.getResources().isEmpty()) {
                sb.append("**推荐资源**:\n");
                for (String resource : plan.getResources()) {
                    sb.append("- ").append(resource).append("\n");
                }
                sb.append("\n");
            }

            if (plan.getExpectedOutcome() != null) {
                sb.append("**预期成果**: ").append(plan.getExpectedOutcome()).append("\n\n");
            }
        }
    }

    // Formatting helpers
    private String formatGateStatus(String status) {
        if (status == null) return "-";
        return switch (status.toLowerCase()) {
            case "passed" -> "✅ 通过";
            case "failed" -> "❌ 不通过";
            case "uncertain" -> "❓ 待确认";
            default -> status;
        };
    }

    private String formatOverallGateStatus(OverallGateStatus status) {
        if (status == null) return "-";
        return switch (status) {
            case PASSED -> "✅ 全部通过";
            case FAILED -> "❌ 存在不通过项";
            case UNCERTAIN -> "❓ 部分信息待确认";
        };
    }

    private String formatGateItemStatus(HardGateStatus status) {
        if (status == null) return "-";
        return switch (status) {
            case PASS -> "✅ 通过";
            case FAIL -> "❌ 不通过";
            case BORDERLINE -> "⚠️ 边界";
            case UNKNOWN -> "❓ 未知";
        };
    }

    private String formatScoreStatus(String status) {
        if (status == null) return "-";
        return switch (status.toLowerCase()) {
            case "matched" -> "✅";
            case "partial" -> "⚠️";
            case "missing" -> "❌";
            default -> "-";
        };
    }

    private String formatImpact(String impact) {
        if (impact == null) return "-";
        return switch (impact.toLowerCase()) {
            case "high" -> "高";
            case "medium" -> "中";
            case "low" -> "低";
            default -> impact;
        };
    }

    private String formatRelevance(String relevance) {
        if (relevance == null) return "-";
        return switch (relevance.toLowerCase()) {
            case "high" -> "高";
            case "medium" -> "中";
            case "low" -> "低";
            default -> relevance;
        };
    }

    private String formatEditType(String type) {
        if (type == null) return "-";
        return switch (type.toLowerCase()) {
            case "highlight" -> "突出展示";
            case "add" -> "新增";
            case "modify" -> "修改";
            case "remove" -> "删除";
            default -> type;
        };
    }
}
