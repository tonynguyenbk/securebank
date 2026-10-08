package com.securebank.bankingcore.infrastructure.kafka;

import com.securebank.bankingcore.application.onboarding.CustomerOnboardingService;
import com.securebank.common.events.Topics;
import com.securebank.common.events.UserRegisteredEvent;
import com.securebank.common.json.EventJson;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Kafka adapter: JSON payload → {@link CustomerOnboardingService}. At-least-once; the service deduplicates. */
@Component
public class UserRegisteredListener {

    private final EventJson json;
    private final CustomerOnboardingService onboarding;

    public UserRegisteredListener(EventJson json, CustomerOnboardingService onboarding) {
        this.json = json;
        this.onboarding = onboarding;
    }

    @KafkaListener(topics = Topics.USER_REGISTERED, groupId = "${spring.application.name}")
    public void on(String payload) {
        onboarding.onUserRegistered(json.read(payload, UserRegisteredEvent.class));
    }
}
