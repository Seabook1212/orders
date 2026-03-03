package works.weave.socks.orders.services;

import brave.Tracer;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import works.weave.socks.orders.config.RestProxyTemplate;
import works.weave.socks.orders.support.FailureClassifier;
import works.weave.socks.orders.support.TraceExceptionTagger;

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

    @Autowired(required = false)
    private Tracer tracer;

    @Value("${http.timeout:5}")
    private long timeoutSeconds;

    @Value("${http.slow-call-threshold-ms:2000}")
    private long slowCallThresholdMs;

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
        String dependency = dependencyName(url);
        long startTime = System.currentTimeMillis();

        try {
            RequestEntity<Void> request = RequestEntity.get(url).accept(HAL_JSON).build();
            T body = restProxyTemplate.getRestTemplate().exchange(request, type).getBody();
            logSlowDependencyCallIfNeeded("GET", url, dependency, startTime);
            return CompletableFuture.completedFuture(body);
        } catch (RestClientException e) {
            logRemoteFailure("GET", url, dependency, startTime, e);
            throw e;
        }
    }

    @Async
    public <T> Future<List<T>> getDataList(URI url, ParameterizedTypeReference<List<T>> type) throws
            InterruptedException {
        String dependency = dependencyName(url);
        long startTime = System.currentTimeMillis();

        try {
            RequestEntity<Void> request = RequestEntity.get(url).accept(MediaType.APPLICATION_JSON).build();
            List<T> body = restProxyTemplate.getRestTemplate().exchange(request, type).getBody();
            logSlowDependencyCallIfNeeded("GET", url, dependency, startTime);
            return CompletableFuture.completedFuture(body);
        } catch (RestClientException e) {
            logRemoteFailure("GET", url, dependency, startTime, e);
            throw e;
        }
    }

    @Async
    public <T, B> Future<T> postResource(URI uri, B body, ParameterizedTypeReference<T> returnType) {
        String dependency = dependencyName(uri);
        long startTime = System.currentTimeMillis();

        try {
            RequestEntity<B> request = RequestEntity.post(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body);
            T responseBody = restProxyTemplate.getRestTemplate().exchange(request, returnType).getBody();
            logSlowDependencyCallIfNeeded("POST", uri, dependency, startTime);
            return CompletableFuture.completedFuture(responseBody);
        } catch (RestClientException e) {
            logRemoteFailure("POST", uri, dependency, startTime, e);
            throw e;
        }
    }

    private void logRemoteFailure(String method, URI uri, String dependency, long startTime, Exception exception) {
        long duration = System.currentTimeMillis() - startTime;
        Throwable rootCause = FailureClassifier.rootCause(exception);
        TraceExceptionTagger.tagCurrentSpan(tracer, exception);
        LOG.error(
                "dependency_call_failed dependency={} method={} uri={} latency_ms={} timeout_seconds={} error_classification={} cause_type={} cause_message={}",
                dependency,
                method,
                uri,
                duration,
                timeoutSeconds,
                FailureClassifier.classify(exception),
                rootCause.getClass().getSimpleName(),
                rootCause.getMessage(),
                exception
        );
    }

    private void logSlowDependencyCallIfNeeded(String method, URI uri, String dependency, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        if (duration >= slowCallThresholdMs) {
            LOG.warn(
                    "dependency_call_slow dependency={} method={} uri={} latency_ms={} slow_threshold_ms={}",
                    dependency,
                    method,
                    uri,
                    duration,
                    slowCallThresholdMs
            );
        } else if (LOG.isDebugEnabled()) {
            LOG.debug("dependency_call_completed dependency={} method={} uri={} latency_ms={}",
                    dependency, method, uri, duration);
        }
    }

    private String dependencyName(URI uri) {
        return uri.getHost() == null ? uri.toString() : uri.getHost();
    }
}
