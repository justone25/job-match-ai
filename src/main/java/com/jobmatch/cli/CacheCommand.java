package com.jobmatch.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Command for managing cache.
 */
@Command(
        name = "cache",
        description = "Manage cache",
        subcommands = {
                CacheCommand.StatusCommand.class,
                CacheCommand.ClearCommand.class
        }
)
public class CacheCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        // Default: show cache status
        return new StatusCommand().call();
    }

    @Command(name = "status", description = "Show cache status")
    static class StatusCommand implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println();
            System.out.println("Cache Status:");
            System.out.println("-------------");
            System.out.println();
            System.out.println("Cache directory: ~/.jobmatch/cache/");
            System.out.println();
            System.out.println("[Info] Cache statistics - Coming soon in Phase 3...");
            System.out.println();
            return 0;
        }
    }

    @Command(name = "clear", description = "Clear cache")
    static class ClearCommand implements Callable<Integer> {
        @Option(names = {"--expired"}, description = "Only clear expired cache")
        private boolean expiredOnly;

        @Option(names = {"--type"}, description = "Cache type to clear: jd, resume, skill")
        private String type;

        @Override
        public Integer call() {
            System.out.println();
            if (expiredOnly) {
                System.out.println("[Info] Clearing expired cache - Coming soon...");
            } else if (type != null) {
                System.out.println("[Info] Clearing " + type + " cache - Coming soon...");
            } else {
                System.out.println("[Info] Clearing all cache - Coming soon...");
            }
            System.out.println();
            return 0;
        }
    }
}
