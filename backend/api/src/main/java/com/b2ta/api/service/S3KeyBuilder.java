package com.b2ta.api.service;

import com.b2ta.api.security.TaPrincipal;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Builds every S3 object key used by the service, always rooted at the owning TA's prefix
 * (Requirement 18.6, 19.3).
 *
 * <p>Pre-signed URLs are the one place where a client receives a capability that S3 honours without
 * consulting the API again, so the key must be derived from the authenticated principal and never
 * from request input. Callers pass a filename; only its extension survives. The object name is a
 * fresh UUID, which also removes the student name from the key — filenames routinely contain it.
 *
 * <p>Layout matches the design document:
 * <pre>
 * uploads/{ta_id}/{session_id}/rubrics/{uuid}.{ext}
 * uploads/{ta_id}/{session_id}/submissions/{uuid}.{ext}
 * exports/{ta_id}/{session_id}/{name}-{timestamp}.csv
 * </pre>
 */
@Component
public class S3KeyBuilder {

    /** Extensions are restricted to a conservative character set to keep keys predictable. */
    private static final Pattern SAFE_EXTENSION = Pattern.compile("^[A-Za-z0-9]{1,8}$");

    public String rubricUpload(TaPrincipal ta, UUID sessionId, String originalFilename) {
        return uploadPrefix(ta, sessionId) + "rubrics/" + UUID.randomUUID()
                + extensionSuffix(originalFilename);
    }

    public String submissionUpload(TaPrincipal ta, UUID sessionId, String originalFilename) {
        return uploadPrefix(ta, sessionId) + "submissions/" + UUID.randomUUID()
                + extensionSuffix(originalFilename);
    }

    /** Key for a generated export; {@code name} is a fixed literal chosen by the caller. */
    public String export(TaPrincipal ta, UUID sessionId, String name, Instant at) {
        return "exports/" + ta.taId() + "/" + sessionId + "/"
                + name + "-" + at.toEpochMilli() + ".csv";
    }

    /** The prefix a pre-signed upload URL for this TA and session must start with. */
    public String uploadPrefix(TaPrincipal ta, UUID sessionId) {
        return "uploads/" + ta.taId() + "/" + sessionId + "/";
    }

    /**
     * Verifies that a key supplied by a client falls inside the requesting TA's prefix.
     *
     * <p>Used before reading or deleting an object identified by a stored key, so a tampered row or
     * a stale client cannot direct the service at another tenant's object.
     */
    public boolean isOwnedBy(String objectKey, TaPrincipal ta) {
        return objectKey != null
                && (objectKey.startsWith("uploads/" + ta.taId() + "/")
                || objectKey.startsWith("exports/" + ta.taId() + "/"));
    }

    private String extensionSuffix(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        String ext = originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return SAFE_EXTENSION.matcher(ext).matches() ? "." + ext : "";
    }
}
