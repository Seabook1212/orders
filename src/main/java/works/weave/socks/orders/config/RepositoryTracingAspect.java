package works.weave.socks.orders.config;

import brave.Span;
import brave.Tracer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.PostConstruct;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Aspect for adding tracing to repository method calls
 * This intercepts calls to CustomerOrderRepository methods and wraps them with Observation spans
 * Includes peer.service and semantic attributes for Jaeger dependency tracking
 */
@Aspect
@Component
public class RepositoryTracingAspect {
    private static final Logger LOG = LoggerFactory.getLogger(RepositoryTracingAspect.class);

    @Autowired
    private ObservationRegistry observationRegistry;

    @Autowired(required = false)
    private Tracer tracer;

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    // Extracted MongoDB connection info for peer.service tags
    private String peerService = "orders-db";
    private String netPeerName = "orders-db";
    private String netPeerPort = "27017";
    private String dbName = "data";

    @PostConstruct
    public void init() {
        try {
            // Parse MongoDB URI to extract host, port, and database
            // Format: mongodb://host:port/database?options
            String uriWithoutPrefix = mongoUri.replace("mongodb://", "");
            String[] parts = uriWithoutPrefix.split("/");
            if (parts.length > 0) {
                String hostPort = parts[0].split("\\?")[0];
                String[] hostParts = hostPort.split(":");
                netPeerName = hostParts[0];
                peerService = hostParts[0].split("\\.")[0]; // Extract service name from FQDN
                if (hostParts.length > 1) {
                    netPeerPort = hostParts[1];
                }
            }
            if (parts.length > 1) {
                dbName = parts[1].split("\\?")[0];
            }
            LOG.info("MongoDB tracing configured: peer.service={}, net.peer.name={}, net.peer.port={}, db.name={}",
                    peerService, netPeerName, netPeerPort, dbName);
        } catch (Exception e) {
            LOG.warn("Failed to parse MongoDB URI, using defaults: {}", e.getMessage());
        }
    }

    /**
     * Intercept findByCustomerId calls and add tracing
     */
    @Around("execution(* works.weave.socks.orders.repositories.CustomerOrderRepository.findByCustomerId(..))")
    public Object traceFindByCustomerId(ProceedingJoinPoint joinPoint) throws Throwable {
        String customerId = (String) joinPoint.getArgs()[0];

        return Observation.createNotStarted("db.order.findByCustomerId", observationRegistry)
                .lowCardinalityKeyValue("span.kind", "client")
                // Peer service for Jaeger dependency tracking
                .lowCardinalityKeyValue("peer.service", peerService)
                // Database semantic attributes
                .lowCardinalityKeyValue("db.system", "mongodb")
                .lowCardinalityKeyValue("db.name", dbName)
                .lowCardinalityKeyValue("db.operation", "find")
                .lowCardinalityKeyValue("db.collection", "customerOrders")
                .lowCardinalityKeyValue("db.mongodb.query", "{customerId: ?}")
                // Network semantic attributes
                .lowCardinalityKeyValue("net.peer.name", netPeerName)
                .lowCardinalityKeyValue("net.peer.port", netPeerPort)
                // High cardinality
                .highCardinalityKeyValue("customer.id", customerId)
                .observe(() -> {
                    markCurrentSpanAsClient();
                    LOG.info("[RepositoryTracingAspect] Finding orders for customer: {}", customerId);
                    long startTime = System.currentTimeMillis();

                    try {
                        @SuppressWarnings("unchecked")
                        List<Object> orders = (List<Object>) joinPoint.proceed();

                        long duration = System.currentTimeMillis() - startTime;
                        LOG.info("[RepositoryTracingAspect] Found {} orders for customer: {}, duration: {}ms",
                                orders.size(), customerId, duration);

                        return orders;
                    } catch (Throwable e) {
                        long duration = System.currentTimeMillis() - startTime;
                        LOG.error("[RepositoryTracingAspect] Error finding orders for customer: {}, duration: {}ms",
                                customerId, duration, e);
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Intercept findById calls and add tracing
     */
    @Around("execution(* works.weave.socks.orders.repositories.CustomerOrderRepository.findById(..))")
    public Object traceFindById(ProceedingJoinPoint joinPoint) throws Throwable {
        String orderId = (String) joinPoint.getArgs()[0];

        return Observation.createNotStarted("db.order.findById", observationRegistry)
                .lowCardinalityKeyValue("span.kind", "client")
                // Peer service for Jaeger dependency tracking
                .lowCardinalityKeyValue("peer.service", peerService)
                // Database semantic attributes
                .lowCardinalityKeyValue("db.system", "mongodb")
                .lowCardinalityKeyValue("db.name", dbName)
                .lowCardinalityKeyValue("db.operation", "findOne")
                .lowCardinalityKeyValue("db.collection", "customerOrders")
                // Network semantic attributes
                .lowCardinalityKeyValue("net.peer.name", netPeerName)
                .lowCardinalityKeyValue("net.peer.port", netPeerPort)
                // High cardinality
                .highCardinalityKeyValue("order.id", orderId)
                .observe(() -> {
                    markCurrentSpanAsClient();
                    LOG.info("[RepositoryTracingAspect] Finding order by ID: {}", orderId);
                    long startTime = System.currentTimeMillis();

                    try {
                        Object result = joinPoint.proceed();

                        long duration = System.currentTimeMillis() - startTime;
                        LOG.info("[RepositoryTracingAspect] Order findById completed: duration: {}ms", duration);

                        return result;
                    } catch (Throwable e) {
                        long duration = System.currentTimeMillis() - startTime;
                        LOG.error("[RepositoryTracingAspect] Error finding order by ID: {}, duration: {}ms",
                                orderId, duration, e);
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Intercept save calls and add tracing
     */
    @Around("execution(* works.weave.socks.orders.repositories.CustomerOrderRepository.save(..))")
    public Object traceSave(ProceedingJoinPoint joinPoint) throws Throwable {
        return Observation.createNotStarted("db.order.save", observationRegistry)
                .lowCardinalityKeyValue("span.kind", "client")
                // Peer service for Jaeger dependency tracking
                .lowCardinalityKeyValue("peer.service", peerService)
                // Database semantic attributes
                .lowCardinalityKeyValue("db.system", "mongodb")
                .lowCardinalityKeyValue("db.name", dbName)
                .lowCardinalityKeyValue("db.operation", "insert")
                .lowCardinalityKeyValue("db.collection", "customerOrders")
                // Network semantic attributes
                .lowCardinalityKeyValue("net.peer.name", netPeerName)
                .lowCardinalityKeyValue("net.peer.port", netPeerPort)
                .observe(() -> {
                    markCurrentSpanAsClient();
                    LOG.info("[RepositoryTracingAspect] Saving order to MongoDB");
                    long startTime = System.currentTimeMillis();

                    try {
                        Object saved = joinPoint.proceed();

                        long duration = System.currentTimeMillis() - startTime;
                        LOG.info("[RepositoryTracingAspect] Order saved successfully, duration: {}ms", duration);

                        return saved;
                    } catch (Throwable e) {
                        long duration = System.currentTimeMillis() - startTime;
                        LOG.error("[RepositoryTracingAspect] Error saving order, duration: {}ms", duration, e);
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Intercept findAll calls and add tracing
     */
    @Around("execution(* works.weave.socks.orders.repositories.CustomerOrderRepository.findAll())")
    public Object traceFindAll(ProceedingJoinPoint joinPoint) throws Throwable {
        return Observation.createNotStarted("db.order.findAll", observationRegistry)
                .lowCardinalityKeyValue("span.kind", "client")
                // Peer service for Jaeger dependency tracking
                .lowCardinalityKeyValue("peer.service", peerService)
                // Database semantic attributes
                .lowCardinalityKeyValue("db.system", "mongodb")
                .lowCardinalityKeyValue("db.name", dbName)
                .lowCardinalityKeyValue("db.operation", "find")
                .lowCardinalityKeyValue("db.collection", "customerOrders")
                // Network semantic attributes
                .lowCardinalityKeyValue("net.peer.name", netPeerName)
                .lowCardinalityKeyValue("net.peer.port", netPeerPort)
                .observe(() -> {
                    markCurrentSpanAsClient();
                    LOG.info("[RepositoryTracingAspect] Finding all orders");
                    long startTime = System.currentTimeMillis();

                    try {
                        @SuppressWarnings("unchecked")
                        List<Object> orders = (List<Object>) joinPoint.proceed();

                        long duration = System.currentTimeMillis() - startTime;
                        LOG.info("[RepositoryTracingAspect] Found {} total orders, duration: {}ms",
                                orders.size(), duration);

                        return orders;
                    } catch (Throwable e) {
                        long duration = System.currentTimeMillis() - startTime;
                        LOG.error("[RepositoryTracingAspect] Error finding all orders, duration: {}ms", duration, e);
                        throw new RuntimeException(e);
                    }
                });
    }

    /**
     * Intercept deleteById calls and add tracing
     */
    @Around("execution(* works.weave.socks.orders.repositories.CustomerOrderRepository.deleteById(..))")
    public Object traceDelete(ProceedingJoinPoint joinPoint) throws Throwable {
        String orderId = (String) joinPoint.getArgs()[0];

        return Observation.createNotStarted("db.order.delete", observationRegistry)
                .lowCardinalityKeyValue("span.kind", "client")
                // Peer service for Jaeger dependency tracking
                .lowCardinalityKeyValue("peer.service", peerService)
                // Database semantic attributes
                .lowCardinalityKeyValue("db.system", "mongodb")
                .lowCardinalityKeyValue("db.name", dbName)
                .lowCardinalityKeyValue("db.operation", "delete")
                .lowCardinalityKeyValue("db.collection", "customerOrders")
                // Network semantic attributes
                .lowCardinalityKeyValue("net.peer.name", netPeerName)
                .lowCardinalityKeyValue("net.peer.port", netPeerPort)
                // High cardinality
                .highCardinalityKeyValue("order.id", orderId)
                .observe(() -> {
                    markCurrentSpanAsClient();
                    LOG.info("[RepositoryTracingAspect] Deleting order: {}", orderId);
                    long startTime = System.currentTimeMillis();

                    try {
                        joinPoint.proceed();

                        long duration = System.currentTimeMillis() - startTime;
                        LOG.info("[RepositoryTracingAspect] Order deleted: {}, duration: {}ms", orderId, duration);

                        return null;
                    } catch (Throwable e) {
                        long duration = System.currentTimeMillis() - startTime;
                        LOG.error("[RepositoryTracingAspect] Error deleting order: {}, duration: {}ms",
                                orderId, duration, e);
                        throw new RuntimeException(e);
                    }
                });
    }

    private void markCurrentSpanAsClient() {
        if (tracer == null) {
            return;
        }

        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) {
            return;
        }

        currentSpan.kind(Span.Kind.CLIENT);
        currentSpan.tag("span.kind", "client");
    }
}
