package com.example.reviewer.config;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setTaskDecorator(otelTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Bean
    public TaskDecorator otelTaskDecorator() {
        return runnable -> {
            Context parent = Context.current();
            return () -> {
                try (Scope ignored = parent.makeCurrent()) {
                    runnable.run();
                }
            };
        };
    }
}



