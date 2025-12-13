package com.jobmatch.cli;

import com.jobmatch.JobMatchApplication;
import com.jobmatch.config.AppConfig;
import com.jobmatch.config.ConfigLoader;
import com.jobmatch.exception.JobMatchException;
import com.jobmatch.llm.LlmClient;
import com.jobmatch.llm.OllamaClient;
import com.jobmatch.matcher.MatchingService;
import com.jobmatch.model.jd.JDParsed;
import com.jobmatch.model.match.MatchReport;
import com.jobmatch.model.resume.ResumeParsed;
import com.jobmatch.parser.JDParserService;
import com.jobmatch.parser.ResumeParserService;
import com.jobmatch.report.ReportFormatter;
import com.jobmatch.storage.StorageService;
import com.jobmatch.util.FileReaderService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * Command for analyzing resume and JD matching.
 */
@Command(
        name = "analyze",
        description = "Analyze resume and JD matching (supports PDF, TXT, MD files)"
)
public class AnalyzeCommand implements Callable<Integer> {

    @Option(names = {"-r", "--resume"}, description = "Resume file path (PDF/TXT/MD)")
    private File resumeFile;

    @Option(names = {"-j", "--jd"}, description = "JD file path (PDF/TXT/MD)")
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

    // Services
    private LlmClient llmClient;
    private ResumeParserService resumeParser;
    private JDParserService jdParser;
    private MatchingService matchingService;
    private ReportFormatter reportFormatter;
    private StorageService storageService;

    @Override
    public Integer call() {
        try {
            initializeServices();

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

        } catch (JobMatchException e) {
            System.err.println(e.getUserFriendlyMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("\n❌ [Error 5999] 未知错误: " + e.getMessage());
            System.err.println("\n💡 建议: 请检查日志文件获取详细信息\n");
            return 1;
        }
    }

    private void initializeServices() throws JobMatchException {
        AppConfig config = ConfigLoader.load();

        // Initialize LLM client
        String provider = config.getLlm().getProvider();
        if ("local".equals(provider)) {
            this.llmClient = new OllamaClient(config.getLlm().getLocal());
        } else {
            throw new JobMatchException(3001, "Unsupported LLM provider: " + provider);
        }

        this.resumeParser = new ResumeParserService(llmClient);
        this.jdParser = new JDParserService(llmClient);
        this.matchingService = new MatchingService();
        this.reportFormatter = new ReportFormatter();
        this.storageService = StorageService.getInstance();
    }

    private Integer runInteractiveMode() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║     JobMatch AI v" + JobMatchApplication.VERSION + " - 交互模式              ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println();

        Scanner scanner = new Scanner(System.in);

        try {
            // Get resume
            System.out.println("请输入简历内容 (输入空行结束):");
            System.out.println("─".repeat(50));
            String resumeContent = readMultilineInput(scanner);

            if (resumeContent.isEmpty()) {
                System.err.println("[Error] 简历内容不能为空");
                return 1;
            }

            // Get JD
            System.out.println();
            System.out.println("请输入职位描述 (输入空行结束):");
            System.out.println("─".repeat(50));
            String jdContent = readMultilineInput(scanner);

            if (jdContent.isEmpty()) {
                System.err.println("[Error] 职位描述不能为空");
                return 1;
            }

            // Perform analysis
            return performAnalysis(resumeContent, jdContent);

        } catch (Exception e) {
            System.err.println("[Error] " + e.getMessage());
            return 1;
        }
    }

    private Integer runFileMode() {
        FileReaderService fileReader = new FileReaderService();

        // Read resume
        String resumeContent = this.resumeText;
        if (resumeFile != null) {
            System.out.println("[Info] 读取简历文件: " + resumeFile.getName());
            resumeContent = fileReader.readFile(resumeFile);
        }

        // Read JD
        String jdContent = this.jdText;
        if (jdFile != null) {
            System.out.println("[Info] 读取JD文件: " + jdFile.getName());
            jdContent = fileReader.readFile(jdFile);
        }

        // Perform analysis
        return performAnalysis(resumeContent, jdContent);
    }

    private Integer performAnalysis(String resumeContent, String jdContent) throws JobMatchException {
        System.out.println();
        System.out.println("[1/3] 解析简历中...");
        ResumeParsed resume = resumeParser.parse(resumeContent);
        System.out.println("  ✓ 提取到 " + resume.getSkills().size() + " 项技能, " +
                resume.getExperiences().size() + " 段工作经历");

        System.out.println("[2/3] 解析职位描述中...");
        JDParsed jd = jdParser.parse(jdContent);
        System.out.println("  ✓ 提取到 " + jd.getHardRequirements().size() + " 项硬性要求, " +
                jd.getSoftRequirements().size() + " 项软性要求");

        if (dryRun) {
            System.out.println();
            System.out.println("[Info] Dry-run mode - skipping match analysis");
            System.out.println();
            System.out.println("=== Resume Parse Result ===");
            System.out.println(reportFormatter.formatJson(createDryRunReport(resume, jd)));
            return 0;
        }

        System.out.println("[3/3] 匹配分析中...");
        MatchReport report = matchingService.match(resume, jd);
        System.out.println("  ✓ 分析完成");

        // Save to history
        try {
            String reportId = storageService.saveReport(report);
            System.out.println("  ✓ 已保存 (ID: " + reportId + ")");
        } catch (IOException e) {
            System.err.println("  ⚠ 保存失败: " + e.getMessage());
        }

        System.out.println();

        // Format output
        String output = reportFormatter.format(report, format);

        // Write to file or stdout
        if (outputFile != null) {
            try {
                Files.writeString(outputFile.toPath(), output, StandardCharsets.UTF_8);
                System.out.println("[Info] 报告已保存至: " + outputFile.getPath());
            } catch (IOException e) {
                System.err.println("[Error] Failed to write output file: " + e.getMessage());
                return 1;
            }
        } else {
            System.out.println(output);
        }

        return 0;
    }

    private String readMultilineInput(Scanner scanner) {
        StringBuilder sb = new StringBuilder();
        String line;
        while (scanner.hasNextLine()) {
            line = scanner.nextLine();
            if (line.isEmpty()) {
                break;
            }
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(line);
        }
        return sb.toString().trim();
    }

    /**
     * Create a simple report for dry-run mode showing parse results.
     */
    private MatchReport createDryRunReport(ResumeParsed resume, JDParsed jd) {
        return MatchReport.builder()
                .summary(MatchReport.Summary.builder()
                        .recommendation("Dry-run mode - 仅解析")
                        .overallScore(0)
                        .hardGateStatus("skipped")
                        .matchLevel("N/A")
                        .oneLine("解析完成，跳过匹配分析")
                        .build())
                .build();
    }
}
