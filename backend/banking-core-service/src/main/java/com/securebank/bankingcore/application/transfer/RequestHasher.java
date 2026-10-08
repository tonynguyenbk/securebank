package com.securebank.bankingcore.application.transfer;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 fingerprint of a transfer request, used to detect an Idempotency-Key reused with a different
 * payload (409 IDEMPOTENCY_KEY_CONFLICT). It hashes the normalized {@link TransferCommand} (amount at scale 2,
 * currency upper-case, description trimmed), so semantically identical retries hash identically regardless of
 * JSON formatting or field order. Each field is length-prefixed, so no two different field tuples can produce
 * the same canonical string.
 */
@Component
public class RequestHasher {

    private static final String VERSION = "transfer-v1";

    public String hash(TransferCommand command) {
        String canonical = field(VERSION)
                + field(command.sourceAccountNumber())
                + field(command.destinationAccountNumber())
                + field(command.amount().toPlainString())
                + field(command.currency())
                + field(command.description() == null ? "" : command.description());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String field(String value) {
        return value.length() + ":" + value + ";";
    }
}
