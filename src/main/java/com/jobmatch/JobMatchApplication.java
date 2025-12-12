package com.jobmatch;

import com.jobmatch.cli.JobMatchCommand;
import picocli.CommandLine;

/**
 * Main entry point for JobMatch AI CLI application.
 *
 * JobMatch AI is an intelligent job matching assistant that helps
 * technical professionals analyze the match between their resume and job descriptions.
 */
public class JobMatchApplication {

    public static final String VERSION = "0.1.0";
    public static final String APP_NAME = "JobMatch AI";

    public static void main(String[] args) {
        int exitCode = new CommandLine(new JobMatchCommand())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
        System.exit(exitCode);
    }
}
