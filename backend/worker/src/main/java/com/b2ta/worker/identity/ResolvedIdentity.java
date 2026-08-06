package com.b2ta.worker.identity;

import com.b2ta.common.entity.enums.IdentityStatus;

/**
 * Result DTO produced by {@link RosterResolver}.
 * Contains the derived student display name, an optional Canvas submission ID,
 * and an identity verification status.
 */
public record ResolvedIdentity(
        String studentDisplayName,
        String canvasSubmissionId,
        IdentityStatus identityStatus
) {

    /**
     * Creates a verified identity resolved from a Canvas filename.
     */
    public static ResolvedIdentity verified(String displayName, String canvasSubmissionId) {
        return new ResolvedIdentity(displayName, canvasSubmissionId, IdentityStatus.VERIFIED);
    }

    /**
     * Creates an unverified identity derived from a generic filename.
     */
    public static ResolvedIdentity unverified(String displayName) {
        return new ResolvedIdentity(displayName, null, IdentityStatus.UNVERIFIED);
    }
}
