package com.tollplaza.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Turns on annotation-driven caching. The Caffeine cache manager itself is
 * configured through {@code spring.cache.*} in application.yml.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String TOLL_PLAZA_RESULTS = "tollPlazaResults";
    public static final String GEOCODES = "geocodes";
}
