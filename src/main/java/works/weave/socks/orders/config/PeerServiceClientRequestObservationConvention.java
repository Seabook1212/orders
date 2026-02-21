package works.weave.socks.orders.config;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;
import org.springframework.lang.NonNull;

import java.net.URI;
import java.util.Map;

/**
 * Custom observation convention that adds peer.service and semantic attributes
 * to outbound HTTP client spans for Jaeger dependency tracking.
 */
public class PeerServiceClientRequestObservationConvention extends DefaultClientRequestObservationConvention {

    // Map of host patterns to service names
    private static final Map<String, String> HOST_TO_SERVICE = Map.of(
            "user", "user",
            "payment", "payment",
            "shipping", "shipping",
            "catalogue", "catalogue",
            "carts", "carts",
            "orders", "orders"
    );

    @Override
    @NonNull
    public KeyValues getLowCardinalityKeyValues(@NonNull ClientRequestObservationContext context) {
        KeyValues keyValues = super.getLowCardinalityKeyValues(context);

        HttpRequest carrier = context.getCarrier();
        if (carrier == null) {
            return keyValues;
        }

        URI uri = carrier.getURI();
        String host = uri.getHost();
        String peerService = resolvePeerService(host);

        // Keep Zipkin/Jaeger-style tag expected by downstream tooling.
        keyValues = keyValues.and(KeyValue.of("span.kind", "client"));

        // Add peer.service for Jaeger dependency tracking
        keyValues = keyValues.and(KeyValue.of("peer.service", peerService));

        // Add network semantic attributes
        keyValues = keyValues.and(KeyValue.of("net.peer.name", host != null ? host : "unknown"));

        int port = uri.getPort();
        if (port > 0) {
            keyValues = keyValues.and(KeyValue.of("net.peer.port", String.valueOf(port)));
        }

        // Add HTTP semantic attributes
        String scheme = uri.getScheme();
        if (scheme != null) {
            keyValues = keyValues.and(KeyValue.of("http.scheme", scheme));
        }

        return keyValues;
    }

    @Override
    @NonNull
    public KeyValues getHighCardinalityKeyValues(@NonNull ClientRequestObservationContext context) {
        KeyValues keyValues = super.getHighCardinalityKeyValues(context);

        HttpRequest carrier = context.getCarrier();
        if (carrier == null) {
            return keyValues;
        }

        URI uri = carrier.getURI();

        // Add full URL as high cardinality (for debugging)
        keyValues = keyValues.and(KeyValue.of("http.url", uri.toString()));

        // Add path
        String path = uri.getPath();
        if (path != null && !path.isEmpty()) {
            keyValues = keyValues.and(KeyValue.of("http.target", path));
        }

        return keyValues;
    }

    /**
     * Resolve the peer service name from the host.
     * Handles Kubernetes service names like "user", "user.sock-shop", "user.sock-shop.svc.cluster.local"
     */
    private String resolvePeerService(String host) {
        if (host == null || host.isEmpty()) {
            return "unknown";
        }

        // Extract base service name from host
        // e.g., "user.sock-shop.svc.cluster.local" -> "user"
        // e.g., "payment" -> "payment"
        String baseHost = host.split("\\.")[0];

        // Check if it matches a known service
        for (Map.Entry<String, String> entry : HOST_TO_SERVICE.entrySet()) {
            if (baseHost.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }

        // Return the base hostname if not found in map
        return baseHost;
    }
}
