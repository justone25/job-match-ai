package com.jobmatch.matcher;

import com.jobmatch.model.match.ActionSuggestion;
import com.jobmatch.model.match.GapAnalysis;
import com.jobmatch.model.match.HardGateResult;
import com.jobmatch.model.match.SoftScoreResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates action suggestions based on match analysis.
 * Based on PRD v3.2 section 13.1.3.
 */
public class ActionGenerator {

    private static final Logger log = LoggerFactory.getLogger(ActionGenerator.class);

    /**
     * Generate action suggestions.
     */
    public ActionSuggestion generate(HardGateResult hardGate, SoftScoreResult scores,
                                     GapAnalysis gaps) {
        List<ActionSuggestion.ResumeEdit> resumeEdits = generateResumeEdits(gaps, scores);
        List<ActionSuggestion.InterviewFocus> interviewFocus = generateInterviewFocus(gaps, hardGate);
        ActionSuggestion.LearningPlan learningPlan = generateLearningPlan(gaps);

        log.info("Generated {} resume edits, {} interview focus points, learning plan with {} tasks",
                resumeEdits.size(), interviewFocus.size(),
                learningPlan != null && learningPlan.getTasks() != null ?
                        learningPlan.getTasks().size() : 0);

        return ActionSuggestion.builder()
                .resumeEdits(resumeEdits)
                .interviewFocus(interviewFocus)
                .learningPlan1w(learningPlan)
                .build();
    }

    /**
     * Generate resume edit suggestions.
     */
    private List<ActionSuggestion.ResumeEdit> generateResumeEdits(GapAnalysis gaps, SoftScoreResult scores) {
        List<ActionSuggestion.ResumeEdit> edits = new ArrayList<>();
        int priority = 1;

        // Highlight strengths
        for (GapAnalysis.StrengthItem strength : gaps.getStrengths()) {
            if ("high".equals(strength.getRelevance())) {
                edits.add(ActionSuggestion.ResumeEdit.builder()
                        .type("highlight")
                        .section("技能描述")
                        .suggestedContent(strength.getHighlightSuggestion())
                        .reason("突出核心优势")
                        .priority(priority++)
                        .build());
            }
        }

        // Address missing skills (if candidate has related skills)
        for (GapAnalysis.GapItem gap : gaps.getInsufficient()) {
            edits.add(ActionSuggestion.ResumeEdit.builder()
                    .type("modify")
                    .section("技能部分")
                    .suggestedContent(String.format("强调 %s 的实际应用经验和项目成果", gap.getName()))
                    .reason(String.format("提升 %s 的展示力度", gap.getName()))
                    .priority(priority++)
                    .build());
        }

        // General improvements based on score
        if (scores.getSkillScore() != null && scores.getSkillScore().getScore() < 70) {
            edits.add(ActionSuggestion.ResumeEdit.builder()
                    .type("add")
                    .section("技能部分")
                    .suggestedContent("添加更多技能关键词，确保与JD用词一致")
                    .reason("提升技能匹配度")
                    .priority(priority++)
                    .build());
        }

        if (scores.getExperienceScore() != null && scores.getExperienceScore().getScore() < 70) {
            edits.add(ActionSuggestion.ResumeEdit.builder()
                    .type("modify")
                    .section("工作经历")
                    .suggestedContent("用数据量化工作成果，如\"优化系统性能提升30%\"")
                    .reason("增强经验说服力")
                    .priority(priority++)
                    .build());
        }

        return edits;
    }

    /**
     * Generate interview focus points.
     */
    private List<ActionSuggestion.InterviewFocus> generateInterviewFocus(GapAnalysis gaps,
                                                                         HardGateResult hardGate) {
        List<ActionSuggestion.InterviewFocus> focus = new ArrayList<>();

        // Address borderline items
        if (hardGate.getBorderlineWarnings() != null) {
            for (String warning : hardGate.getBorderlineWarnings()) {
                focus.add(ActionSuggestion.InterviewFocus.builder()
                        .topic("解释边界情况")
                        .importance("面试官可能会追问此项")
                        .keyPoints(List.of(
                                "准备具体数据和案例",
                                "强调快速学习能力",
                                "展示相关经验的可迁移性"
                        ))
                        .sampleQuestion("您的经验在这方面稍显不足，您如何看待？")
                        .answerApproach("承认差距，强调快速学习能力和相关经验")
                        .build());
            }
        }

        // Missing high-impact skills
        for (GapAnalysis.GapItem gap : gaps.getMissing()) {
            if ("high".equals(gap.getImpact())) {
                focus.add(ActionSuggestion.InterviewFocus.builder()
                        .topic(gap.getName())
                        .importance("JD 中的核心要求")
                        .keyPoints(List.of(
                                "准备回答为什么没有这项技能",
                                "展示学习计划和意愿",
                                "强调可迁移的相关技能"
                        ))
                        .sampleQuestion(String.format("您没有 %s 的经验，打算如何快速上手？", gap.getName()))
                        .answerApproach("展示学习能力和计划，强调相关技能基础")
                        .build());
            }
        }

        // Leverage strengths
        for (GapAnalysis.StrengthItem strength : gaps.getStrengths()) {
            if ("high".equals(strength.getRelevance())) {
                focus.add(ActionSuggestion.InterviewFocus.builder()
                        .topic(strength.getName() + " 深度")
                        .importance("这是您的核心优势")
                        .keyPoints(List.of(
                                "准备2-3个深度技术案例",
                                "能够解释底层原理",
                                "展示解决复杂问题的能力"
                        ))
                        .sampleQuestion(String.format("请详细讲讲您在 %s 方面的经验", strength.getName()))
                        .answerApproach("用 STAR 方法讲述具体案例，突出技术深度")
                        .build());
            }
        }

        return focus;
    }

    /**
     * Generate 1-week learning plan.
     */
    private ActionSuggestion.LearningPlan generateLearningPlan(GapAnalysis gaps) {
        List<String> focusAreas = new ArrayList<>();
        List<ActionSuggestion.DailyTask> tasks = new ArrayList<>();
        List<String> resources = new ArrayList<>();

        // Focus on high-impact missing skills
        int day = 1;
        for (GapAnalysis.GapItem gap : gaps.getMissing()) {
            if ("high".equals(gap.getImpact()) && day <= 5) {
                focusAreas.add(gap.getName());

                tasks.add(ActionSuggestion.DailyTask.builder()
                        .day(day)
                        .task(String.format("学习 %s 基础概念和核心用法", gap.getName()))
                        .estimatedHours(2)
                        .deliverable("完成入门教程")
                        .build());

                tasks.add(ActionSuggestion.DailyTask.builder()
                        .day(day + 1)
                        .task(String.format("动手实践 %s，完成一个小项目", gap.getName()))
                        .estimatedHours(3)
                        .deliverable("可运行的示例项目")
                        .build());

                resources.add(String.format("%s 官方文档", gap.getName()));
                resources.add(String.format("%s 入门教程 (YouTube/B站)", gap.getName()));

                day += 2;
            }
        }

        // Add review day
        if (!tasks.isEmpty()) {
            tasks.add(ActionSuggestion.DailyTask.builder()
                    .day(7)
                    .task("复习本周学习内容，整理笔记")
                    .estimatedHours(2)
                    .deliverable("学习笔记和知识总结")
                    .build());
        }

        if (focusAreas.isEmpty()) {
            // No critical gaps, focus on strengths deepening
            focusAreas.add("巩固现有技能");
            tasks.add(ActionSuggestion.DailyTask.builder()
                    .day(1)
                    .task("复习核心技能的高级特性")
                    .estimatedHours(2)
                    .deliverable("技能深度知识点整理")
                    .build());
            tasks.add(ActionSuggestion.DailyTask.builder()
                    .day(3)
                    .task("准备技术面试常见问题")
                    .estimatedHours(2)
                    .deliverable("面试问答准备文档")
                    .build());
            tasks.add(ActionSuggestion.DailyTask.builder()
                    .day(5)
                    .task("整理项目经验，准备 STAR 格式案例")
                    .estimatedHours(2)
                    .deliverable("3个项目案例描述")
                    .build());
        }

        return ActionSuggestion.LearningPlan.builder()
                .duration("1周")
                .focusAreas(focusAreas)
                .tasks(tasks)
                .resources(resources)
                .expectedOutcome(focusAreas.isEmpty() ?
                        "面试准备充分，技术深度得到巩固" :
                        String.format("掌握 %s 的基础知识，能够进行简单应用",
                                String.join("、", focusAreas)))
                .build();
    }
}
