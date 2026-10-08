package com.securebank.bankingcore.security;

/** {@code @PreAuthorize} expressions matching the role columns of docs/contracts/api.md §2–§3. */
public final class Roles {

    /** C — customer endpoints. */
    public static final String CUSTOMER = "hasRole('CUSTOMER')";
    /** S A AD — staff/admin read endpoints. */
    public static final String STAFF_AUDITOR_ADMIN = "hasAnyRole('BANK_STAFF', 'AUDITOR', 'ADMIN')";
    /** S AD — mutations (freeze, unfreeze, limits). AUDITOR never mutates. */
    public static final String STAFF_ADMIN = "hasAnyRole('BANK_STAFF', 'ADMIN')";
    /** A AD — reconciliation. */
    public static final String AUDITOR_ADMIN = "hasAnyRole('AUDITOR', 'ADMIN')";

    private Roles() {
    }
}
