package com.oj.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ContestTimeConfig {
    @Bean
    public Clock contestClock() {
        return Clock.systemUTC();
    }
}
