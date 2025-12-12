package com.jobmatch.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

/**
 * Command for submitting feedback.
 */
@Command(
        name = "feedback",
        description = "Submit feedback for an analysis"
)
public class FeedbackCommand implements Callable<Integer> {

    @Parameters(index = "0", description = "Analysis ID to provide feedback for", arity = "0..1")
    private String analysisId;

    @Override
    public Integer call() {
        System.out.println();
        if (analysisId != null) {
            System.out.println("[Info] Submitting feedback for analysis: " + analysisId);
        } else {
            System.out.println("[Info] Submitting feedback for most recent analysis");
        }
        System.out.println();
        System.out.println("[Info] Feedback system - Coming soon in Phase 4...");
        System.out.println();
        return 0;
    }
}
