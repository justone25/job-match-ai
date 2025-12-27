package com.jobmatch.cli;

import com.jobmatch.model.monitor.BossJob;
import com.jobmatch.monitor.MonitorService;
import com.jobmatch.monitor.NotificationService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI command for monitoring BOSS jobs.
 */
@Command(
        name = "monitor",
        description = "Monitor BOSS jobs for AI positions",
        subcommands = {
                MonitorCommand.InitCommand.class,
                MonitorCommand.RunCommand.class,
                MonitorCommand.ListCommand.class,
                MonitorCommand.CronCommand.class
        }
)
public class MonitorCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println("Usage: jobmatch monitor <command>");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  init    Initialize monitor (login to BOSS)");
        System.out.println("  run     Run one crawl cycle");
        System.out.println("  list    List collected jobs");
        System.out.println("  cron    Manage cron jobs");
        return 0;
    }

    /**
     * Initialize monitor: login to BOSS and save cookies.
     */
    @Command(name = "init", description = "Initialize monitor (login to BOSS)")
    public static class InitCommand implements Callable<Integer> {

        @Override
        public Integer call() {
            System.out.println("Initializing BOSS monitor...");
            try {
                MonitorService service = new MonitorService();
                service.initialize();
                System.out.println("✓ Monitor initialized successfully");
                System.out.println("  Cookies saved to ~/.jobmatch/boss_cookies.json");
                return 0;
            } catch (Exception e) {
                System.err.println("✗ Initialization failed: " + e.getMessage());
                return 1;
            }
        }
    }

    /**
     * Run one crawl cycle.
     */
    @Command(name = "run", description = "Run one crawl cycle")
    public static class RunCommand implements Callable<Integer> {

        @Option(names = {"--no-notify"}, description = "Disable desktop notification")
        private boolean noNotify;

        @Override
        public Integer call() {
            System.out.println("Running BOSS job crawl...");
            try {
                MonitorService service = new MonitorService();
                List<BossJob> newJobs = service.crawlAndDetectNew();

                if (newJobs.isEmpty()) {
                    System.out.println("No new jobs found.");
                } else {
                    System.out.println("Found " + newJobs.size() + " new job(s):");
                    for (BossJob job : newJobs) {
                        System.out.println("  - " + job.getTitle() + " @ " + job.getCompany() +
                                " (" + job.getSalary() + ")");
                    }

                    if (!noNotify) {
                        NotificationService notifier = new NotificationService();
                        notifier.notify("JobMatch: 发现新职位",
                                "发现 " + newJobs.size() + " 个新的 AI 职位");
                    }
                }
                return 0;
            } catch (Exception e) {
                System.err.println("✗ Crawl failed: " + e.getMessage());
                e.printStackTrace();
                return 1;
            }
        }
    }

    /**
     * List collected jobs.
     */
    @Command(name = "list", description = "List collected jobs")
    public static class ListCommand implements Callable<Integer> {

        @Option(names = {"-n", "--limit"}, description = "Limit number of results", defaultValue = "20")
        private int limit;

        @Option(names = {"--week"}, description = "Show only this week's jobs")
        private boolean thisWeek;

        @Override
        public Integer call() {
            try {
                MonitorService service = new MonitorService();
                List<BossJob> jobs = thisWeek ? service.getThisWeekJobs() : service.getAllJobs();

                if (jobs.isEmpty()) {
                    System.out.println("No jobs collected yet.");
                    return 0;
                }

                System.out.println("Collected Jobs (" + jobs.size() + " total):");
                System.out.println("─".repeat(80));

                int count = 0;
                for (BossJob job : jobs) {
                    if (count >= limit) break;
                    System.out.printf("%-40s │ %-20s │ %s%n",
                            truncate(job.getTitle(), 40),
                            truncate(job.getCompany(), 20),
                            job.getSalary());
                    count++;
                }

                if (jobs.size() > limit) {
                    System.out.println("... and " + (jobs.size() - limit) + " more");
                }
                return 0;
            } catch (Exception e) {
                System.err.println("✗ Failed to list jobs: " + e.getMessage());
                return 1;
            }
        }

        private String truncate(String s, int maxLen) {
            if (s == null) return "";
            return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
        }
    }

    /**
     * Manage cron jobs.
     */
    @Command(
            name = "cron",
            description = "Manage cron jobs",
            subcommands = {
                    CronCommand.InstallCommand.class,
                    CronCommand.UninstallCommand.class,
                    CronCommand.StatusCommand.class
            }
    )
    public static class CronCommand implements Callable<Integer> {

        private static final String CRON_MARKER = "# JobMatch Monitor";
        private static final Path JOBMATCH_BIN = Paths.get(System.getProperty("user.home"),
                ".jobmatch", "bin", "jobmatch");

        @Override
        public Integer call() {
            System.out.println("Usage: jobmatch monitor cron <command>");
            System.out.println();
            System.out.println("Commands:");
            System.out.println("  install    Install cron job (daily 10:00, 18:00)");
            System.out.println("  uninstall  Remove cron job");
            System.out.println("  status     Show cron status");
            return 0;
        }

        @Command(name = "install", description = "Install cron job")
        public static class InstallCommand implements Callable<Integer> {

            @Override
            public Integer call() {
                try {
                    String jobmatchPath = findJobmatchPath();
                    String logPath = System.getProperty("user.home") + "/.jobmatch/logs/monitor.log";

                    // Cron entry: run at 10:00 and 18:00 daily
                    String cronEntry = String.format(
                            "0 10,18 * * * %s monitor run >> %s 2>&1 %s%n",
                            jobmatchPath, logPath, CRON_MARKER);

                    // Get current crontab
                    String currentCrontab = getCurrentCrontab();

                    // Check if already installed
                    if (currentCrontab.contains(CRON_MARKER)) {
                        System.out.println("Monitor cron job already installed.");
                        return 0;
                    }

                    // Add new entry
                    String newCrontab = currentCrontab + cronEntry;
                    setCrontab(newCrontab);

                    System.out.println("✓ Cron job installed successfully");
                    System.out.println("  Schedule: Daily at 10:00 and 18:00");
                    System.out.println("  Log file: " + logPath);
                    return 0;
                } catch (Exception e) {
                    System.err.println("✗ Failed to install cron: " + e.getMessage());
                    return 1;
                }
            }
        }

        @Command(name = "uninstall", description = "Remove cron job")
        public static class UninstallCommand implements Callable<Integer> {

            @Override
            public Integer call() {
                try {
                    String currentCrontab = getCurrentCrontab();

                    if (!currentCrontab.contains(CRON_MARKER)) {
                        System.out.println("No monitor cron job found.");
                        return 0;
                    }

                    // Remove lines containing marker
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

        @Command(name = "status", description = "Show cron status")
        public static class StatusCommand implements Callable<Integer> {

            @Override
            public Integer call() {
                try {
                    String currentCrontab = getCurrentCrontab();

                    if (currentCrontab.contains(CRON_MARKER)) {
                        System.out.println("Monitor cron job: INSTALLED");
                        currentCrontab.lines()
                                .filter(line -> line.contains(CRON_MARKER))
                                .forEach(line -> System.out.println("  " + line));
                    } else {
                        System.out.println("Monitor cron job: NOT INSTALLED");
                    }
                    return 0;
                } catch (Exception e) {
                    System.err.println("✗ Failed to check cron status: " + e.getMessage());
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
            // If no crontab, output will contain "no crontab"
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
            // Try common locations
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

            // Default to jar in target
            return "java -jar " + System.getProperty("user.dir") + "/target/job-match-0.1.0-SNAPSHOT.jar";
        }
    }
}
