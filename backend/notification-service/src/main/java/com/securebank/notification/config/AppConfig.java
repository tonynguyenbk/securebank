package com.securebank.notification.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * JSONB {@code params} hold money values: read decimals back as BigDecimal (not double) so large VND amounts
     * keep their exact value.
     */
    @Bean
    public HibernatePropertiesCustomizer jsonFormatMapper(ObjectMapper objectMapper) {
        ObjectMapper mapper = objectMapper.copy().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        return properties -> properties.put(AvailableSettings.JSON_FORMAT_MAPPER, new JacksonJsonFormatMapper(mapper));
    }
}
