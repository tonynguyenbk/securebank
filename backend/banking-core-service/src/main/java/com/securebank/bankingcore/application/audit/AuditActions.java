package com.securebank.bankingcore.application.audit;

/** Audit vocabulary emitted by banking-core (docs/contracts/api.md §5). */
public final class AuditActions {

    public static final String ACCOUNT_OPENED = "ACCOUNT_OPENED";
    public static final String TRANSFER_COMPLETED = "TRANSFER_COMPLETED";
    public static final String TRANSFER_REJECTED = "TRANSFER_REJECTED";
    public static final String ACCOUNT_FREEZE = "ACCOUNT_FREEZE";
    public static final String ACCOUNT_UNFREEZE = "ACCOUNT_UNFREEZE";
    public static final String TRANSFER_LIMIT_UPDATE = "TRANSFER_LIMIT_UPDATE";

    public static final String RESOURCE_ACCOUNT = "ACCOUNT";
    public static final String RESOURCE_TRANSACTION = "TRANSACTION";

    private AuditActions() {
    }
}
