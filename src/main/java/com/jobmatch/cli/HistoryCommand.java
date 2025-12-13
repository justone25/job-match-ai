package com.jobmatch.cli;

import com.jobmatch.model.match.MatchReport;
import com.jobmatch.report.ReportFormatter;
import com.jobmatch.storage.StorageService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Command for managing analysis history.
 */
@Command(
        name = "history",
        description = "Manage analysis history",
        subcommands = {
                HistoryCommand.ListCommand.class,
                HistoryCommand.ShowCommand.class,
                HistoryCommand.DeleteCommand.class,
                HistoryCommand.ExportCommand.class,
                HistoryCommand.ClearCommand.class
        }
)
public class HistoryCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        // Default: list history
        return new ListCommand().call();
    }

    @Command(name = "list", description = "List analysis history")
    static class ListCommand implements Callable<Integer> {
        @Option(names = {"-n", "--limit"}, description = "Limit number of records", defaultValue = "10")
        private int limit;

        private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());

        @Override
        public Integer call() {
            StorageService storage = StorageService.getInstance();
            List<StorageService.HistoryEntry> entries = storage.listHistory();

            if (entries.isEmpty()) {
                System.out.println();
                System.out.println("No analysis history found.");
                System.out.println();
                System.out.println("Run 'jobmatch analyze' to create your first analysis.");
                return 0;
            }

            System.out.println();
            System.out.println("Analysis History (最近 " + Math.min(limit, entries.size()) + " 条):");
            System.out.println("─".repeat(80));
            System.out.printf("%-20s  %-6s  %-6s  %s%n", "ID", "等级", "得分", "摘要");
            System.out.println("─".repeat(80));

            entries.stream()
                    .limit(limit)
                    .forEach(entry -> {
                        String level = entry.getMatchLevel() != null ? entry.getMatchLevel() : "-";
                        String score = String.valueOf(entry.getScore());
                        String oneLine = entry.getOneLine() != null ?
                                (entry.getOneLine().length() > 40 ? entry.getOneLine().substring(0, 40) + "..." : entry.getOneLine()) : "-";
                        System.out.printf("%-20s  %-6s  %-6s  %s%n", entry.getId(), level, score, oneLine);
                    });

            System.out.println("─".repeat(80));
            System.out.println("Total: " + entries.size() + " records");
            System.out.println();
            System.out.println("Use 'jobmatch history show <id>' to view details.");
            return 0;
        }
    }

    @Command(name = "show", description = "Show analysis details")
    static class ShowCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Analysis ID")
        private String analysisId;

        @Option(names = {"-f", "--format"}, description = "Output format: json, markdown", defaultValue = "markdown")
        private String format;

        @Override
        public Integer call() {
            StorageService storage = StorageService.getInstance();
            Optional<MatchReport> report = storage.loadReport(analysisId);

            if (report.isEmpty()) {
                System.err.println("[Error] Analysis not found: " + analysisId);
                return 1;
            }

            ReportFormatter formatter = new ReportFormatter();
            String output = formatter.format(report.get(), format);
            System.out.println(output);
            return 0;
        }
    }

    @Command(name = "delete", description = "Delete analysis record")
    static class DeleteCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Analysis ID", arity = "0..1")
        private String analysisId;

        @Option(names = {"--all"}, description = "Delete all records")
        private boolean deleteAll;

        @Override
        public Integer call() {
            StorageService storage = StorageService.getInstance();

            if (deleteAll) {
                int count = storage.clearHistory();
                System.out.println("[Info] Deleted " + count + " history records.");
                return 0;
            }

            if (analysisId == null || analysisId.isEmpty()) {
                System.err.println("[Error] Please specify an analysis ID or use --all");
                return 1;
            }

            boolean deleted = storage.deleteReport(analysisId);
            if (deleted) {
                System.out.println("[Info] Deleted: " + analysisId);
                return 0;
            } else {
                System.err.println("[Error] Analysis not found: " + analysisId);
                return 1;
            }
        }
    }

    @Command(name = "export", description = "Export analysis result")
    static class ExportCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Analysis ID")
        private String analysisId;

        @Option(names = {"-o", "--output"}, description = "Output file path", required = true)
        private File outputFile;

        @Option(names = {"-f", "--format"}, description = "Output format: json, markdown", defaultValue = "markdown")
        private String format;

        @Override
        public Integer call() {
            StorageService storage = StorageService.getInstance();
            Optional<MatchReport> report = storage.loadReport(analysisId);

            if (report.isEmpty()) {
                System.err.println("[Error] Analysis not found: " + analysisId);
                return 1;
            }

            ReportFormatter formatter = new ReportFormatter();
            String output = formatter.format(report.get(), format);

            try {
                Files.writeString(outputFile.toPath(), output, StandardCharsets.UTF_8);
                System.out.println("[Info] Exported to: " + outputFile.getPath());
                return 0;
            } catch (IOException e) {
                System.err.println("[Error] Failed to write file: " + e.getMessage());
                return 1;
            }
        }
    }

    @Command(name = "clear", description = "Clear all history")
    static class ClearCommand implements Callable<Integer> {
        @Option(names = {"-f", "--force"}, description = "Skip confirmation")
        private boolean force;

        @Override
        public Integer call() {
            if (!force) {
                System.out.print("Are you sure you want to clear all history? (y/N): ");
                try {
                    int ch = System.in.read();
                    if (ch != 'y' && ch != 'Y') {
                        System.out.println("Cancelled.");
                        return 0;
                    }
                } catch (IOException e) {
                    System.out.println("Cancelled.");
                    return 0;
                }
            }

            StorageService storage = StorageService.getInstance();
            int count = storage.clearHistory();
            System.out.println("[Info] Cleared " + count + " history records.");
            return 0;
        }
    }
}
