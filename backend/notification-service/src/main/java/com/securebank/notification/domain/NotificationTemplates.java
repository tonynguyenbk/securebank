package com.securebank.notification.domain;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;

/**
 * English rendering of each template (the fallback text; the UI localizes from templateCode + params).
 * Pure and stateless. Subjects are deliberately generic (no names, no amounts) because they appear in logs.
 */
public final class NotificationTemplates {

    private NotificationTemplates() {
    }

    public record Rendered(String subject, String message) {
    }

    public static Rendered render(TemplateCode code, Map<String, Object> p) {
        return switch (code) {
            case TRANSFER_SENT -> new Rendered("Transfer sent",
                    "You sent %s %s to %s (%s) from account %s. Reference %s. Available balance: %s %s.".formatted(
                            money(p, "amount"), text(p, "currency"), text(p, "counterpartyName"),
                            text(p, "counterpartyAccountNumber"), text(p, "accountNumber"), text(p, "reference"),
                            money(p, "balanceAfter"), text(p, "currency")));
            case TRANSFER_RECEIVED -> new Rendered("Money received",
                    "You received %s %s from %s into account %s. Reference %s. New balance: %s %s.".formatted(
                            money(p, "amount"), text(p, "currency"), text(p, "counterpartyName"),
                            text(p, "accountNumber"), text(p, "reference"), money(p, "balanceAfter"),
                            text(p, "currency")));
            case TRANSFER_REJECTED -> new Rendered("Transfer not completed",
                    "Your transfer of %s %s from account %s to %s was not completed (%s). Reference %s.".formatted(
                            money(p, "amount"), text(p, "currency"), text(p, "accountNumber"),
                            text(p, "counterpartyAccountNumber"), text(p, "failureCode"), text(p, "reference")));
            case ACCOUNT_FROZEN -> new Rendered("Account frozen",
                    "Your account %s has been frozen. Outgoing transfers are blocked until it is reactivated. "
                            .formatted(text(p, "accountNumber"))
                            + "Please contact SecureBank support if you have questions.");
            case ACCOUNT_UNFROZEN -> new Rendered("Account reactivated",
                    "Your account %s is active again. You can send transfers as usual."
                            .formatted(text(p, "accountNumber")));
            case WELCOME -> new Rendered("Welcome to SecureBank",
                    "Hello %s, welcome to SecureBank! Your VND current account is being opened and will appear in the app shortly."
                            .formatted(text(p, "fullName")));
        };
    }

    /** Formats money for humans: 1000000.00 → "1,000,000"; 1500.5 → "1,500.5". */
    static String money(Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value == null) {
            return "-";
        }
        BigDecimal amount = value instanceof BigDecimal bd ? bd : new BigDecimal(String.valueOf(value));
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
        return format.format(amount);
    }

    private static String text(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? "-" : String.valueOf(value);
    }
}
