package works.weave.socks.orders.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class WebMvcConfig {
    // HTTP metrics are now automatically provided by Spring Boot 3 Actuator
    // with Micrometer. No custom interceptor needed.
    // Metrics are available at /actuator/prometheus
}
