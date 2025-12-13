package com.jobmatch.cli;

import com.jobmatch.storage.StorageService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
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
            StorageService storage = StorageService.getInstance();
            StorageService.CacheStats stats = storage.getCacheStats();

            System.out.println();
            System.out.println("╔══════════════════════════════════════════════════╗");
            System.out.println("║                  Cache Status                     ║");
            System.out.println("╚══════════════════════════════════════════════════╝");
            System.out.println();
            System.out.println("  Status:    " + (stats.isEnabled() ? "✅ Enabled" : "❌ Disabled"));
            System.out.println("  Directory: " + stats.getCacheDir());
            System.out.println("  TTL:       " + stats.getTtlDays() + " days");
            System.out.println();
            System.out.println("  Statistics:");
            System.out.println("  ─────────────────────────────");
            System.out.println("  Files:     " + stats.getFileCount());
            System.out.println("  Size:      " + stats.getTotalSizeMb() + " MB");
            System.out.println("  Expired:   " + stats.getExpiredCount());
            System.out.println();

            if (stats.getExpiredCount() > 0) {
                System.out.println("  Tip: Run 'jobmatch cache clear --expired' to remove expired entries.");
            }

            return 0;
        }
    }

    @Command(name = "clear", description = "Clear cache")
    static class ClearCommand implements Callable<Integer> {
        @Option(names = {"--expired"}, description = "Only clear expired cache")
        private boolean expiredOnly;

        @Option(names = {"-f", "--force"}, description = "Skip confirmation")
        private boolean force;

        @Override
        public Integer call() {
            StorageService storage = StorageService.getInstance();

            if (expiredOnly) {
                int count = storage.clearExpiredCache();
                System.out.println("[Info] Cleared " + count + " expired cache entries.");
                return 0;
            }

            // Clear all cache
            if (!force) {
                System.out.print("Are you sure you want to clear all cache? (y/N): ");
                try {
                    int ch = System.in.read();
                    if (ch != 'y' && ch != 'Y') {
                        System.out.println("Cancelled.");
                        return 0;
                    }
                } catch (IOException e) {
                    System.out.println("Cancelled.");
                    return 0;
                }
            }

            int count = storage.clearAllCache();
            System.out.println("[Info] Cleared " + count + " cache entries.");
            return 0;
        }
    }
}
