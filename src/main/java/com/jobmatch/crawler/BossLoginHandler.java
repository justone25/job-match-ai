package com.jobmatch.crawler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Handles BOSS login state management.
 * Saves and loads cookies to maintain login session.
 */
public class BossLoginHandler {

    private static final Logger log = LoggerFactory.getLogger(BossLoginHandler.class);
    private static final Path COOKIE_FILE = Paths.get(
            System.getProperty("user.home"), ".jobmatch", "boss_cookies.json");
    private static final String LOGIN_URL = "https://www.zhipin.com/web/user/?ka=header-login";
    private static final String CHECK_URL = "https://www.zhipin.com/web/geek/job";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Ensure user is logged in. If not, prompt for manual login.
     *
     * @param context browser context to use
     * @return true if logged in successfully
     */
    public boolean ensureLogin(BrowserContext context) {
        // Try to load existing cookies
        if (loadCookies(context)) {
            if (isLoggedIn(context)) {
                log.info("Logged in using saved cookies");
                return true;
            }
            log.info("Saved cookies expired, need to re-login");
        }

        // Need manual login
        return performManualLogin(context);
    }

    /**
     * Perform manual login by opening browser and waiting for user.
     */
    public boolean performManualLogin(BrowserContext context) {
        Page page = context.newPage();
        try {
            System.out.println();
            System.out.println("╔══════════════════════════════════════════════════╗");
            System.out.println("║           BOSS 直聘登录                          ║");
            System.out.println("╚══════════════════════════════════════════════════╝");
            System.out.println();
            System.out.println("请在打开的浏览器中完成登录（扫码或手机验证码）");
            System.out.println("登录成功后，按回车键继续...");
            System.out.println();

            page.navigate(LOGIN_URL);

            // Wait for user to press Enter
            Scanner scanner = new Scanner(System.in);
            scanner.nextLine();

            // Verify login
            if (isLoggedIn(context)) {
                saveCookies(context);
                System.out.println("✓ 登录成功，Cookie 已保存");
                return true;
            } else {
                System.out.println("✗ 登录验证失败，请重试");
                return false;
            }
        } finally {
            page.close();
        }
    }

    /**
     * Check if user is currently logged in.
     */
    public boolean isLoggedIn(BrowserContext context) {
        Page page = context.newPage();
        try {
            page.navigate(CHECK_URL);
            page.waitForLoadState();

            // Check for login indicators
            // If redirected to login page or see login button, not logged in
            String url = page.url();
            if (url.contains("/web/user/") || url.contains("login")) {
                return false;
            }

            // Check for user avatar or nickname element
            try {
                page.waitForSelector(".user-nav", new Page.WaitForSelectorOptions().setTimeout(3000));
                return true;
            } catch (Exception e) {
                // Try another selector
                try {
                    page.waitForSelector(".header-login-btn", new Page.WaitForSelectorOptions().setTimeout(1000));
                    return false; // Login button visible means not logged in
                } catch (Exception e2) {
                    // No login button, assume logged in
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check login status: {}", e.getMessage());
            return false;
        } finally {
            page.close();
        }
    }

    /**
     * Save cookies to file.
     */
    public void saveCookies(BrowserContext context) {
        try {
            Files.createDirectories(COOKIE_FILE.getParent());
            List<com.microsoft.playwright.options.Cookie> cookies = context.cookies();
            objectMapper.writeValue(COOKIE_FILE.toFile(), cookies);
            log.info("Saved {} cookies to {}", cookies.size(), COOKIE_FILE);
        } catch (IOException e) {
            log.error("Failed to save cookies", e);
        }
    }

    /**
     * Load cookies from file.
     *
     * @return true if cookies were loaded successfully
     */
    public boolean loadCookies(BrowserContext context) {
        if (!Files.exists(COOKIE_FILE)) {
            log.debug("No cookie file found");
            return false;
        }

        try {
            List<Map<String, Object>> cookieData = objectMapper.readValue(
                    COOKIE_FILE.toFile(),
                    new TypeReference<List<Map<String, Object>>>() {});

            // Convert to Playwright cookie format
            for (Map<String, Object> cookie : cookieData) {
                com.microsoft.playwright.options.Cookie c = new com.microsoft.playwright.options.Cookie(
                        (String) cookie.get("name"),
                        (String) cookie.get("value"));

                if (cookie.get("domain") != null) c.setDomain((String) cookie.get("domain"));
                if (cookie.get("path") != null) c.setPath((String) cookie.get("path"));
                if (cookie.get("expires") != null) {
                    Object exp = cookie.get("expires");
                    if (exp instanceof Number) {
                        c.setExpires(((Number) exp).doubleValue());
                    }
                }
                if (cookie.get("httpOnly") != null) c.setHttpOnly((Boolean) cookie.get("httpOnly"));
                if (cookie.get("secure") != null) c.setSecure((Boolean) cookie.get("secure"));
                if (cookie.get("sameSite") != null) {
                    String sameSite = (String) cookie.get("sameSite");
                    c.setSameSite(com.microsoft.playwright.options.SameSiteAttribute.valueOf(sameSite.toUpperCase()));
                }

                context.addCookies(List.of(c));
            }

            log.info("Loaded {} cookies from file", cookieData.size());
            return true;
        } catch (Exception e) {
            log.warn("Failed to load cookies: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Delete saved cookies.
     */
    public void clearCookies() {
        try {
            Files.deleteIfExists(COOKIE_FILE);
            log.info("Cleared saved cookies");
        } catch (IOException e) {
            log.error("Failed to clear cookies", e);
        }
    }

    /**
     * Check if cookies file exists.
     */
    public boolean hasSavedCookies() {
        return Files.exists(COOKIE_FILE);
    }
}
