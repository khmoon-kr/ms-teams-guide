package com.example.teams.security;

import java.time.Instant;

public record LinkVerificationResult(
        LinkVerificationStatus status,
        String message,
        String source,
        String cardType,
        String action,
        Instant issuedAt,
        Instant expiresAt,
        String canonicalPayload
) {
    public boolean isVerified() {
        return status == LinkVerificationStatus.VERIFIED_TEAMS;
    }
}
