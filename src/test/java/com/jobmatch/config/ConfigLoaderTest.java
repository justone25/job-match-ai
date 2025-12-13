package com.jobmatch.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConfigLoader.
 */
class ConfigLoaderTest {

    @BeforeEach
    void setUp() {
        // Force reload to clear any cached config
        ConfigLoader.reload();
    }

    @Test
    void shouldLoadBuiltinDefaults() {
        AppConfig config = ConfigLoader.load();

        assertNotNull(config);
        assertNotNull(config.getLlm());
        assertEquals("local", config.getLlm().getProvider());
        assertEquals("http://localhost:11434", config.getLlm().getLocal().getBaseUrl());
        assertEquals("qwen2.5:7b", config.getLlm().getLocal().getModel());
    }

    @Test
    void shouldLoadDefaultValues() {
        AppConfig config = ConfigLoader.load();

        // LLM defaults
        assertEquals(0.1, config.getLlm().getCommon().getTemperature());
        assertEquals(4096, config.getLlm().getCommon().getMaxTokens());
        assertEquals(2, config.getLlm().getCommon().getRetryTimes());

        // Storage defaults
        assertNotNull(config.getStorage());
        assertTrue(config.getStorage().isCacheEnabled());
        assertEquals(100, config.getStorage().getCacheMaxSizeMb());

        // Output defaults
        assertNotNull(config.getOutput());
        assertEquals("markdown", config.getOutput().getDefaultFormat());
        assertEquals("zh", config.getOutput().getLanguage());
    }

    @Test
    void shouldLoadConfigFromFile(@TempDir Path tempDir) throws IOException {
        // Create test config file
        Path configFile = tempDir.resolve("test-config.yaml");
        String yamlContent = """
                llm:
                  provider: cloud
                  local:
                    model: llama3:8b
                  cloud:
                    model: gpt-4
                output:
                  language: en
                """;
        Files.writeString(configFile, yamlContent);

        // Load config
        AppConfig config = ConfigLoader.loadFromFile(configFile.toString());

        assertNotNull(config);
        assertEquals("cloud", config.getLlm().getProvider());
        assertEquals("llama3:8b", config.getLlm().getLocal().getModel());
        assertEquals("gpt-4", config.getLlm().getCloud().getModel());
        assertEquals("en", config.getOutput().getLanguage());
    }

    @Test
    void shouldThrowOnMissingFile() {
        assertThrows(IOException.class, () ->
                ConfigLoader.loadFromFile("/nonexistent/path/config.yaml"));
    }

    @Test
    void shouldExpandTildePath() {
        String expanded = ConfigLoader.expandPath("~/test/path");
        String userHome = System.getProperty("user.home");
        assertEquals(userHome + "/test/path", expanded);
    }

    @Test
    void shouldNotExpandAbsolutePath() {
        String path = "/absolute/path";
        String expanded = ConfigLoader.expandPath(path);
        assertEquals(path, expanded);
    }

    @Test
    void shouldHandleNullPath() {
        String expanded = ConfigLoader.expandPath(null);
        assertNull(expanded);
    }

    @Test
    void shouldCacheConfig() {
        AppConfig config1 = ConfigLoader.load();
        AppConfig config2 = ConfigLoader.load();
        assertSame(config1, config2);
    }

    @Test
    void shouldReloadConfig() {
        AppConfig config1 = ConfigLoader.load();
        AppConfig config2 = ConfigLoader.reload();
        // After reload, should be a new instance
        assertNotSame(config1, config2);
    }
}
