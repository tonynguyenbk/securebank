package com.securebank.gateway.support;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * A fake downstream service that echoes what it received (path and relevant headers) as JSON,
 * so tests can assert on what the gateway forwarded without ordering issues.
 * {@code /api/v1/accounts/conflict} returns a downstream ApiError to check pass-through.
 */
public final class EchoDownstream {

    public static final String DOWNSTREAM_ERROR = """
            {"timestamp":"2026-10-08T03:42:00Z","status":409,"code":"ACCOUNT_STATUS_UNCHANGED",\
            "message":"The account is already in the requested status.","path":"/api/v1/accounts/conflict"}""";

    private static final MockWebServer SERVER = new MockWebServer();

    static {
        SERVER.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if ("/api/v1/accounts/conflict".equals(request.getPath())) {
                    return new MockResponse().setResponseCode(409)
                            .setHeader("Content-Type", "application/json").setBody(DOWNSTREAM_ERROR);
                }
                String body = "{\"path\":%s,\"method\":%s,\"authorization\":%s,\"correlationId\":%s,\"forwardedFor\":%s}"
                        .formatted(q(request.getPath()), q(request.getMethod()), q(request.getHeader("Authorization")),
                                q(request.getHeader("X-Correlation-Id")), q(request.getHeader("X-Forwarded-For")));
                return new MockResponse().setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setHeader("X-Correlation-Id", String.valueOf(request.getHeader("X-Correlation-Id")))
                        .setBody(body);
            }
        });
        try {
            SERVER.start();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private EchoDownstream() {
    }

    public static String url() {
        return "http://localhost:" + SERVER.getPort();
    }

    private static String q(String value) {
        return value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
