package com.jobmatch.cli;

import com.jobmatch.storage.StorageService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * Command for submitting and viewing feedback.
 */
@Command(
        name = "feedback",
        description = "Submit feedback for an analysis or view feedback statistics"
)
public class FeedbackCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Analysis ID to provide feedback for", arity = "0..1")
    private String analysisId;

    @Option(names = {"--stats"}, description = "Show feedback statistics")
    private boolean showStats;

    @Option(names = {"--list"}, description = "List all feedback entries")
    private boolean listFeedback;

    @Option(names = {"-r", "--rating"}, description = "Rating: 1=helpful, 2=neutral, 3=not helpful")
    private Integer rating;

    @Option(names = {"-c", "--comment"}, description = "Comment (optional)")
    private String comment;

    private final StorageService storageService;

    public FeedbackCommand() {
        this.storageService = StorageService.getInstance();
    }

    @Override
    public Integer call() {
        if (showStats) {
            return showStatistics();
        }

        if (listFeedback) {
            return listAllFeedback();
        }

        return submitFeedback();
    }

    private Integer showStatistics() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║               反馈统计                           ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();

        StorageService.FeedbackStats stats = storageService.getFeedbackStats();

        if (stats.getTotal() == 0) {
            System.out.println("  暂无反馈数据");
            System.out.println();
            return 0;
        }

        System.out.println("  总反馈数:     " + stats.getTotal());
        System.out.println("  有帮助:       " + stats.getHelpful() + " (" + String.format("%.1f", stats.getHelpfulRate()) + "%)");
        System.out.println("  一般:         " + stats.getNeutral());
        System.out.println("  没有帮助:     " + stats.getNotHelpful());
        System.out.println();

        return 0;
    }

    private Integer listAllFeedback() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║               反馈列表                           ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();

        List<StorageService.Feedback> feedbacks = storageService.listFeedback();

        if (feedbacks.isEmpty()) {
            System.out.println("  暂无反馈记录");
            System.out.println();
            return 0;
        }

        System.out.println(String.format("  %-20s  %-10s  %s", "分析ID", "评价", "评论"));
        System.out.println("  " + "─".repeat(60));

        for (StorageService.Feedback fb : feedbacks) {
            String ratingStr = getRatingString(fb.getRating());
            String commentStr = fb.getComment() != null ? truncate(fb.getComment(), 30) : "-";
            String idStr = fb.getAnalysisId() != null ? fb.getAnalysisId() : "N/A";
            System.out.println(String.format("  %-20s  %-10s  %s", idStr, ratingStr, commentStr));
        }
        System.out.println();

        return 0;
    }

    private Integer submitFeedback() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║               提交反馈                           ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();

        // Get analysis ID if not provided
        String targetId = analysisId;
        if (targetId == null) {
            // Try to get most recent analysis ID
            List<StorageService.HistoryEntry> history = storageService.listHistory();
            if (!history.isEmpty()) {
                targetId = history.get(0).getId();
                System.out.println("  使用最近的分析: " + targetId);
            }
        } else {
            System.out.println("  分析ID: " + targetId);
        }
        System.out.println();

        // Get rating
        int finalRating;
        if (rating != null) {
            finalRating = rating;
            if (finalRating < 1 || finalRating > 3) {
                System.err.println("[Error] 评价必须是 1-3 (1=有帮助, 2=一般, 3=没有帮助)");
                return 1;
            }
        } else {
            finalRating = promptForRating();
            if (finalRating == -1) {
                return 1;
            }
        }

        // Get comment
        String finalComment = comment;
        if (finalComment == null) {
            finalComment = promptForComment();
        }

        // Save feedback
        StorageService.Feedback feedback = StorageService.Feedback.builder()
                .analysisId(targetId)
                .rating(finalRating)
                .comment(finalComment)
                .timestamp(Instant.now())
                .build();

        try {
            storageService.saveFeedback(feedback);
            System.out.println();
            System.out.println("  ✓ 感谢您的反馈！");
            System.out.println();
        } catch (IOException e) {
            System.err.println("[Error] 保存反馈失败: " + e.getMessage());
            return 1;
        }

        return 0;
    }

    private int promptForRating() {
        System.out.println("  请评价本次分析结果:");
        System.out.println("  [1] 有帮助 - 分析结果对我有价值");
        System.out.println("  [2] 一般 - 分析结果基本准确");
        System.out.println("  [3] 没有帮助 - 分析结果不准确");
        System.out.println();
        System.out.print("  请输入 (1-3): ");

        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine().trim();

        try {
            int r = Integer.parseInt(input);
            if (r >= 1 && r <= 3) {
                return r;
            }
        } catch (NumberFormatException ignored) {
        }

        System.err.println("[Error] 无效输入，请输入 1-3");
        return -1;
    }

    private String promptForComment() {
        System.out.println();
        System.out.println("  请输入评论 (可选，直接回车跳过):");
        System.out.print("  > ");

        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine().trim();
        return input.isEmpty() ? null : input;
    }

    private String getRatingString(int rating) {
        return switch (rating) {
            case 1 -> "有帮助";
            case 2 -> "一般";
            case 3 -> "没有帮助";
            default -> "未知";
        };
    }

    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }
}
