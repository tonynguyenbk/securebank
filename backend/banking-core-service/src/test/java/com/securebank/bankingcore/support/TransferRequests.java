package com.securebank.bankingcore.support;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Builds POST /api/v1/transfers requests. */
public final class TransferRequests {

    private TransferRequests() {
    }

    public static MockHttpServletRequestBuilder transfer(String token, String key, String from, String to,
                                                         String amount, String description) {
        String desc = description == null ? "null" : "\"" + description + "\"";
        String body = """
                {"sourceAccountNumber":"%s","destinationAccountNumber":"%s","amount":%s,"currency":"VND","description":%s}
                """.formatted(from, to, amount, desc);
        MockHttpServletRequestBuilder request = post("/api/v1/transfers")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return request;
    }

    public static String newKey() {
        return java.util.UUID.randomUUID().toString();
    }
}
