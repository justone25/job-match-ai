package com.jobmatch.parser;

import com.jobmatch.model.resume.Experience;
import com.jobmatch.model.resume.Project;
import com.jobmatch.model.resume.ResumeParsed;
import com.jobmatch.model.resume.Skill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates parsed results and verifies evidence references.
 * Based on PRD v3.2 section 14.2 - output validation rules.
 */
public class ParseValidator {

    private static final Logger log = LoggerFactory.getLogger(ParseValidator.class);
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.5;

    /**
     * Validation result with details.
     */
    public record ValidationResult(
            boolean valid,
            List<String> errors,
            List<String> warnings,
            int invalidEvidenceCount
    ) {
        public static ValidationResult success() {
            return new ValidationResult(true, List.of(), List.of(), 0);
        }
    }

    /**
     * Validate parsed resume result.
     */
    public ValidationResult validateResumeParsed(ResumeParsed parsed) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int invalidEvidenceCount = 0;

        String originalText = parsed.getOriginalText();

        // Check basic info
        if (parsed.getBasicInfo() == null) {
            errors.add("Missing basic_info section");
        }

        // Check skills
        if (parsed.getSkills() == null || parsed.getSkills().isEmpty()) {
            warnings.add("No skills extracted from resume");
        } else {
            for (int i = 0; i < parsed.getSkills().size(); i++) {
                Skill skill = parsed.getSkills().get(i);
                String prefix = "skills[" + i + "]";

                if (skill.getName() == null || skill.getName().isEmpty()) {
                    errors.add(prefix + ": missing skill name");
                }

                if (skill.getConfidence() != null && skill.getConfidence() < MIN_CONFIDENCE_THRESHOLD) {
                    warnings.add(prefix + " '" + skill.getName() + "': low confidence " + skill.getConfidence());
                }

                // Verify evidence exists in original text
                if (originalText != null && skill.getEvidence() != null) {
                    for (String evidence : skill.getEvidence()) {
                        if (!containsEvidence(originalText, evidence)) {
                            invalidEvidenceCount++;
                            warnings.add(prefix + ": evidence not found in original text: " + truncate(evidence, 50));
                        }
                    }
                }
            }
        }

        // Check experiences
        if (parsed.getExperiences() != null) {
            for (int i = 0; i < parsed.getExperiences().size(); i++) {
                Experience exp = parsed.getExperiences().get(i);
                String prefix = "experience[" + i + "]";

                if (exp.getCompany() == null || exp.getCompany().isEmpty()) {
                    warnings.add(prefix + ": missing company name");
                }

                // Verify evidence
                if (originalText != null && exp.getEvidence() != null) {
                    for (String evidence : exp.getEvidence()) {
                        if (!containsEvidence(originalText, evidence)) {
                            invalidEvidenceCount++;
                            warnings.add(prefix + ": evidence not found in original text");
                        }
                    }
                }
            }
        }

        // Check projects
        if (parsed.getProjects() != null) {
            for (int i = 0; i < parsed.getProjects().size(); i++) {
                Project proj = parsed.getProjects().get(i);
                String prefix = "projects[" + i + "]";

                // Verify evidence
                if (originalText != null && proj.getEvidence() != null) {
                    for (String evidence : proj.getEvidence()) {
                        if (!containsEvidence(originalText, evidence)) {
                            invalidEvidenceCount++;
                            warnings.add(prefix + ": evidence not found in original text");
                        }
                    }
                }
            }
        }

        // Check overall confidence
        if (parsed.getParseMeta() != null &&
                parsed.getParseMeta().getOverallConfidence() != null &&
                parsed.getParseMeta().getOverallConfidence() < MIN_CONFIDENCE_THRESHOLD) {
            warnings.add("Overall parsing confidence is low: " + parsed.getParseMeta().getOverallConfidence());
        }

        boolean valid = errors.isEmpty();

        if (!valid) {
            log.warn("Resume validation failed with {} errors, {} warnings",
                    errors.size(), warnings.size());
        } else if (!warnings.isEmpty()) {
            log.info("Resume validation passed with {} warnings", warnings.size());
        }

        return new ValidationResult(valid, errors, warnings, invalidEvidenceCount);
    }

    /**
     * Check if evidence text exists in original content.
     * Uses fuzzy matching to handle minor differences.
     */
    private boolean containsEvidence(String original, String evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return true;
        }

        // Normalize both strings for comparison
        String normalizedOriginal = normalize(original);
        String normalizedEvidence = normalize(evidence);

        // Direct substring match
        if (normalizedOriginal.contains(normalizedEvidence)) {
            return true;
        }

        // Try with shorter evidence (first 20 chars) for fuzzy match
        if (normalizedEvidence.length() > 20) {
            String shortEvidence = normalizedEvidence.substring(0, 20);
            return normalizedOriginal.contains(shortEvidence);
        }

        return false;
    }

    /**
     * Normalize text for comparison.
     */
    private String normalize(String text) {
        return text.toLowerCase()
                .replaceAll("\\s+", " ")
                .replaceAll("[\uff0c\u3002\u3001\uff1b\uff1a\u201c\u201d\u2018\u2019\u3010\u3011\u300a\u300b]", " ")
                .trim();
    }

    /**
     * Truncate text for display.
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }

    /**
     * Mark low confidence items in parsed result.
     */
    public void markLowConfidenceItems(ResumeParsed parsed) {
        if (parsed.getSkills() != null) {
            for (Skill skill : parsed.getSkills()) {
                if (skill.getConfidence() != null && skill.getConfidence() < MIN_CONFIDENCE_THRESHOLD) {
                    skill.setLevel(Skill.LEVEL_UNKNOWN);
                }
            }
        }
    }
}
