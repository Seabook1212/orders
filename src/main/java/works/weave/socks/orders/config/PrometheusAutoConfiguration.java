package works.weave.socks.orders.config;

import org.springframework.context.annotation.Configuration;

// Prometheus metrics are now automatically configured by Spring Boot Actuator
// with micrometer-registry-prometheus dependency
// Metrics are available at /actuator/prometheus by default
@Configuration
class PrometheusAutoConfiguration {
    // No custom configuration needed - Spring Boot 3 handles this automatically
}
