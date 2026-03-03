package works.weave.socks.orders.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.net.InetSocketAddress;
import java.net.Proxy;

@Configuration
public class RestProxyTemplate {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Value("${proxy.host:}")
    private String host;

    @Value("${proxy.port:}")
    private String port;

    @Value("${http.timeout:5}")
    private long timeoutSeconds;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @Bean
    public RestTemplate restTemplate() {
        // Use RestTemplateBuilder which automatically configures tracing interceptors
        RestTemplate restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(timeoutSeconds))
                .setReadTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();

        logger.info("Configuring RestTemplate with connect/read timeout={}s", timeoutSeconds);

        // Add custom interceptor to log trace headers
        restTemplate.getInterceptors().add(new TracingLoggingInterceptor());

        if (!host.isEmpty() && !port.isEmpty()) {
            int portNr = -1;
            try {
                portNr = Integer.parseInt(port);
            } catch (NumberFormatException e) {
                logger.error("Unable to parse HTTP proxy port value={}", port, e);
                return restTemplate;
            }

            logger.info("Configuring HTTP proxy: {}:{}", host, portNr);
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            InetSocketAddress address = new InetSocketAddress(host, portNr);
            Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
            factory.setProxy(proxy);
            factory.setConnectTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
            factory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
            restTemplate.setRequestFactory(factory);
        }

        logger.info("RestTemplate configured with {} interceptors (including tracing)",
                restTemplate.getInterceptors().size());

        return restTemplate;
    }

    public RestTemplate getRestTemplate() {
        return restTemplate();
    }
}
