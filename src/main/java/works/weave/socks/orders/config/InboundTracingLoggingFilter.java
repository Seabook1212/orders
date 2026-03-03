package works.weave.socks.orders.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Filter to log inbound B3 trace headers for debugging trace propagation
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InboundTracingLoggingFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(InboundTracingLoggingFilter.class);

    // B3 propagation headers to log
    private static final List<String> B3_HEADERS = Arrays.asList(
            "X-B3-TraceId",
            "X-B3-SpanId",
            "X-B3-ParentSpanId",
            "X-B3-Sampled",
            "X-B3-Flags",
            "b3",           // Single header format
            "traceparent",  // W3C format
            "tracestate"    // W3C format
    );

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest httpRequest) {
            String method = httpRequest.getMethod();
            String uri = httpRequest.getRequestURI();

            // Skip logging for health/metrics endpoints
            if (!isMonitoringEndpoint(uri)) {
                LOG.debug("[InboundTracing] ====== Incoming Request: {} {} ======", method, uri);

                boolean hasTraceHeaders = false;
                for (String headerName : B3_HEADERS) {
                    String headerValue = httpRequest.getHeader(headerName);
                    if (headerValue != null && !headerValue.isEmpty()) {
                        LOG.debug("[InboundTracing] Header: {} = {}", headerName, headerValue);
                        hasTraceHeaders = true;
                    }
                }

                if (!hasTraceHeaders) {
                    LOG.debug("[InboundTracing] No B3/W3C trace headers found in request to: {} {}", method, uri);
                }

                LOG.debug("[InboundTracing] ==========================================");
            }
        }

        chain.doFilter(request, response);
    }

    private boolean isMonitoringEndpoint(String uri) {
        return uri != null && (
                uri.equals("/health") ||
                uri.equals("/metrics") ||
                uri.equals("/prometheus") ||
                uri.startsWith("/actuator/")
        );
    }
}
