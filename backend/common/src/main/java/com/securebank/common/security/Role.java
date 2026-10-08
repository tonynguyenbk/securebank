package com.securebank.common.security;

public enum Role {
    CUSTOMER,
    BANK_STAFF,
    AUDITOR,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
