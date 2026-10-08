package com.securebank.bankingcore.config;

import com.securebank.bankingcore.application.onboarding.DemoSeedService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Runs the demo seed at startup only when {@code securebank.demo.seed=true} (env SECUREBANK_DEMO_SEED). */
@Component
@ConditionalOnProperty(name = "securebank.demo.seed", havingValue = "true")
public class DemoSeedRunner implements ApplicationRunner {

    private final DemoSeedService seed;

    public DemoSeedRunner(DemoSeedService seed) {
        this.seed = seed;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed.seed();
    }
}
