package com.jobmatch.cli;

import com.jobmatch.JobMatchApplication;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Main CLI command for JobMatch AI.
 * Supports subcommands: analyze, history, config, cache, feedback, version.
 */
@Command(
        name = "jobmatch",
        description = "JobMatch AI - Intelligent Job Matching Assistant",
        mixinStandardHelpOptions = true,
        version = JobMatchApplication.VERSION,
        subcommands = {
                AnalyzeCommand.class,
                HistoryCommand.class,
                ConfigCommand.class,
                CacheCommand.class,
                DictionaryCommand.class,
                FeedbackCommand.class,
                VersionCommand.class,
                CommandLine.HelpCommand.class
        }
)
public class JobMatchCommand implements Callable<Integer> {

    @Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    private boolean verbose;

    @Override
    public Integer call() {
        // When no subcommand is provided, show help
        CommandLine.usage(this, System.out);
        return 0;
    }

    public boolean isVerbose() {
        return verbose;
    }
}
