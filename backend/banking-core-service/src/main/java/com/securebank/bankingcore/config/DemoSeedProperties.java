package com.securebank.bankingcore.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code securebank.demo.seed=true} (env SECUREBANK_DEMO_SEED) seeds the demo customers. Local demo only. */
@ConfigurationProperties(prefix = "securebank.demo")
public record DemoSeedProperties(boolean seed) {
}
