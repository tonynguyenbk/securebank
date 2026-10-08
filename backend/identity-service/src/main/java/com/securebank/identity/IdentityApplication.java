package com.securebank.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/** Credentials are verified by our own services; Boot's in-memory default user is not created. */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class IdentityApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityApplication.class, args);
    }
}
