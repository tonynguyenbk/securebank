package com.securebank.identity.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties({IdentityProperties.class, DemoSeedProperties.class})
public class IdentityConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
