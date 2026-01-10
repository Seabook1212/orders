package works.weave.socks.orders.config;

import io.micrometer.context.ContextSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for async method execution with trace context propagation
 */
@Configuration
@EnableAsync
public class AsyncConfiguration implements AsyncConfigurer {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncConfiguration.class);

    /**
     * Configure async executor with trace context propagation
     * This ensures that trace IDs are passed to async methods
     */
    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        LOG.info("Configuring async executor with trace context propagation");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");

        // Add task decorator to propagate trace context
        executor.setTaskDecorator(new TraceContextTaskDecorator());

        executor.initialize();

        LOG.info("Async executor configured: corePoolSize=10, maxPoolSize=50, queueCapacity=100");

        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) -> {
            LOG.error("Async method execution failed: method={}, params={}",
                    method.getName(), params, throwable);
        };
    }

    /**
     * Task decorator that propagates trace context to async threads
     */
    private static class TraceContextTaskDecorator implements TaskDecorator {
        private static final Logger LOG = LoggerFactory.getLogger(TraceContextTaskDecorator.class);

        @Override
        public Runnable decorate(Runnable runnable) {
            // Capture the current trace context from the parent thread
            // Using the deprecated method but it's the most reliable for Spring Boot 3.4.x
            @SuppressWarnings("deprecation")
            ContextSnapshot snapshot = ContextSnapshot.captureAll();

            LOG.trace("Capturing trace context for async task");

            // Return a wrapped runnable that restores the context in the async thread
            return () -> {
                try (ContextSnapshot.Scope ignored = snapshot.setThreadLocals()) {
                    LOG.trace("Restored trace context in async thread");
                    runnable.run();
                } catch (Exception e) {
                    LOG.error("Error in async task execution", e);
                    throw e;
                }
            };
        }
    }
}
