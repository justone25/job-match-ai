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
                MonitorCommand.ShowCommand.class,
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
        System.out.println("  show    Show job details");
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
                System.out.println("─".repeat(85));
                System.out.printf(" %-3s │ %-35s │ %-18s │ %s%n", "#", "Title", "Company", "Salary");
                System.out.println("─".repeat(85));

                int count = 0;
                for (BossJob job : jobs) {
                    if (count >= limit) break;
                    count++;
                    System.out.printf(" %-3d │ %-35s │ %-18s │ %s%n",
                            count,
                            truncate(job.getTitle(), 35),
                            truncate(job.getCompany(), 18),
                            job.getSalary() != null ? job.getSalary() : "");
                }

                if (jobs.size() > limit) {
                    System.out.println("... and " + (jobs.size() - limit) + " more");
                }
                System.out.println();
                System.out.println("Tip: Use 'jobmatch monitor show <#>' to view job details");
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
     * Show job details.
     */
    @Command(name = "show", description = "Show job details")
    public static class ShowCommand implements Callable<Integer> {

        @picocli.CommandLine.Parameters(index = "0", description = "Job number (from list) or job ID")
        private String jobRef;

        @Option(names = {"--open"}, description = "Open job URL in browser")
        private boolean openBrowser;

        @Override
        public Integer call() {
            try {
                MonitorService service = new MonitorService();
                List<BossJob> jobs = service.getAllJobs();

                if (jobs.isEmpty()) {
                    System.out.println("No jobs collected yet.");
                    return 0;
                }

                BossJob job = findJob(jobs, jobRef);
                if (job == null) {
                    System.err.println("✗ Job not found: " + jobRef);
                    System.out.println("  Use 'jobmatch monitor list' to see available jobs");
                    return 1;
                }

                // Display job details
                printJobDetails(job);

                // Open in browser if requested
                if (openBrowser && job.getUrl() != null) {
                    openUrl(job.getUrl());
                }

                return 0;
            } catch (Exception e) {
                System.err.println("✗ Failed to show job: " + e.getMessage());
                return 1;
            }
        }

        private BossJob findJob(List<BossJob> jobs, String ref) {
            // Try to parse as number (1-based index)
            try {
                int index = Integer.parseInt(ref);
                if (index >= 1 && index <= jobs.size()) {
                    return jobs.get(index - 1);
                }
            } catch (NumberFormatException ignored) {
            }

            // Try to match by job ID or partial title/company
            String lowerRef = ref.toLowerCase();
            for (BossJob job : jobs) {
                if (job.getJobId().equals(ref)) {
                    return job;
                }
                if (job.getTitle() != null && job.getTitle().toLowerCase().contains(lowerRef)) {
                    return job;
                }
                if (job.getCompany() != null && job.getCompany().toLowerCase().contains(lowerRef)) {
                    return job;
                }
            }
            return null;
        }

        private void printJobDetails(BossJob job) {
            System.out.println();
            System.out.println("═".repeat(70));
            System.out.println("  " + (job.getTitle() != null ? job.getTitle() : "Unknown Title"));
            System.out.println("═".repeat(70));
            System.out.println();

            // Basic info
            printField("Company", job.getCompany());
            printField("Salary", job.getSalary());
            printField("Location", formatLocation(job));
            printField("Experience", job.getExperience());
            printField("Education", job.getEducation());
            System.out.println();

            // Description
            if (job.getDescription() != null && !job.getDescription().isEmpty()) {
                System.out.println("─".repeat(70));
                System.out.println("  Job Description");
                System.out.println("─".repeat(70));
                System.out.println();
                // Format description with word wrap
                printWrapped(job.getDescription(), 68);
                System.out.println();
            }

            // Link
            System.out.println("─".repeat(70));
            printField("URL", job.getUrl());
            printField("Collected", job.getFirstSeenAt() != null ?
                    job.getFirstSeenAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "");
            System.out.println();
            System.out.println("Tip: Use 'jobmatch monitor show " + job.getJobId() + " --open' to open in browser");
            System.out.println();
        }

        private void printField(String label, String value) {
            if (value != null && !value.isEmpty()) {
                System.out.printf("  %-12s: %s%n", label, value);
            }
        }

        private String formatLocation(BossJob job) {
            StringBuilder sb = new StringBuilder();
            if (job.getCity() != null && !job.getCity().isEmpty()) {
                sb.append(job.getCity());
            }
            if (job.getDistrict() != null && !job.getDistrict().isEmpty()) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(job.getDistrict());
            }
            return sb.toString();
        }

        private void printWrapped(String text, int width) {
            if (text == null) return;
            // Split into paragraphs and wrap each
            String[] paragraphs = text.split("\n");
            for (String para : paragraphs) {
                para = para.trim();
                if (para.isEmpty()) {
                    System.out.println();
                    continue;
                }
                // Simple word wrap
                int pos = 0;
                while (pos < para.length()) {
                    int end = Math.min(pos + width, para.length());
                    if (end < para.length() && para.charAt(end) != ' ') {
                        // Find last space
                        int lastSpace = para.lastIndexOf(' ', end);
                        if (lastSpace > pos) {
                            end = lastSpace;
                        }
                    }
                    System.out.println("  " + para.substring(pos, end).trim());
                    pos = end + 1;
                }
            }
        }

        private void openUrl(String url) {
            try {
                String os = System.getProperty("os.name").toLowerCase();
                ProcessBuilder pb;
                if (os.contains("mac")) {
                    pb = new ProcessBuilder("open", url);
                } else if (os.contains("win")) {
                    pb = new ProcessBuilder("cmd", "/c", "start", url);
                } else {
                    pb = new ProcessBuilder("xdg-open", url);
                }
                pb.start();
                System.out.println("  Opening in browser...");
            } catch (Exception e) {
                System.err.println("  Failed to open browser: " + e.getMessage());
            }
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
