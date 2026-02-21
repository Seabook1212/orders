package works.weave.socks.orders.config;

import brave.Span;
import brave.Tracer;
import brave.propagation.TraceContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Diagnostic filter to log what trace context Micrometer/Brave actually extracted
 * Runs AFTER Spring's tracing filter (low precedence = runs later)
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class TraceContextDiagnosticFilter implements Filter {
    private static final Logger LOG = LoggerFactory.getLogger(TraceContextDiagnosticFilter.class);

    @Autowired(required = false)
    private Tracer tracer;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (request instanceof HttpServletRequest httpRequest) {
            String uri = httpRequest.getRequestURI();

            // Skip logging for health/metrics endpoints
            if (!isMonitoringEndpoint(uri)) {
                logTraceContext(httpRequest);
            }
        }

        chain.doFilter(request, response);
    }

    private void logTraceContext(HttpServletRequest request) {
        LOG.info("[TraceDiagnostic] ====== Extracted Trace Context ======");

        if (tracer == null) {
            LOG.warn("[TraceDiagnostic] Tracer is NULL - tracing not configured!");
            return;
        }

        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) {
            LOG.warn("[TraceDiagnostic] Current span is NULL - no trace context extracted!");
            return;
        }

        TraceContext context = currentSpan.context();
        if (context == null) {
            LOG.warn("[TraceDiagnostic] TraceContext is NULL!");
            return;
        }

        // Log extracted trace context details
        String traceId = context.traceIdString();
        String spanId = context.spanIdString();
        String parentSpanId = context.parentIdString();

        LOG.info("[TraceDiagnostic] Extracted traceId: {}", traceId);
        LOG.info("[TraceDiagnostic] Extracted spanId (current): {}", spanId);
        LOG.info("[TraceDiagnostic] Extracted parentSpanId: {}", parentSpanId);

        // Log what we received vs what was extracted
        String receivedSpanId = request.getHeader("X-B3-SpanId");
        LOG.info("[TraceDiagnostic] Received X-B3-SpanId: {}", receivedSpanId);
        LOG.info("[TraceDiagnostic] Expected parentSpanId should be: {}", receivedSpanId);

        if (receivedSpanId != null && !receivedSpanId.equals(parentSpanId)) {
            LOG.error("[TraceDiagnostic] MISMATCH! parentSpanId ({}) != received X-B3-SpanId ({})",
                    parentSpanId, receivedSpanId);
        } else if (receivedSpanId != null && receivedSpanId.equals(parentSpanId)) {
            LOG.info("[TraceDiagnostic] CORRECT! parentSpanId matches received X-B3-SpanId");
        }

        LOG.info("[TraceDiagnostic] =====================================");
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
