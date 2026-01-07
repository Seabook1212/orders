package works.weave.socks.orders.middleware;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class HTTPMonitoringInterceptor implements HandlerInterceptor {
    private static final String START_TIME_KEY = "startTime";

    @Autowired
    private MeterRegistry meterRegistry;

    @Value("${spring.application.name:orders}")
    private String serviceName;

    @Override
    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse
            httpServletResponse, Object o) throws Exception {
        httpServletRequest.setAttribute(START_TIME_KEY, System.nanoTime());
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest httpServletRequest, HttpServletResponse
            httpServletResponse, Object o, ModelAndView modelAndView) throws Exception {
        long start = (long) httpServletRequest.getAttribute(START_TIME_KEY);
        long elapsed = System.nanoTime() - start;
        String path = httpServletRequest.getRequestURI();

        Timer.builder("http.server.requests")
                .tag("service", serviceName)
                .tag("method", httpServletRequest.getMethod())
                .tag("uri", path)
                .tag("status", Integer.toString(httpServletResponse.getStatus()))
                .register(meterRegistry)
                .record(java.time.Duration.ofNanos(elapsed));
    }

    @Override
    public void afterCompletion(HttpServletRequest httpServletRequest, HttpServletResponse
            httpServletResponse, Object o, Exception e) throws Exception {
    }
}
