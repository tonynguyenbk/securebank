package com.securebank.common.web;

/** Masks account numbers for logs: 1000000001 → ******0001. */
public final class Masking {

    private Masking() {
    }

    public static String accountNumber(String number) {
        if (number == null || number.length() <= 4) {
            return "****";
        }
        return "*".repeat(number.length() - 4) + number.substring(number.length() - 4);
    }
}
