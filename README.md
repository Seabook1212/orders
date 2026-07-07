# orders

This repository contains the `orders` service used in the enhanced Sock Shop
system for the EviRCA microservice root cause analysis benchmark.

The service is derived from the original Sock Shop orders service, but has been
modernized and instrumented for telemetry-rich RCA experiments. In the benchmark,
`orders` participates in the e-commerce checkout path by fetching customer,
address, card, and cart item data from downstream services, authorizing payment,
creating shipment records, and persisting customer orders in MongoDB.

## Research Context

This service is part of the benchmark introduced in:

**EviRCA: An Evidence-Aware Skill-Based LLM Agent and a Telemetry-Rich
Multi-Modal Benchmark for Microservice Root Cause Analysis**

The benchmark is built on an enhanced Sock Shop deployment and provides
synchronized metrics, logs, traces, service topology, fault injection artifacts,
upgraded service implementations, and fine-grained RCA labels. The enhanced
services are designed to make failures observable across multiple telemetry
modalities so RCA methods can distinguish root causes from propagated symptoms.

## Service Role

`orders` exposes the order creation and lookup API for Sock Shop. During order
creation it:

- validates the incoming order request;
- fetches customer, address, card, and cart item resources;
- calculates the order total;
- calls the payment service for authorization;
- calls the shipping service to create a shipment;
- stores the resulting `CustomerOrder` in MongoDB.

The service depends on:

- `user` for customer, address, and card data;
- `carts` for cart items;
- `payment` for payment authorization;
- `shipping` for shipment creation;
- `orders-db` / MongoDB for order persistence.

## Benchmark-Oriented Enhancements

The original Sock Shop Java services were upgraded for the EviRCA benchmark.
For `orders`, the main changes include:

- migration to Spring Boot 3.4.1 and Java 17;
- Spring Boot Actuator and Micrometer Prometheus metrics at `/metrics`;
- Micrometer/Brave distributed tracing with B3 propagation;
- Zipkin-compatible trace export, typically consumed by Jaeger;
- filtering of health and metrics endpoints to reduce trace noise;
- outbound HTTP client spans with `peer.service` and network semantic tags;
- MongoDB repository spans for order query and persistence operations;
- trace-aware logging with `traceId` and `spanId`;
- structured dependency, timeout, slow-call, validation, and failure logs;
- failure classification and span exception tagging;
- configurable timeout and slow-request thresholds;
- Chaos Monkey Spring Boot support for controlled service/controller faults.

These changes make the service useful for collecting application metrics, logs,
and traces during controlled workload and fault-injection runs.

## API

The service listens on port `8082` by default.

Useful endpoints:

- `GET /health` - service health check;
- `GET /metrics` - Prometheus metrics;
- `GET /orders` - list orders through Spring Data REST;
- `GET /orders/{id}` - fetch an order;
- `POST /orders` - create an order.

The legacy API specification is available in [api-spec/orders.json](api-spec/orders.json).

## Configuration

Configuration is defined in
[src/main/resources/application.properties](src/main/resources/application.properties).

Common environment variables:

| Variable | Default | Description |
| --- | --- | --- |
| `port` | `8082` | HTTP server port. |
| `db` | `orders-db` | MongoDB host used in the connection URI. |
| `zipkin_enabled` | `true` | Enables Micrometer tracing export. |
| `zipkin_host` | `jaeger-collector.observability.svc.cluster.local` | Trace collector host. |
| `HTTP_TIMEOUT` | `5` | Timeout in seconds for dependency calls. |
| `HTTP_SLOW_CALL_THRESHOLD_MS` | `2000` | Slow dependency-call log threshold. |
| `ORDERS_SLOW_REQUEST_THRESHOLD_MS` | `3000` | Slow order-create log threshold. |
| `SPRING_PROFILES_ACTIVE` | `chaos-monkey` | Active Spring profile. |

## Build

Build the Java package:

```sh
mvn -DskipTests package
```

Build the Docker image:

```sh
GROUP=weaveworksdemos COMMIT=test ./scripts/build.sh
```

## Test

Run Maven unit tests:

```sh
mvn test
```

Run the legacy Python-based test wrapper:

```sh
./test/test.sh unit.py
```

## Run Locally

Start the service with Maven:

```sh
mvn spring-boot:run
```

Then check the service:

```sh
curl http://localhost:8082/health
curl http://localhost:8082/metrics
```

For a full Sock Shop benchmark deployment, run this service together with its
downstream services, MongoDB, Prometheus, and the configured tracing backend.

## Docker Push

```sh
GROUP=weaveworksdemos COMMIT=test ./scripts/push.sh
```

## License

This repository keeps the original Sock Shop license. See [LICENSE](LICENSE).
