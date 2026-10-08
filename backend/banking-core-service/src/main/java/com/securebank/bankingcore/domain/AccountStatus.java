package com.securebank.bankingcore.domain;

/**
 * Account lifecycle. Policy (spec §16, contract §2): a FROZEN account cannot send money but can still
 * receive it; a CLOSED account can neither send nor receive.
 */
public enum AccountStatus {
    ACTIVE,
    FROZEN,
    CLOSED
}
