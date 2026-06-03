package ua.dmytrolutsiuk.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import ua.dmytrolutsiuk.backend.config.properties.GenerationProperties;

/**
 * Async infrastructure for artifact generation. {@code artifactGenerationExecutor} runs one
 * background orchestrator task per project; {@code artifactCallExecutor} is the bounded pool the
 * orchestrator fans per-artifact LLM calls onto (its size is the concurrency cap).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("artifactGenerationExecutor")
    public ThreadPoolTaskExecutor artifactGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("artifact-gen-");
        executor.initialize();
        return executor;
    }

    @Bean("artifactCallExecutor")
    public ThreadPoolTaskExecutor artifactCallExecutor(GenerationProperties properties) {
        int concurrency = Math.max(1, properties.concurrency());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("artifact-call-");
        executor.initialize();
        return executor;
    }
}
