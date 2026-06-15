package com.phungquocthai.symphony.configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                build("songDetail",    30, TimeUnit.MINUTES, 2_000),
                build("songs",         15, TimeUnit.MINUTES,   300),
                build("topSongs",       1, TimeUnit.MINUTES,    150),
                build("newSongs",      10, TimeUnit.MINUTES,    50),
                build("songSearch",     5, TimeUnit.MINUTES,   500),
                build("allCategories", 60, TimeUnit.MINUTES,     1)
        ));
        return manager;
    }

    private CaffeineCache build(String name, long ttl, TimeUnit unit, long maxSize) {
        return new CaffeineCache(name,
                Caffeine.newBuilder()
                        .maximumSize(maxSize)
                        .expireAfterWrite(ttl, unit)
                        .recordStats()
                        .build()
        );
    }
}