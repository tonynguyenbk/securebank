package com.securebank.fraud.infrastructure;

import com.securebank.common.events.Topics;
import com.securebank.common.events.TransactionCompletedEvent;
import com.securebank.common.json.EventJson;
import com.securebank.fraud.application.FraudDetectionService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka entry point. Parsing happens here; the transactional, idempotent work is in
 * {@link FraudDetectionService#process}.
 */
@Component
public class TransactionCompletedListener {

    private final EventJson eventJson;
    private final FraudDetectionService detection;

    public TransactionCompletedListener(EventJson eventJson, FraudDetectionService detection) {
        this.eventJson = eventJson;
        this.detection = detection;
    }

    @KafkaListener(topics = Topics.TRANSACTION_COMPLETED)
    public void onTransactionCompleted(String payload) {
        detection.process(parse(payload));
    }

    private TransactionCompletedEvent parse(String payload) {
        TransactionCompletedEvent event;
        try {
            event = eventJson.read(payload, TransactionCompletedEvent.class);
        } catch (IllegalArgumentException e) {
            throw new MalformedEventException("Unreadable TransactionCompletedEvent", e);
        }
        if (event == null || event.eventId() == null || event.transactionId() == null || event.customerId() == null
                || event.sourceAccountId() == null || event.amount() == null) {
            throw new MalformedEventException("TransactionCompletedEvent misses required fields", null);
        }
        return event;
    }
}
