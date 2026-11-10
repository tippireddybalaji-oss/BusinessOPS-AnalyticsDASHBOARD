package com.businessops.dashboard.exception;

/**
 * Discriminator identifying which safeguard tier enforced the 10,000-document processing limit.
 */
public enum LimitSafeguardType {
    /**
     * Tier 1: Rejected before streaming payloads via server-side aggregate count query.
     */
    PREFLIGHT_COUNT_SAFEGUARD,

    /**
     * Tier 2: Authoritative hard cap triggered during cursor streaming when document 10,001 was encountered.
     */
    RUNTIME_STREAMING_SAFEGUARD
}

