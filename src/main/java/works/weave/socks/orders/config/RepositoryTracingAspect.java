package works.weave.socks.orders.config;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Aspect for adding tracing to repository method calls
 * This intercepts calls to CustomerOrderRepository methods and wraps them with Observation spans
 */
@Aspect
@Component
public class RepositoryTracingAspect {
    private static final Logger LOG = LoggerFactory.getLogger(RepositoryTracingAspect.class);

    @Autowired
    private ObservationRegistry observationRegistry;

    /**
     * Intercept findByCustomerId calls and add tracing
     */
    @Around("execution(* works.weave.socks.orders.repositories.CustomerOrderRepository.findByCustomerId(..))")
    public Object traceFindByCustomerId(ProceedingJoinPoint joinPoint) throws Throwable {
        String customerId = (String) joinPoint.getArgs()[0];

        return Observation.createNotStarted("db.order.findByCustomerId", observationRegistry)
                .lowCardinalityKeyValue("db.system", "mongodb")
                .lowCardinalityKeyValue("db.operation", "find")
                .lowCardinalityKeyValue("db.collection", "customerOrders")
                .lowCardinalityKeyValue("db.mongodb.query", "{customerId: ?}")
                .highCardinalityKeyValue("customer.id", customerId)
                .observe(() -> {
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
                .lowCardinalityKeyValue("db.system", "mongodb")
                .lowCardinalityKeyValue("db.operation", "findOne")
                .lowCardinalityKeyValue("db.collection", "customerOrders")
                .highCardinalityKeyValue("order.id", orderId)
                .observe(() -> {
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
        Object order = joinPoint.getArgs()[0];

        return Observation.createNotStarted("db.order.save", observationRegistry)
                .lowCardinalityKeyValue("db.system", "mongodb")
                .lowCardinalityKeyValue("db.operation", "insert")
                .lowCardinalityKeyValue("db.collection", "customerOrders")
                .observe(() -> {
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
                .lowCardinalityKeyValue("db.system", "mongodb")
                .lowCardinalityKeyValue("db.operation", "find")
                .lowCardinalityKeyValue("db.collection", "customerOrders")
                .observe(() -> {
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
                .lowCardinalityKeyValue("db.system", "mongodb")
                .lowCardinalityKeyValue("db.operation", "delete")
                .lowCardinalityKeyValue("db.collection", "customerOrders")
                .highCardinalityKeyValue("order.id", orderId)
                .observe(() -> {
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
}
