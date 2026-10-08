package com.securebank.bankingcore.config;

import com.securebank.bankingcore.application.transfer.TransferFaultInjector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties({BankingProperties.class, DemoSeedProperties.class})
public class CoreConfig {

    /** Injected everywhere "now" matters, so tests can pin time. */
    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Production no-op; integration tests replace it to force a failure after the debit. */
    @Bean
    @ConditionalOnMissingBean
    public TransferFaultInjector transferFaultInjector() {
        return TransferFaultInjector.NONE;
    }
}
