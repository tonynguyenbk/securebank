package com.securebank.identity.security;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * BCrypt only uses the first 72 bytes of its input, and Spring Security rejects longer inputs.
 * The contract allows passwords of up to 100 characters (and non-ASCII characters take several bytes),
 * so inputs longer than 72 UTF-8 bytes are first reduced to Base64(SHA-256(password)) — 44 bytes that
 * keep the full entropy of the original. Shorter passwords are hashed with plain BCrypt.
 */
public class Bcrypt72SafePasswordEncoder implements PasswordEncoder {

    static final int BCRYPT_MAX_BYTES = 72;

    private final BCryptPasswordEncoder bcrypt;

    public Bcrypt72SafePasswordEncoder(int strength) {
        this.bcrypt = new BCryptPasswordEncoder(strength);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return bcrypt.encode(prepare(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        return bcrypt.matches(prepare(rawPassword), encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return bcrypt.upgradeEncoding(encodedPassword);
    }

    static String prepare(CharSequence raw) {
        String value = raw.toString();
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= BCRYPT_MAX_BYTES) {
            return value;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
