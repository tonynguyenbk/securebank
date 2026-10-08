package com.securebank.bankingcore.application.transfer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/** Micrometer meters for the transfer API (spec §33). */
@Component
public class TransferMetrics {

    private final MeterRegistry registry;
    private final Counter success;
    private final Counter replayed;
    private final Timer latency;

    public TransferMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.success = Counter.builder("transfer_success_total")
                .description("Transfers that moved money").register(registry);
        this.replayed = Counter.builder("transfer_idempotent_replay_total")
                .description("Requests answered from a stored idempotent response").register(registry);
        this.latency = Timer.builder("transfer_latency")
                .description("End-to-end latency of POST /transfers")
                .publishPercentileHistogram()
                .register(registry);
    }

    public Timer.Sample start() {
        return Timer.start(registry);
    }

    public void stop(Timer.Sample sample) {
        sample.stop(latency);
    }

    public void success() {
        success.increment();
    }

    public void replayed() {
        replayed.increment();
    }

    public void failure(String code) {
        Counter.builder("transfer_failure_total")
                .description("Transfers refused or failed, by error code")
                .tag("code", code)
                .register(registry)
                .increment();
    }
}
