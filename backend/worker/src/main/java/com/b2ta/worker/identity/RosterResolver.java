package com.b2ta.worker.identity;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives student identity from a submission filename.
 *
 * <p>Canvas filename convention:
 * {@code {student_name}_{late?}_{submission_id}_{filename}.{ext}}
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code lastfirst_12345_6789012_assignment.pdf} → name: "lastfirst", id: "6789012"</li>
 *   <li>{@code john_doe_late_67890_1234567_essay.pdf} → name: "john doe", id: "1234567"</li>
 *   <li>{@code jane_smith_98765_assignment-1.docx} → name: "jane smith", id: "98765"</li>
 * </ul>
 *
 * <p>The pattern captures:
 * <ul>
 *   <li>Group 1: student name (underscores become spaces)</li>
 *   <li>Group 2: Canvas submission identifier (numeric)</li>
 * </ul>
 *
 * <p>Fallback for non-Canvas filenames: strip extension, trim, collapse whitespace,
 * truncate to 200 characters, mark as unverified.
 *
 * @see com.b2ta.common.entity.enums.IdentityStatus
 */
@Component
public class RosterResolver {

    private static final int MAX_DISPLAY_NAME_LENGTH = 200;

    /**
     * Canvas filename pattern:
     * - Group 1: student name segment (everything before optional _late and the numeric segments)
     * - Group 2: Canvas submission identifier (the last numeric segment before the filename)
     *
     * Pattern explanation:
     *   ^(.+?)              — lazy match of the student name (underscores → spaces)
     *   (?:_late)?          — optional "_late" marker (case-insensitive)
     *   (?:_\d+)?           — optional first numeric segment (user/course ID, consumed but not captured)
     *   _(\d+)             — underscore + Canvas submission ID (the last numeric segment)
     *   _[^_]+\.[^.]+$     — underscore + original filename with extension
     *
     * Examples:
     *   lastfirst_12345_6789012_assignment.pdf → name="lastfirst", id="6789012"
     *   john_doe_late_67890_1234567_essay.pdf → name="john doe", id="1234567"
     *   jane_smith_98765_assignment-1.docx → name="jane smith", id="98765"
     */
    private static final Pattern CANVAS_PATTERN =
            Pattern.compile("^(.+?)(?:_late)?(?:_\\d+)?_(\\d+)_[^_]+\\.[^.]+$", Pattern.CASE_INSENSITIVE);

    /**
     * Resolves student identity from the given original filename.
     *
     * @param originalFilename the submission filename (including extension)
     * @return a {@link ResolvedIdentity} with display name, optional Canvas ID, and status
     */
    public ResolvedIdentity resolve(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return ResolvedIdentity.unverified("");
        }

        Matcher matcher = CANVAS_PATTERN.matcher(originalFilename);
        if (matcher.matches()) {
            String rawName = matcher.group(1);
            String canvasSubmissionId = matcher.group(2);

            // Replace underscores with spaces and normalize
            String displayName = normalizeDisplayName(rawName.replace('_', ' '));
            return ResolvedIdentity.verified(displayName, canvasSubmissionId);
        }

        // Fallback: strip extension, normalize
        String stem = stripExtension(originalFilename);
        String displayName = normalizeDisplayName(stem);
        return ResolvedIdentity.unverified(displayName);
    }

    /**
     * Normalizes a display name: trim, collapse consecutive whitespace, truncate to 200 chars.
     */
    private String normalizeDisplayName(String raw) {
        String trimmed = raw.trim();
        String collapsed = trimmed.replaceAll("\\s+", " ");
        if (collapsed.length() > MAX_DISPLAY_NAME_LENGTH) {
            return collapsed.substring(0, MAX_DISPLAY_NAME_LENGTH);
        }
        return collapsed;
    }

    /**
     * Strips the file extension from a filename (everything after the last dot).
     * If there is no dot or the dot is at position 0, returns the filename as-is.
     */
    private String stripExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(0, lastDot);
        }
        return filename;
    }
}
