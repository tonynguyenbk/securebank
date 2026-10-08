package com.securebank.notification.domain;

/** Template identifiers; the frontend renders them through i18n (EN/VI) from {@code params}. */
public enum TemplateCode {
    TRANSFER_SENT,
    TRANSFER_RECEIVED,
    TRANSFER_REJECTED,
    ACCOUNT_FROZEN,
    ACCOUNT_UNFROZEN,
    WELCOME
}
