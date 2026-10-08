package com.securebank.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code securebank.demo.seed=true} (env SECUREBANK_DEMO_SEED) inserts the api.md §7 demo users. Local only. */
@ConfigurationProperties(prefix = "securebank.demo")
public record DemoSeedProperties(boolean seed) {
}
