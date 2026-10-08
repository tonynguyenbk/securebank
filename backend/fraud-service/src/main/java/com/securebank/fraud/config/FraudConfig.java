package com.securebank.fraud.config;

import com.securebank.fraud.domain.FraudRuleEngine;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FraudProperties.class)
public class FraudConfig {

    @Bean
    public FraudRuleEngine fraudRuleEngine(FraudProperties properties) {
        return new FraudRuleEngine(properties.thresholds());
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
