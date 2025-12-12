package com.jobmatch.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.concurrent.Callable;

/**
 * Command for analyzing resume and JD matching.
 */
@Command(
        name = "analyze",
        description = "Analyze resume and JD matching"
)
public class AnalyzeCommand implements Callable<Integer> {

    @Option(names = {"-r", "--resume"}, description = "Resume file path")
    private File resumeFile;

    @Option(names = {"-j", "--jd"}, description = "JD file path")
    private File jdFile;

    @Option(names = {"--resume-text"}, description = "Resume text content")
    private String resumeText;

    @Option(names = {"--jd-text"}, description = "JD text content")
    private String jdText;

    @Option(names = {"-f", "--format"}, description = "Output format: json, markdown, simple", defaultValue = "markdown")
    private String format;

    @Option(names = {"-o", "--output"}, description = "Output file path")
    private File outputFile;

    @Option(names = {"--no-cache"}, description = "Disable cache, force re-analysis")
    private boolean noCache;

    @Option(names = {"--dry-run"}, description = "Parse only, no matching (for debugging)")
    private boolean dryRun;

    @Override
    public Integer call() {
        // Check if we have required inputs
        boolean hasResume = resumeFile != null || (resumeText != null && !resumeText.isEmpty());
        boolean hasJd = jdFile != null || (jdText != null && !jdText.isEmpty());

        if (!hasResume && !hasJd) {
            // Interactive mode
            return runInteractiveMode();
        }

        if (!hasResume) {
            System.err.println("[Error] Resume is required. Use -r <file> or --resume-text <text>");
            return 1;
        }

        if (!hasJd) {
            System.err.println("[Error] JD is required. Use -j <file> or --jd-text <text>");
            return 1;
        }

        // File mode
        return runFileMode();
    }

    private Integer runInteractiveMode() {
        System.out.println();
        System.out.println("Welcome to JobMatch AI v" + com.jobmatch.JobMatchApplication.VERSION);
        System.out.println("Interactive mode - Coming soon...");
        System.out.println();
        System.out.println("For now, please use file input mode:");
        System.out.println("  jobmatch analyze -r <resume_file> -j <jd_file>");
        System.out.println();
        return 0;
    }

    private Integer runFileMode() {
        System.out.println();
        System.out.println("[Info] Analysis mode - Coming soon in Phase 2...");
        System.out.println();
        System.out.println("Resume: " + (resumeFile != null ? resumeFile.getPath() : "text input"));
        System.out.println("JD: " + (jdFile != null ? jdFile.getPath() : "text input"));
        System.out.println("Format: " + format);
        System.out.println("Cache: " + (noCache ? "disabled" : "enabled"));
        System.out.println();
        return 0;
    }
}
