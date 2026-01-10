package works.weave.socks.orders.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import io.micrometer.observation.ObservationPredicate;

/**
 * Configuration for distributed tracing with filtering
 */
@Configuration
public class TracingConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(TracingConfiguration.class);

    /**
     * Skip tracing for health checks and monitoring endpoints
     * This prevents Jaeger from being flooded with health check traces
     */
    @Bean
    public ObservationPredicate skipHealthCheckTracing() {
        return (name, context) -> {
            // Only filter HTTP server observations
            if (context instanceof ServerRequestObservationContext) {
                ServerRequestObservationContext serverContext = (ServerRequestObservationContext) context;
                String uri = serverContext.getCarrier().getRequestURI();

                // List of endpoints to exclude from tracing
                boolean shouldSkip = uri != null && (uri.equals("/health") ||
                        uri.equals("/metrics") ||
                        uri.equals("/prometheus") ||
                        uri.startsWith("/actuator/"));

                if (shouldSkip) {
                    LOG.trace("Skipping trace for endpoint: {}", uri);
                    return false; // Don't observe (skip tracing)
                }
            }

            return true; // Observe (create trace)
        };
    }
}
