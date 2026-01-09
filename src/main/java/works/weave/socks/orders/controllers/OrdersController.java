package works.weave.socks.orders.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.rest.webmvc.RepositoryRestController;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import works.weave.socks.orders.config.OrdersConfigurationProperties;
import works.weave.socks.orders.entities.*;
import works.weave.socks.orders.repositories.CustomerOrderRepository;
import works.weave.socks.orders.resources.NewOrderResource;
import works.weave.socks.orders.services.AsyncGetService;
import works.weave.socks.orders.values.PaymentRequest;
import works.weave.socks.orders.values.PaymentResponse;

import java.io.IOException;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@RepositoryRestController
public class OrdersController {
    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @Autowired
    private OrdersConfigurationProperties config;

    @Autowired
    private AsyncGetService asyncGetService;

    @Autowired
    private CustomerOrderRepository customerOrderRepository;

    @Value(value = "${http.timeout:5}")
    private long timeout;

    @ResponseStatus(HttpStatus.CREATED)
    @RequestMapping(path = "/orders", consumes = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public
    @ResponseBody
    CustomerOrder newOrder(@RequestBody NewOrderResource item) {
        LOG.info("=== NEW ORDER REQUEST RECEIVED ===");
        LOG.info("Request details - address: {}, customer: {}, card: {}, items: {}",
                item.address, item.customer, item.card, item.items);

        try {
            // Step 1: Validate request
            LOG.info("Step 1: Validating order request...");
            if (item.address == null || item.customer == null || item.card == null || item.items == null) {
                LOG.error("Validation failed - Missing required fields. address={}, customer={}, card={}, items={}",
                        item.address, item.customer, item.card, item.items);
                throw new InvalidOrderException("Invalid order request. Order requires customer, address, card and items.");
            }
            LOG.info("Step 1: Validation successful");

            // Step 2: Fetch resources from external services
            LOG.info("Step 2: Starting async calls to fetch address, customer, card, and items...");
            Future<EntityModel<Address>> addressFuture = asyncGetService.getResource(item.address, new
                    ParameterizedTypeReference<EntityModel<Address>>() {
            });
            LOG.debug("Address request initiated for: {}", item.address);

            Future<EntityModel<Customer>> customerFuture = asyncGetService.getResource(item.customer, new
                    ParameterizedTypeReference<EntityModel<Customer>>() {
            });
            LOG.debug("Customer request initiated for: {}", item.customer);

            Future<EntityModel<Card>> cardFuture = asyncGetService.getResource(item.card, new
                    ParameterizedTypeReference<EntityModel<Card>>() {
            });
            LOG.debug("Card request initiated for: {}", item.card);

            Future<List<Item>> itemsFuture = asyncGetService.getDataList(item.items, new
                    ParameterizedTypeReference<List<Item>>() {
            });
            LOG.debug("Items request initiated for: {}", item.items);
            LOG.info("Step 2: All async calls initiated");

            // Step 3: Wait for items and calculate total
            LOG.info("Step 3: Waiting for items response (timeout: {} seconds)...", timeout);
            List<Item> items = itemsFuture.get(timeout, TimeUnit.SECONDS);
            LOG.info("Step 3: Items received, count: {}", items != null ? items.size() : 0);

            float amount = calculateTotal(items);
            LOG.info("Step 3: Order total calculated: ${}", amount);

            // Step 4: Wait for address, card, customer responses
            LOG.info("Step 4: Waiting for address, card, and customer responses...");
            EntityModel<Address> addressModel = addressFuture.get(timeout, TimeUnit.SECONDS);
            LOG.info("Step 4: Address received: {}", addressModel != null ? addressModel.getContent() : "null");

            EntityModel<Card> cardModel = cardFuture.get(timeout, TimeUnit.SECONDS);
            LOG.info("Step 4: Card received: {}", cardModel != null ? cardModel.getContent() : "null");

            EntityModel<Customer> customerModel = customerFuture.get(timeout, TimeUnit.SECONDS);
            LOG.info("Step 4: Customer received: {}", customerModel != null ? customerModel.getContent() : "null");

            // Step 5: Call payment service
            LOG.info("Step 5: Preparing payment request...");
            PaymentRequest paymentRequest = new PaymentRequest(
                    addressModel.getContent(),
                    cardModel.getContent(),
                    customerModel.getContent(),
                    amount);
            LOG.info("Step 5: Sending payment request to: {}, amount: ${}", config.getPaymentUri(), amount);

            Future<PaymentResponse> paymentFuture = asyncGetService.postResource(
                    config.getPaymentUri(),
                    paymentRequest,
                    new ParameterizedTypeReference<PaymentResponse>() {
                    });

            PaymentResponse paymentResponse = paymentFuture.get(timeout, TimeUnit.SECONDS);
            LOG.info("Step 5: Payment response received - authorized: {}, message: {}",
                    paymentResponse != null ? paymentResponse.isAuthorised() : "null",
                    paymentResponse != null ? paymentResponse.getMessage() : "null");

            if (paymentResponse == null) {
                LOG.error("Step 5: Payment failed - Unable to parse authorization packet");
                throw new PaymentDeclinedException("Unable to parse authorisation packet");
            }
            if (!paymentResponse.isAuthorised()) {
                LOG.error("Step 5: Payment declined - {}", paymentResponse.getMessage());
                throw new PaymentDeclinedException(paymentResponse.getMessage());
            }

            // Step 6: Request shipping
            String customerId = customerModel.getContent().getId();
            LOG.info("Step 6: Requesting shipment for customer: {}, shipping URI: {}", customerId, config.getShippingUri());

            Future<Shipment> shipmentFuture = asyncGetService.postResource(config.getShippingUri(), new Shipment
                    (customerId), new ParameterizedTypeReference<Shipment>() {
            });

            Shipment shipment = shipmentFuture.get(timeout, TimeUnit.SECONDS);
            LOG.info("Step 6: Shipment response received: {}", shipment);

            // Step 7: Create order object
            LOG.info("Step 7: Creating order object...");
            CustomerOrder order = new CustomerOrder(
                    null,
                    customerId,
                    customerModel.getContent(),
                    addressModel.getContent(),
                    cardModel.getContent(),
                    items,
                    shipment,
                    Calendar.getInstance().getTime(),
                    amount);
            LOG.info("Step 7: Order object created: {}", order);

            // Step 8: Save to database
            LOG.info("Step 8: Saving order to MongoDB...");
            CustomerOrder savedOrder = customerOrderRepository.save(order);
            LOG.info("Step 8: Order saved successfully with ID: {}", savedOrder.getId());
            LOG.info("=== ORDER CREATION COMPLETED SUCCESSFULLY ===");

            return savedOrder;
        } catch (TimeoutException e) {
            LOG.error("ORDER CREATION FAILED - Timeout waiting for service response", e);
            LOG.error("Timeout details: timeout setting = {} seconds", timeout);
            throw new IllegalStateException("Unable to create order due to timeout from one of the services.", e);
        } catch (InterruptedException e) {
            LOG.error("ORDER CREATION FAILED - Thread interrupted during order processing", e);
            throw new IllegalStateException("Unable to create order due to interruption.", e);
        } catch (ExecutionException e) {
            LOG.error("ORDER CREATION FAILED - Execution exception occurred", e);
            LOG.error("Root cause: {}", e.getCause() != null ? e.getCause().getMessage() : "unknown");
            if (e.getCause() != null) {
                LOG.error("Root cause stack trace:", e.getCause());
            }
            throw new IllegalStateException("Unable to create order due to error: " +
                    (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), e);
        } catch (InvalidOrderException | PaymentDeclinedException e) {
            LOG.error("ORDER CREATION FAILED - Business validation error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            LOG.error("ORDER CREATION FAILED - Unexpected error occurred", e);
            LOG.error("Error type: {}, Message: {}", e.getClass().getName(), e.getMessage());
            throw new IllegalStateException("Unable to create order due to unexpected error: " + e.getMessage(), e);
        }
    }

//    TODO: Add link to shipping
//    @RequestMapping(method = RequestMethod.GET, value = "/orders")
//    public @ResponseBody
//    ResponseEntity<?> getOrders() {
//        List<CustomerOrder> customerOrders = customerOrderRepository.findAll();
//
//        Resources<CustomerOrder> resources = new Resources<>(customerOrders);
//
//        resources.forEach(r -> r);
//
//        resources.add(linkTo(methodOn(ShippingController.class, CustomerOrder.getShipment::ge)).withSelfRel());
//
//        // add other links as needed
//
//        return ResponseEntity.ok(resources);
//    }

    private float calculateTotal(List<Item> items) {
        float amount = 0F;
        float shipping = 4.99F;
        amount += items.stream().mapToDouble(i -> i.getQuantity() * i.getUnitPrice()).sum();
        amount += shipping;
        return amount;
    }

    @ResponseStatus(value = HttpStatus.NOT_ACCEPTABLE)
    public class PaymentDeclinedException extends IllegalStateException {
        public PaymentDeclinedException(String s) {
            super(s);
        }
    }

    @ResponseStatus(value = HttpStatus.NOT_ACCEPTABLE)
    public class InvalidOrderException extends IllegalStateException {
        public InvalidOrderException(String s) {
            super(s);
        }
    }
}
