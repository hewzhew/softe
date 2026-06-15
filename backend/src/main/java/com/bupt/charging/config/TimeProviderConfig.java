package com.bupt.charging.config;

import com.bupt.charging.support.TimeProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeProviderConfig {
    @Bean
    public TimeProvider timeProvider() {
        return TimeProvider.system();
    }
}
