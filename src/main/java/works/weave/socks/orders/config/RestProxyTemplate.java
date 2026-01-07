package works.weave.socks.orders.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;

@Configuration
public class RestProxyTemplate {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Value("${proxy.host:}")
    private String host;

    @Value("${proxy.port:}")
    private String port;

    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();

        if (!host.isEmpty() && !port.isEmpty()) {
            int portNr = -1;
            try {
                portNr = Integer.parseInt(port);
            } catch (NumberFormatException e) {
                logger.error("Unable to parse the proxy port number");
                return restTemplate;
            }

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            InetSocketAddress address = new InetSocketAddress(host, portNr);
            Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
            factory.setProxy(proxy);
            restTemplate.setRequestFactory(factory);
        }

        return restTemplate;
    }

    public RestTemplate getRestTemplate() {
        return restTemplate();
    }
}
