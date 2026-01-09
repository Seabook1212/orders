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

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class RestProxyTemplate {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Value("${proxy.host:}")
    private String host;

    @Value("${proxy.port:}")
    private String port;

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @Bean
    public RestTemplate restTemplate() {
        // Use RestTemplateBuilder which automatically configures tracing interceptors
        RestTemplate restTemplate = restTemplateBuilder.build();

        logger.info("Configuring RestTemplate with tracing support");

        // Add custom interceptor to log trace headers
        restTemplate.getInterceptors().add(new TracingLoggingInterceptor());

        if (!host.isEmpty() && !port.isEmpty()) {
            int portNr = -1;
            try {
                portNr = Integer.parseInt(port);
            } catch (NumberFormatException e) {
                logger.error("Unable to parse the proxy port number");
                return restTemplate;
            }

            logger.info("Configuring HTTP proxy: {}:{}", host, portNr);
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            InetSocketAddress address = new InetSocketAddress(host, portNr);
            Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
            factory.setProxy(proxy);
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
