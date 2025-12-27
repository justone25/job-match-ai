package com.jobmatch.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Service for sending desktop notifications on macOS.
 * Uses osascript to display native notifications.
 */
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /**
     * Send a desktop notification.
     *
     * @param title   notification title
     * @param message notification message
     */
    public void notify(String title, String message) {
        try {
            String escapedTitle = escapeForAppleScript(title);
            String escapedMessage = escapeForAppleScript(message);

            String script = String.format(
                    "display notification \"%s\" with title \"%s\" sound name \"default\"",
                    escapedMessage, escapedTitle);

            ProcessBuilder pb = new ProcessBuilder("osascript", "-e", script);
            pb.inheritIO();
            Process p = pb.start();
            int exitCode = p.waitFor();

            if (exitCode == 0) {
                log.debug("Notification sent: {} - {}", title, message);
            } else {
                log.warn("Notification failed with exit code: {}", exitCode);
            }
        } catch (IOException | InterruptedException e) {
            log.error("Failed to send notification", e);
        }
    }

    /**
     * Send notification with subtitle.
     *
     * @param title    notification title
     * @param subtitle notification subtitle
     * @param message  notification message
     */
    public void notifyWithSubtitle(String title, String subtitle, String message) {
        try {
            String escapedTitle = escapeForAppleScript(title);
            String escapedSubtitle = escapeForAppleScript(subtitle);
            String escapedMessage = escapeForAppleScript(message);

            String script = String.format(
                    "display notification \"%s\" with title \"%s\" subtitle \"%s\" sound name \"default\"",
                    escapedMessage, escapedTitle, escapedSubtitle);

            ProcessBuilder pb = new ProcessBuilder("osascript", "-e", script);
            pb.inheritIO();
            Process p = pb.start();
            p.waitFor();

            log.debug("Notification sent: {} - {} - {}", title, subtitle, message);
        } catch (IOException | InterruptedException e) {
            log.error("Failed to send notification", e);
        }
    }

    /**
     * Escape special characters for AppleScript string.
     */
    private String escapeForAppleScript(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ");
    }

    /**
     * Check if notifications are available on this system.
     */
    public boolean isAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", "osascript");
            Process p = pb.start();
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
