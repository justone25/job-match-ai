package com.jobmatch.util;

import com.jobmatch.exception.ErrorCode;
import com.jobmatch.exception.JobMatchException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;

/**
 * Service for reading content from various file formats.
 * Supports: PDF, TXT, MD, and other text-based formats.
 */
public class FileReaderService {

    private static final Logger log = LoggerFactory.getLogger(FileReaderService.class);

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".txt", ".md", ".markdown", ".text", ".rst"
    );

    private static final Set<String> PDF_EXTENSIONS = Set.of(
            ".pdf"
    );

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    /**
     * Read content from a file, automatically detecting format.
     *
     * @param file the file to read
     * @return text content extracted from the file
     * @throws JobMatchException if file cannot be read
     */
    public String readFile(File file) throws JobMatchException {
        if (file == null) {
            throw new JobMatchException(ErrorCode.FILE_NOT_FOUND, "File is null");
        }

        if (!file.exists()) {
            throw new JobMatchException(ErrorCode.FILE_NOT_FOUND, file.getPath());
        }

        if (!file.isFile()) {
            throw new JobMatchException(ErrorCode.INVALID_FILE_FORMAT, "Not a file: " + file.getPath());
        }

        if (file.length() > MAX_FILE_SIZE) {
            throw new JobMatchException(ErrorCode.FILE_TOO_LARGE,
                    String.format("File size %.2f MB exceeds limit of %.2f MB",
                            file.length() / (1024.0 * 1024.0),
                            MAX_FILE_SIZE / (1024.0 * 1024.0)));
        }

        String extension = getExtension(file.getName()).toLowerCase();

        log.debug("Reading file: {} (extension: {})", file.getName(), extension);

        if (PDF_EXTENSIONS.contains(extension)) {
            return readPdf(file);
        } else if (TEXT_EXTENSIONS.contains(extension) || extension.isEmpty()) {
            return readText(file);
        } else {
            // Try to read as text for unknown extensions
            log.warn("Unknown file extension '{}', attempting to read as text", extension);
            return readText(file);
        }
    }

    /**
     * Read content from a PDF file.
     */
    private String readPdf(File file) throws JobMatchException {
        log.info("Reading PDF file: {}", file.getName());

        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            String text = stripper.getText(document);

            // Clean up extracted text
            text = cleanPdfText(text);

            int pageCount = document.getNumberOfPages();
            log.info("PDF parsed successfully: {} pages, {} characters", pageCount, text.length());

            if (text.trim().isEmpty()) {
                throw new JobMatchException(ErrorCode.RESUME_PARSE_FAILED,
                        "PDF appears to be empty or contains only images");
            }

            return text;

        } catch (IOException e) {
            log.error("Failed to read PDF file: {}", e.getMessage());
            throw new JobMatchException(ErrorCode.STORAGE_READ_FAILED,
                    "Cannot read PDF file: " + e.getMessage(), e);
        }
    }

    /**
     * Read content from a text file.
     */
    private String readText(File file) throws JobMatchException {
        log.debug("Reading text file: {}", file.getName());

        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            log.debug("Text file read successfully: {} characters", content.length());
            return content;

        } catch (IOException e) {
            log.error("Failed to read text file: {}", e.getMessage());
            throw new JobMatchException(ErrorCode.STORAGE_READ_FAILED,
                    "Cannot read file: " + e.getMessage(), e);
        }
    }

    /**
     * Clean up text extracted from PDF.
     * Handles common PDF extraction issues.
     */
    private String cleanPdfText(String text) {
        if (text == null) {
            return "";
        }

        return text
                // Normalize line endings
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                // Remove excessive blank lines
                .replaceAll("\n{3,}", "\n\n")
                // Remove trailing whitespace on each line
                .replaceAll("[ \\t]+\n", "\n")
                // Trim overall
                .trim();
    }

    /**
     * Get file extension including the dot.
     */
    private String getExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            return filename.substring(lastDot);
        }
        return "";
    }

    /**
     * Check if a file format is supported.
     */
    public boolean isSupported(File file) {
        if (file == null || !file.exists()) {
            return false;
        }
        String ext = getExtension(file.getName()).toLowerCase();
        return PDF_EXTENSIONS.contains(ext) || TEXT_EXTENSIONS.contains(ext) || ext.isEmpty();
    }

    /**
     * Get list of supported file extensions.
     */
    public static String getSupportedFormats() {
        return "PDF (.pdf), Text (.txt, .md, .markdown)";
    }
}
