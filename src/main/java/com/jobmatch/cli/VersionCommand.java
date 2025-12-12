package com.jobmatch.cli;

import com.jobmatch.JobMatchApplication;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * Command to display version information.
 */
@Command(
        name = "version",
        description = "Display version information"
)
public class VersionCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        System.out.println();
        System.out.println("  " + JobMatchApplication.APP_NAME + " v" + JobMatchApplication.VERSION);
        System.out.println();
        System.out.println("  A CLI tool for intelligent resume-JD matching analysis.");
        System.out.println();
        System.out.println("  Java Version: " + System.getProperty("java.version"));
        System.out.println("  OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println();
        return 0;
    }
}
