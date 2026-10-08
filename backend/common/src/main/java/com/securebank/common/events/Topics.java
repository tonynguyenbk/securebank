package com.securebank.common.events;

/** Kafka topic names. Versioned so a breaking schema change gets a new topic (spec §17). */
public final class Topics {

    public static final String TRANSACTION_COMPLETED = "bank.transaction.completed.v1";
    public static final String TRANSACTION_FAILED = "bank.transaction.failed.v1";
    public static final String FRAUD_ALERT_CREATED = "bank.fraud.alert.created.v1";
    public static final String ACCOUNT_STATUS_CHANGED = "bank.account.status.changed.v1";
    public static final String AUDIT_EVENT = "bank.audit.event.v1";
    public static final String USER_REGISTERED = "bank.user.registered.v1";

    public static final String[] ALL = {
            TRANSACTION_COMPLETED, TRANSACTION_FAILED, FRAUD_ALERT_CREATED,
            ACCOUNT_STATUS_CHANGED, AUDIT_EVENT, USER_REGISTERED
    };

    private Topics() {
    }
}
