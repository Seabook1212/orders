package works.weave.socks.orders.controllers;

import brave.Tracer;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.rest.webmvc.RepositoryRestController;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import works.weave.socks.orders.config.OrdersConfigurationProperties;
import works.weave.socks.orders.entities.Address;
import works.weave.socks.orders.entities.Card;
import works.weave.socks.orders.entities.Customer;
import works.weave.socks.orders.entities.CustomerOrder;
import works.weave.socks.orders.entities.Item;
import works.weave.socks.orders.entities.Shipment;
import works.weave.socks.orders.resources.NewOrderResource;
import works.weave.socks.orders.services.AsyncGetService;
import works.weave.socks.orders.services.OrderService;
import works.weave.socks.orders.support.FailureClassifier;
import works.weave.socks.orders.support.TraceExceptionTagger;
import works.weave.socks.orders.values.PaymentRequest;
import works.weave.socks.orders.values.PaymentResponse;

import java.net.URI;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RepositoryRestController
public class OrdersController {
    private final Logger LOG = LoggerFactory.getLogger(getClass());

    @Autowired
    private OrdersConfigurationProperties config;

    @Autowired
    private AsyncGetService asyncGetService;

    @Autowired
    private OrderService orderService;

    @Autowired(required = false)
    private Tracer tracer;

    @Value(value = "${http.timeout:5}")
    private long timeout;

    @Value("${orders.slow-request-threshold-ms:3000}")
    private long slowRequestThresholdMs;

    @ResponseStatus(HttpStatus.CREATED)
    @RequestMapping(path = "/orders", consumes = MediaType.APPLICATION_JSON_VALUE, method = RequestMethod.POST)
    public @ResponseBody CustomerOrder newOrder(@Valid @RequestBody NewOrderResource item) {
        long startTime = System.currentTimeMillis();
        LOG.info("order_create_started customer_uri={} address_uri={} card_uri={} items_uri={}",
                item.customer, item.address, item.card, item.items);

        try {
            validateRequiredFields(item);

            Future<EntityModel<Address>> addressFuture = asyncGetService.getResource(item.address,
                    new ParameterizedTypeReference<EntityModel<Address>>() {
                    });
            Future<EntityModel<Customer>> customerFuture = asyncGetService.getResource(item.customer,
                    new ParameterizedTypeReference<EntityModel<Customer>>() {
                    });
            Future<EntityModel<Card>> cardFuture = asyncGetService.getResource(item.card,
                    new ParameterizedTypeReference<EntityModel<Card>>() {
                    });
            Future<List<Item>> itemsFuture = asyncGetService.getDataList(item.items,
                    new ParameterizedTypeReference<List<Item>>() {
                    });

            List<Item> items = awaitDependency(itemsFuture, item.items, "fetch_items");
            if (items == null || items.isEmpty()) {
                LOG.warn("order_create_rejected error_classification=missing_data dependency={} operation=fetch_items customer_uri={} items_uri={}",
                        dependencyName(item.items), item.customer, item.items);
                throw new InvalidOrderException("Invalid order request. Order requires at least one item.");
            }

            float amount = calculateTotal(items);
            Address address = requireContent(awaitDependency(addressFuture, item.address, "fetch_address"),
                    item.address, "fetch_address");
            Card card = requireContent(awaitDependency(cardFuture, item.card, "fetch_card"),
                    item.card, "fetch_card");
            Customer customer = requireContent(awaitDependency(customerFuture, item.customer, "fetch_customer"),
                    item.customer, "fetch_customer");

            URI paymentUri = config.getPaymentUri();
            PaymentResponse paymentResponse = awaitDependency(
                    asyncGetService.postResource(paymentUri, new PaymentRequest(address, card, customer, amount),
                            new ParameterizedTypeReference<PaymentResponse>() {
                            }),
                    paymentUri,
                    "authorize_payment");
            if (paymentResponse == null) {
                LOG.error("order_create_failed dependency={} operation=authorize_payment error_classification=serialization_error customer_id={} amount={} reason=null_payment_response",
                        dependencyName(paymentUri), customer.getId(), amount);
                throw new PaymentDeclinedException("Unable to parse authorisation packet");
            }
            if (!paymentResponse.isAuthorised()) {
                LOG.warn("order_create_rejected dependency={} operation=authorize_payment error_classification=payment_declined customer_id={} amount={} message={}",
                        dependencyName(paymentUri), customer.getId(), amount, paymentResponse.getMessage());
                throw new PaymentDeclinedException(paymentResponse.getMessage());
            }

            URI shippingUri = config.getShippingUri();
            Shipment shipment = awaitDependency(
                    asyncGetService.postResource(shippingUri, new Shipment(customer.getId()),
                            new ParameterizedTypeReference<Shipment>() {
                            }),
                    shippingUri,
                    "create_shipment");
            if (shipment == null) {
                LOG.error("order_create_failed dependency={} operation=create_shipment error_classification=missing_data customer_id={} reason=null_shipment_response",
                        dependencyName(shippingUri), customer.getId());
                throw new IllegalStateException("Unable to create order due to missing shipment response.");
            }

            CustomerOrder savedOrder = orderService.saveOrder(new CustomerOrder(
                    null,
                    customer.getId(),
                    customer,
                    address,
                    card,
                    items,
                    shipment,
                    Calendar.getInstance().getTime(),
                    amount));

            long totalDuration = System.currentTimeMillis() - startTime;
            if (totalDuration >= slowRequestThresholdMs) {
                LOG.warn("order_create_slow order_id={} customer_id={} latency_ms={} slow_threshold_ms={} item_count={} total={}",
                        savedOrder.getId(), customer.getId(), totalDuration, slowRequestThresholdMs, items.size(), amount);
            }
            LOG.info("order_create_completed order_id={} customer_id={} item_count={} total={} latency_ms={}",
                    savedOrder.getId(), customer.getId(), items.size(), amount, totalDuration);
            return savedOrder;
        } catch (TimeoutException e) {
            throw new IllegalStateException("Unable to create order due to timeout from one of the services.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            TraceExceptionTagger.tagCurrentSpan(tracer, e);
            LOG.error("order_create_failed error_classification=interrupted timeout_seconds={} customer_uri={}",
                    timeout, item.customer, e);
            throw new IllegalStateException("Unable to create order due to interruption.", e);
        } catch (ExecutionException e) {
            TraceExceptionTagger.tagCurrentSpan(tracer, e);
            throw new IllegalStateException("Unable to create order due to error: "
                    + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()), e);
        } catch (InvalidOrderException | PaymentDeclinedException e) {
            TraceExceptionTagger.tagCurrentSpan(tracer, e);
            throw e;
        } catch (TaskRejectedException e) {
            TraceExceptionTagger.tagCurrentSpan(tracer, e);
            LOG.error("order_create_failed error_classification=thread_pool_saturated customer_uri={} timeout_seconds={}",
                    item.customer, timeout, e);
            throw new IllegalStateException("Unable to create order because async capacity is exhausted.", e);
        } catch (Exception e) {
            Throwable rootCause = FailureClassifier.rootCause(e);
            TraceExceptionTagger.tagCurrentSpan(tracer, e);
            LOG.error("order_create_failed error_classification={} customer_uri={} cause_type={} cause_message={}",
                    FailureClassifier.classify(e), item.customer, rootCause.getClass().getSimpleName(),
                    rootCause.getMessage(), e);
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Unable to create order due to unexpected error: " + e.getMessage(), e);
        }
    }

    private void validateRequiredFields(NewOrderResource item) {
        if (item.address == null || item.customer == null || item.card == null || item.items == null) {
            LOG.warn("order_create_rejected error_classification=validation_error reason=missing_required_field address_present={} customer_present={} card_present={} items_present={}",
                    item.address != null,
                    item.customer != null,
                    item.card != null,
                    item.items != null);
            throw new InvalidOrderException("Invalid order request. Order requires customer, address, card and items.");
        }

        validateDependencyUri("customer", item.customer);
        validateDependencyUri("address", item.address);
        validateDependencyUri("card", item.card);
        validateDependencyUri("items", item.items);
    }

    private float calculateTotal(List<Item> items) {
        float amount = 0F;
        float shipping = 4.99F;
        amount += items.stream().mapToDouble(i -> i.getQuantity() * i.getUnitPrice()).sum();
        amount += shipping;
        return amount;
    }

    private <T> T awaitDependency(Future<T> future, URI dependencyUri, String operation)
            throws InterruptedException, ExecutionException, TimeoutException {
        try {
            return future.get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            LOG.error("dependency_call_timeout dependency={} operation={} uri={} timeout_seconds={}",
                    dependencyName(dependencyUri), operation, dependencyUri, timeout, e);
            throw e;
        }
    }

    private <T> T requireContent(EntityModel<T> model, URI dependencyUri, String operation) {
        if (model == null || model.getContent() == null) {
            LOG.error("dependency_response_invalid dependency={} operation={} uri={} error_classification=missing_data",
                    dependencyName(dependencyUri), operation, dependencyUri);
            throw new IllegalStateException("Dependency response did not include required content.");
        }
        return model.getContent();
    }

    private String dependencyName(URI uri) {
        return uri.getHost() == null ? uri.toString() : uri.getHost();
    }

    private void validateDependencyUri(String fieldName, URI uri) {
        String scheme = uri.getScheme();
        boolean supportedScheme = scheme != null
                && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));

        if (!uri.isAbsolute() || !supportedScheme || uri.getHost() == null) {
            LOG.warn("order_create_rejected error_classification=validation_error reason=invalid_dependency_uri field={} uri={}",
                    fieldName, uri);
            throw new InvalidOrderException("Invalid " + fieldName + " URI. Expected absolute http/https URI.");
        }
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
