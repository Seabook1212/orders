package works.weave.socks.orders.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.List;

/**
 * Interceptor to log trace propagation headers in outgoing HTTP requests
 */
public class TracingLoggingInterceptor implements ClientHttpRequestInterceptor {
    private static final Logger LOG = LoggerFactory.getLogger(TracingLoggingInterceptor.class);

    // B3 propagation headers
    private static final String[] TRACE_HEADERS = {
            "X-B3-TraceId",
            "X-B3-SpanId",
            "X-B3-ParentSpanId",
            "X-B3-Sampled",
            "X-B3-Flags",
            "b3",  // Single header format
            "traceparent",  // W3C format
            "tracestate"
    };

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {

        // Log trace headers being propagated
        LOG.info("[TracingInterceptor] Outgoing request to: {} {}", request.getMethod(), request.getURI());

        boolean hasTraceHeaders = false;
        for (String headerName : TRACE_HEADERS) {
            List<String> headerValues = request.getHeaders().get(headerName);
            if (headerValues != null && !headerValues.isEmpty()) {
                LOG.info("[TracingInterceptor] Trace header: {} = {}", headerName, headerValues.get(0));
                hasTraceHeaders = true;
            }
        }

        if (!hasTraceHeaders) {
            LOG.warn("[TracingInterceptor] No trace headers found in outgoing request to: {}", request.getURI());
            LOG.warn("[TracingInterceptor] All headers: {}", request.getHeaders().keySet());
        }

        // Continue with the request
        return execution.execute(request, body);
    }
}
