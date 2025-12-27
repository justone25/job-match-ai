package com.jobmatch.cli;

import com.jobmatch.model.monitor.WeeklySummary;
import com.jobmatch.monitor.NotificationService;
import com.jobmatch.summary.WeeklySummaryService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;

/**
 * CLI command for weekly JD summary.
 */
@Command(
        name = "weekly-summary",
        description = "Generate weekly JD summary and learning suggestions",
        subcommands = {
                WeeklySummaryCommand.GenerateCommand.class,
                WeeklySummaryCommand.ShowCommand.class,
                WeeklySummaryCommand.CronCommand.class
        }
)
public class WeeklySummaryCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("Usage: jobmatch weekly-summary <command>");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  generate  Generate weekly summary report");
        System.out.println("  show      Show latest summary report");
        System.out.println("  cron      Manage cron jobs");
        return 0;
    }

    /**
     * Generate weekly summary.
     */
    @Command(name = "generate", description = "Generate weekly summary report")
    public static class GenerateCommand implements Callable<Integer> {

        @Option(names = {"-o", "--output"}, description = "Output file path")
        private String outputPath;

        @Option(names = {"--no-notify"}, description = "Disable desktop notification")
        private boolean noNotify;

        @Override
        public Integer call() {
            System.out.println("Generating weekly summary...");
            try {
                WeeklySummaryService service = new WeeklySummaryService();
                WeeklySummary summary = service.generate();

                if (summary.getTotalJobs() == 0) {
                    System.out.println("No jobs collected this week. Nothing to summarize.");
                    return 0;
                }

                // Output report
                String report = summary.getRawContent();
                if (outputPath != null) {
                    Files.writeString(Paths.get(outputPath), report);
                    System.out.println("✓ Report saved to: " + outputPath);
                } else {
                    System.out.println();
                    System.out.println(report);
                }

                // Desktop notification
                if (!noNotify) {
                    NotificationService notifier = new NotificationService();
                    notifier.notify("JobMatch: 周报已生成",
                            String.format("分析了 %d 个职位，发现 %d 项热门技能",
                                    summary.getTotalJobs(),
                                    summary.getTopSkills() != null ? summary.getTopSkills().size() : 0));
                }

                return 0;
            } catch (Exception e) {
                System.err.println("✗ Failed to generate summary: " + e.getMessage());
                e.printStackTrace();
                return 1;
            }
        }
    }

    /**
     * Show latest summary.
     */
    @Command(name = "show", description = "Show latest summary report")
    public static class ShowCommand implements Callable<Integer> {

        @Option(names = {"--date"}, description = "Show report for specific date (yyyyMMdd)")
        private String date;

        @Override
        public Integer call() {
            try {
                WeeklySummaryService service = new WeeklySummaryService();
                WeeklySummary summary = date != null ?
                        service.loadByDate(date) :
                        service.loadLatest();

                if (summary == null) {
                    System.out.println("No summary report found.");
                    System.out.println("Run 'jobmatch weekly-summary generate' to create one.");
                    return 0;
                }

                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                System.out.println("Weekly Summary Report");
                System.out.println("Week: " + summary.getWeekStart().format(fmt) +
                        " ~ " + summary.getWeekEnd().format(fmt));
                System.out.println("─".repeat(60));
                System.out.println(summary.getRawContent());
                return 0;
            } catch (Exception e) {
                System.err.println("✗ Failed to load summary: " + e.getMessage());
                return 1;
            }
        }
    }

    /**
     * Manage cron jobs for weekly summary.
     */
    @Command(
            name = "cron",
            description = "Manage cron jobs for weekly summary",
            subcommands = {
                    CronCommand.InstallCommand.class,
                    CronCommand.UninstallCommand.class
            }
    )
    public static class CronCommand implements Callable<Integer> {

        private static final String CRON_MARKER = "# JobMatch Weekly Summary";

        @Override
        public Integer call() {
            System.out.println("Usage: jobmatch weekly-summary cron <command>");
            System.out.println();
            System.out.println("Commands:");
            System.out.println("  install    Install cron job (Monday 9:00)");
            System.out.println("  uninstall  Remove cron job");
            return 0;
        }

        @Command(name = "install", description = "Install cron job for weekly summary")
        public static class InstallCommand implements Callable<Integer> {

            @Override
            public Integer call() {
                try {
                    String jobmatchPath = findJobmatchPath();
                    String logPath = System.getProperty("user.home") + "/.jobmatch/logs/summary.log";

                    // Cron entry: run at 9:00 every Monday
                    String cronEntry = String.format(
                            "0 9 * * 1 %s weekly-summary generate >> %s 2>&1 %s%n",
                            jobmatchPath, logPath, CRON_MARKER);

                    String currentCrontab = getCurrentCrontab();

                    if (currentCrontab.contains(CRON_MARKER)) {
                        System.out.println("Weekly summary cron job already installed.");
                        return 0;
                    }

                    String newCrontab = currentCrontab + cronEntry;
                    setCrontab(newCrontab);

                    System.out.println("✓ Cron job installed successfully");
                    System.out.println("  Schedule: Every Monday at 9:00");
                    System.out.println("  Log file: " + logPath);
                    return 0;
                } catch (Exception e) {
                    System.err.println("✗ Failed to install cron: " + e.getMessage());
                    return 1;
                }
            }
        }

        @Command(name = "uninstall", description = "Remove cron job for weekly summary")
        public static class UninstallCommand implements Callable<Integer> {

            @Override
            public Integer call() {
                try {
                    String currentCrontab = getCurrentCrontab();

                    if (!currentCrontab.contains(CRON_MARKER)) {
                        System.out.println("No weekly summary cron job found.");
                        return 0;
                    }

                    String newCrontab = currentCrontab.lines()
                            .filter(line -> !line.contains(CRON_MARKER))
                            .reduce("", (a, b) -> a + b + "\n");

                    setCrontab(newCrontab);
                    System.out.println("✓ Cron job removed successfully");
                    return 0;
                } catch (Exception e) {
                    System.err.println("✗ Failed to uninstall cron: " + e.getMessage());
                    return 1;
                }
            }
        }

        private static String getCurrentCrontab() throws IOException, InterruptedException {
            ProcessBuilder pb = new ProcessBuilder("crontab", "-l");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            if (output.contains("no crontab")) {
                return "";
            }
            return output;
        }

        private static void setCrontab(String content) throws IOException, InterruptedException {
            ProcessBuilder pb = new ProcessBuilder("crontab", "-");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getOutputStream().write(content.getBytes());
            p.getOutputStream().close();
            p.waitFor();
        }

        private static String findJobmatchPath() {
            String[] paths = {
                    System.getProperty("user.home") + "/.jobmatch/bin/jobmatch",
                    "/usr/local/bin/jobmatch",
                    System.getProperty("user.dir") + "/target/job-match-0.1.0-SNAPSHOT.jar"
            };

            for (String path : paths) {
                if (Files.exists(Paths.get(path))) {
                    if (path.endsWith(".jar")) {
                        return "java -jar " + path;
                    }
                    return path;
                }
            }
            return "java -jar " + System.getProperty("user.dir") + "/target/job-match-0.1.0-SNAPSHOT.jar";
        }
    }
}
