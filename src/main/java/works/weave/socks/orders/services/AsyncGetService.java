package works.weave.socks.orders.services;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.MediaTypes;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import works.weave.socks.orders.config.RestProxyTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.springframework.hateoas.MediaTypes.HAL_JSON;

@Service
public class AsyncGetService {
    private final Logger LOG = LoggerFactory.getLogger(getClass());

    private final RestProxyTemplate restProxyTemplate;

    private final RestTemplate halTemplate;

    @Autowired
    public AsyncGetService(RestProxyTemplate restProxyTemplate) {
        this.restProxyTemplate = restProxyTemplate;
        this.halTemplate = new RestTemplate(restProxyTemplate.getRestTemplate().getRequestFactory());

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        MappingJackson2HttpMessageConverter halConverter = new MappingJackson2HttpMessageConverter();
        halConverter.setSupportedMediaTypes(Arrays.asList(MediaTypes.HAL_JSON));
        halConverter.setObjectMapper(objectMapper);
        halTemplate.setMessageConverters(Collections.singletonList(halConverter));
    }

    @Async
    public <T> Future<T> getResource(URI url, ParameterizedTypeReference<T> type) throws
            InterruptedException {
        LOG.info("[AsyncGetService] GET request starting - URL: {}", url);
        long startTime = System.currentTimeMillis();

        try {
            RequestEntity<Void> request = RequestEntity.get(url).accept(HAL_JSON).build();
            LOG.debug("[AsyncGetService] Request details: {}", request);
            LOG.debug("[AsyncGetService] Request headers: {}", request.getHeaders());

            T body = restProxyTemplate.getRestTemplate().exchange(request, type).getBody();

            long duration = System.currentTimeMillis() - startTime;
            LOG.info("[AsyncGetService] GET request completed - URL: {}, Duration: {}ms, Response received: {}",
                    url, duration, body != null ? "success" : "null");
            LOG.debug("[AsyncGetService] Response body: {}", body);

            return CompletableFuture.completedFuture(body);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            LOG.error("[AsyncGetService] GET request failed - URL: {}, Duration: {}ms, Error: {}",
                    url, duration, e.getMessage(), e);
            throw e;
        }
    }

    @Async
    public <T> Future<List<T>> getDataList(URI url, ParameterizedTypeReference<List<T>> type) throws
            InterruptedException {
        LOG.info("[AsyncGetService] GET DATA LIST request starting - URL: {}", url);
        long startTime = System.currentTimeMillis();

        try {
            RequestEntity<Void> request = RequestEntity.get(url).accept(MediaType.APPLICATION_JSON).build();
            LOG.debug("[AsyncGetService] Request details: {}", request);
            LOG.debug("[AsyncGetService] Request headers: {}", request.getHeaders());

            List<T> body = restProxyTemplate.getRestTemplate().exchange(request, type).getBody();

            long duration = System.currentTimeMillis() - startTime;
            LOG.info("[AsyncGetService] GET DATA LIST completed - URL: {}, Duration: {}ms, Items count: {}",
                    url, duration, body != null ? body.size() : 0);
            LOG.debug("[AsyncGetService] Response body: {}", body);

            return CompletableFuture.completedFuture(body);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            LOG.error("[AsyncGetService] GET DATA LIST failed - URL: {}, Duration: {}ms, Error: {}",
                    url, duration, e.getMessage(), e);
            throw e;
        }
    }

    @Async
    public <T, B> Future<T> postResource(URI uri, B body, ParameterizedTypeReference<T> returnType) {
        LOG.info("[AsyncGetService] POST request starting - URI: {}", uri);
        LOG.debug("[AsyncGetService] POST request body: {}", body);
        long startTime = System.currentTimeMillis();

        try {
            RequestEntity<B> request = RequestEntity.post(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body);
            LOG.debug("[AsyncGetService] Request details: {}", request);
            LOG.debug("[AsyncGetService] Request headers: {}", request.getHeaders());

            T responseBody = restProxyTemplate.getRestTemplate().exchange(request, returnType).getBody();

            long duration = System.currentTimeMillis() - startTime;
            LOG.info("[AsyncGetService] POST request completed - URI: {}, Duration: {}ms, Response received: {}",
                    uri, duration, responseBody != null ? "success" : "null");
            LOG.debug("[AsyncGetService] Response body: {}", responseBody);

            return CompletableFuture.completedFuture(responseBody);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            LOG.error("[AsyncGetService] POST request failed - URI: {}, Duration: {}ms, Error: {}",
                    uri, duration, e.getMessage(), e);
            throw e;
        }
    }
}
