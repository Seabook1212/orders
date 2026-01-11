package works.weave.socks.orders.services;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import works.weave.socks.orders.entities.CustomerOrder;
import works.weave.socks.orders.repositories.CustomerOrderRepository;

/**
 * Service layer for CustomerOrder operations
 * Tracing is automatically handled by RepositoryTracingAspect
 */
@Service
public class OrderService {
    private static final Logger LOG = LoggerFactory.getLogger(OrderService.class);

    @Autowired
    private CustomerOrderRepository repository;

    /**
     * Save order to MongoDB
     */
    public CustomerOrder saveOrder(CustomerOrder order) {
        LOG.info("[OrderService] Saving order to MongoDB for customer: {}",
                order.getCustomer() != null ? order.getCustomer().getId() : "unknown");

        CustomerOrder saved = repository.save(order);

        LOG.info("[OrderService] Order saved successfully with ID: {}", saved.getId());
        return saved;
    }

    /**
     * Find order by ID
     */
    public Optional<CustomerOrder> findById(String orderId) {
        LOG.info("[OrderService] Finding order by ID: {}", orderId);

        Optional<CustomerOrder> result = repository.findById(orderId);

        LOG.info("[OrderService] Order findById completed: found={}", result.isPresent());
        return result;
    }

    /**
     * Find orders by customer ID
     */
    public List<CustomerOrder> findByCustomerId(String customerId) {
        LOG.info("[OrderService] Finding orders for customer: {}", customerId);

        List<CustomerOrder> orders = repository.findByCustomerId(customerId);

        LOG.info("[OrderService] Found {} orders for customer: {}", orders.size(), customerId);
        return orders;
    }

    /**
     * Find all orders
     */
    public List<CustomerOrder> findAllOrders() {
        LOG.info("[OrderService] Finding all orders");

        List<CustomerOrder> orders = repository.findAll();

        LOG.info("[OrderService] Found {} total orders", orders.size());
        return orders;
    }

    /**
     * Delete order by ID
     */
    public void deleteOrder(String orderId) {
        LOG.info("[OrderService] Deleting order: {}", orderId);

        repository.deleteById(orderId);

        LOG.info("[OrderService] Order deleted: {}", orderId);
    }

    /**
     * Count all orders
     */
    public long countOrders() {
        LOG.info("[OrderService] Counting all orders");

        long count = repository.count();

        LOG.info("[OrderService] Total orders count: {}", count);
        return count;
    }
}
