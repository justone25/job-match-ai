package com.jobmatch.cli;

import com.jobmatch.config.*;
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

                // Show matcher config version
                System.out.println();
                MatcherConfig matcherConfig = MatcherConfigLoader.getInstance().getConfig();
                System.out.println("Matcher Config:");
                System.out.println("  version: " + matcherConfig.getVersion());
                System.out.println("  updated_at: " + matcherConfig.getUpdatedAt());
                System.out.println("  weights: skill=" + matcherConfig.getSoftScore().getWeights().getSkill() +
                        ", experience=" + matcherConfig.getSoftScore().getWeights().getExperience() +
                        ", bonus=" + matcherConfig.getSoftScore().getWeights().getBonus());

                // Show skill implication config version
                System.out.println();
                SkillImplicationConfig implConfig = SkillImplicationLoader.getInstance().getConfig();
                System.out.println("Skill Implication Rules:");
                System.out.println("  version: " + implConfig.getVersion());
                System.out.println("  updated_at: " + implConfig.getUpdatedAt());
                System.out.println("  rules_count: " + implConfig.getRuleCount());
                System.out.println("  llm_fallback: " + (implConfig.getSettings().getLlmFallback().isEnabled() ? "enabled" : "disabled"));

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

    @Command(name = "validate", description = "Validate all configuration files")
    static class ValidateCommand implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println();
            System.out.println("Validating configuration...");
            System.out.println();

            ConfigValidator.ValidationResult result = ConfigValidator.validateAll();

            // Print errors
            if (!result.getErrors().isEmpty()) {
                System.out.println("Errors:");
                for (String error : result.getErrors()) {
                    System.out.println("  [✗] " + error);
                }
                System.out.println();
            }

            // Print warnings
            if (!result.getWarnings().isEmpty()) {
                System.out.println("Warnings:");
                for (String warning : result.getWarnings()) {
                    System.out.println("  [!] " + warning);
                }
                System.out.println();
            }

            // Additional LLM connectivity check
            System.out.println("Checking LLM connectivity...");
            try {
                AppConfig config = ConfigLoader.load();
                String provider = config.getLlm().getProvider();
                if ("local".equals(provider)) {
                    System.out.println("  [?] Ollama at " + config.getLlm().getLocal().getBaseUrl());
                    System.out.println("      Run 'jobmatch analyze' to verify connectivity");
                } else if ("cloud".equals(provider)) {
                    System.out.println("  [✓] Cloud provider configured");
                }
            } catch (Exception e) {
                System.out.println("  [!] Could not check LLM config: " + e.getMessage());
            }

            System.out.println();
            if (result.isValid()) {
                System.out.println("Configuration validation passed!");
                System.out.println("  Errors: 0, Warnings: " + result.getWarningCount());
                return 0;
            } else {
                System.out.println("Configuration validation failed.");
                System.out.println("  Errors: " + result.getErrorCount() + ", Warnings: " + result.getWarningCount());
                return 1;
            }
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
