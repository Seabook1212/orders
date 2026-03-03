package works.weave.socks.orders.config;

import brave.Tracer;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import works.weave.socks.orders.support.TraceExceptionTagger;

import java.io.IOException;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class TraceExceptionTaggingFilter implements Filter {
    @Autowired(required = false)
    private Tracer tracer;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException e) {
            TraceExceptionTagger.tagCurrentSpan(tracer, e);
            throw e;
        }
    }
}
