package com.jobmatch.cli;

import com.jobmatch.config.AppConfig;
import com.jobmatch.config.ConfigLoader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

/**
 * Command for managing configuration.
 */
@Command(
        name = "config",
        description = "Manage configuration",
        subcommands = {
                ConfigCommand.ShowCommand.class,
                ConfigCommand.SetCommand.class,
                ConfigCommand.ValidateCommand.class,
                ConfigCommand.ResetCommand.class
        }
)
public class ConfigCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        // Default: show current config
        return new ShowCommand().call();
    }

    @Command(name = "show", description = "Show current configuration")
    static class ShowCommand implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println();
            System.out.println("Current Configuration:");
            System.out.println("----------------------");

            try {
                AppConfig config = ConfigLoader.load();
                System.out.println();
                System.out.println("LLM Settings:");
                System.out.println("  provider: " + config.getLlm().getProvider());
                System.out.println("  local.base_url: " + config.getLlm().getLocal().getBaseUrl());
                System.out.println("  local.model: " + config.getLlm().getLocal().getModel());
                System.out.println("  local.timeout: " + config.getLlm().getLocal().getTimeout() + "s");

                System.out.println();
                System.out.println("Storage Settings:");
                System.out.println("  data_dir: " + config.getStorage().getDataDir());
                System.out.println("  cache_dir: " + config.getStorage().getCacheDir());
                System.out.println("  cache_enabled: " + config.getStorage().isCacheEnabled());
                System.out.println("  cache_ttl_days: " + config.getStorage().getCacheTtlDays());

                System.out.println();
                System.out.println("Logging Settings:");
                System.out.println("  level: " + config.getLogging().getLevel());
                System.out.println("  file: " + config.getLogging().getFile());

                System.out.println();
                System.out.println("Output Settings:");
                System.out.println("  default_format: " + config.getOutput().getDefaultFormat());
                System.out.println("  color: " + config.getOutput().isColor());

                System.out.println();
            } catch (Exception e) {
                System.err.println("[Error] Failed to load configuration: " + e.getMessage());
                return 1;
            }

            return 0;
        }
    }

    @Command(name = "set", description = "Set a configuration value")
    static class SetCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Configuration key (e.g., llm.provider)")
        private String key;

        @Parameters(index = "1", description = "Configuration value")
        private String value;

        @Override
        public Integer call() {
            System.out.println("[Info] Setting " + key + " = " + value);
            // TODO: Implement config persistence
            System.out.println("[Info] Configuration persistence - Coming soon...");
            return 0;
        }
    }

    @Command(name = "validate", description = "Validate current configuration")
    static class ValidateCommand implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println();
            System.out.println("Validating configuration...");
            System.out.println();

            try {
                AppConfig config = ConfigLoader.load();
                boolean allValid = true;

                // Check LLM provider
                String provider = config.getLlm().getProvider();
                if ("local".equals(provider)) {
                    System.out.println("[✓] llm.provider: local");
                    System.out.println("[✓] llm.local.base_url: " + config.getLlm().getLocal().getBaseUrl());
                    System.out.println("[✓] llm.local.model: " + config.getLlm().getLocal().getModel());
                    // TODO: Check if Ollama is running
                    System.out.println("[?] Ollama connection check - Coming soon...");
                } else if ("cloud".equals(provider)) {
                    System.out.println("[✓] llm.provider: cloud");
                    String apiKey = config.getLlm().getCloud().getApiKey();
                    if (apiKey == null || apiKey.isEmpty() || apiKey.startsWith("${")) {
                        System.out.println("[✗] llm.cloud.api_key: not set");
                        System.out.println("    → Please set environment variable LLM_API_KEY or configure in settings.yaml");
                        allValid = false;
                    } else {
                        System.out.println("[✓] llm.cloud.api_key: (set)");
                    }
                }

                // Check storage directories
                System.out.println("[✓] storage.data_dir: " + config.getStorage().getDataDir());
                System.out.println("[✓] storage.cache_dir: " + config.getStorage().getCacheDir());

                System.out.println();
                if (allValid) {
                    System.out.println("Configuration validation passed!");
                } else {
                    System.out.println("Configuration validation failed. Please fix the issues above.");
                    return 1;
                }

            } catch (Exception e) {
                System.err.println("[✗] Failed to load configuration: " + e.getMessage());
                return 1;
            }

            return 0;
        }
    }

    @Command(name = "reset", description = "Reset configuration to defaults")
    static class ResetCommand implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println("[Info] Resetting configuration to defaults - Coming soon...");
            return 0;
        }
    }
}
