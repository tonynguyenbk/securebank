package com.securebank.bankingcore.application.transfer;

import com.securebank.bankingcore.api.CreateTransferRequest;
import com.securebank.common.error.ApiException;
import com.securebank.common.error.ErrorCode;
import com.securebank.common.security.AuthenticatedUser;
import com.securebank.common.security.Role;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Orchestration rules of {@link TransferService}: fast replay, rejection path, metrics, no persistence on 400. */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    private static final String KEY = "key-12345678";

    @Mock
    private IdempotencyService idempotency;
    @Mock
    private TransferExecutor executor;
    @Mock
    private TransferRejectionRecorder rejections;

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "customer1",
            Set.of(Role.CUSTOMER), "jti", Instant.now().plusSeconds(60));
    private TransferService service;

    @BeforeEach
    void setUp() {
        service = new TransferService(new TransferRequestValidator(), new RequestHasher(), idempotency, executor,
                rejections, new TransferMetrics(meters));
    }

    @Test
    void storedResponseIsReplayedWithoutExecuting() {
        TransferResult stored = new TransferResult(201, "{}", true, UUID.randomUUID());
        when(idempotency.findReplay(eq(user.userId()), eq(KEY), anyString())).thenReturn(Optional.of(stored));

        assertThat(service.submit(user, KEY, request("100"))).isSameAs(stored);
        verifyNoInteractions(executor, rejections);
        assertThat(meters.counter("transfer_idempotent_replay_total").count()).isEqualTo(1);
    }

    @Test
    void successIsCounted() {
        when(idempotency.findReplay(any(), any(), any())).thenReturn(Optional.empty());
        when(executor.execute(eq(user), eq(KEY), anyString(), any()))
                .thenReturn(new TransferResult(201, "{}", false, UUID.randomUUID()));

        assertThat(service.submit(user, KEY, request("100")).status()).isEqualTo(201);
        assertThat(meters.counter("transfer_success_total").count()).isEqualTo(1);
        assertThat(meters.timer("transfer_latency").count()).isEqualTo(1);
    }

    @Test
    void businessRejectionIsRecordedInASeparateStep() {
        when(idempotency.findReplay(any(), any(), any())).thenReturn(Optional.empty());
        TransferRejectedException rejection = new TransferRejectedException(ErrorCode.INSUFFICIENT_FUNDS, "no money",
                UUID.randomUUID(), UUID.randomUUID());
        when(executor.execute(eq(user), eq(KEY), anyString(), any())).thenThrow(rejection);
        TransferResult recorded = new TransferResult(422, "{\"code\":\"INSUFFICIENT_FUNDS\"}", false, UUID.randomUUID());
        when(rejections.record(eq(user), eq(KEY), anyString(), any(), eq(rejection))).thenReturn(recorded);

        assertThat(service.submit(user, KEY, request("100"))).isSameAs(recorded);
        assertThat(meters.counter("transfer_failure_total", "code", "INSUFFICIENT_FUNDS").count()).isEqualTo(1);
    }

    @Test
    void validationErrorsNeverReachThePersistenceLayer() {
        assertThatThrownBy(() -> service.submit(user, KEY, request("0")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.INVALID_TRANSFER_AMOUNT));
        assertThatThrownBy(() -> service.submit(user, null, request("10")))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REQUIRED));
        verifyNoInteractions(idempotency, executor, rejections);
        assertThat(meters.counter("transfer_failure_total", "code", "INVALID_TRANSFER_AMOUNT").count()).isEqualTo(1);
    }

    @Test
    void technicalFailurePropagatesAndIsNotRecordedAsRejection() {
        when(idempotency.findReplay(any(), any(), any())).thenReturn(Optional.empty());
        when(executor.execute(any(), any(), any(), any())).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.submit(user, KEY, request("100"))).isInstanceOf(IllegalStateException.class);
        verify(rejections, never()).record(any(), any(), any(), any(), any());
        assertThat(meters.counter("transfer_failure_total", "code", "INTERNAL_ERROR").count()).isEqualTo(1);
    }

    private static CreateTransferRequest request(String amount) {
        return new CreateTransferRequest("1000000001", "1000000002", new BigDecimal(amount), "VND", null);
    }
}
