package works.weave.socks.orders.config;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Adds Kubernetes metadata tags to every exported trace span.
 */
@Configuration
public class KubernetesTraceTagsConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(KubernetesTraceTagsConfiguration.class);
    private static final Path SERVICE_ACCOUNT_NAMESPACE_PATH =
            Path.of("/var/run/secrets/kubernetes.io/serviceaccount/namespace");

    private static final List<String> POD_ENV_KEYS = List.of(
            "POD_NAME",
            "MY_POD_NAME",
            "K8S_POD_NAME",
            "KUBERNETES_POD_NAME",
            "HOSTNAME"
    );

    private static final List<String> CONTAINER_ENV_KEYS = List.of(
            "CONTAINER_NAME",
            "MY_CONTAINER_NAME",
            "K8S_CONTAINER_NAME",
            "KUBERNETES_CONTAINER_NAME",
            "container_name"
    );

    private static final List<String> NODE_ENV_KEYS = List.of(
            "NODE_NAME",
            "MY_NODE_NAME",
            "K8S_NODE_NAME",
            "KUBERNETES_NODE_NAME"
    );

    private static final List<String> NAMESPACE_ENV_KEYS = List.of(
            "POD_NAMESPACE",
            "MY_POD_NAMESPACE",
            "K8S_NAMESPACE",
            "KUBERNETES_NAMESPACE",
            "NAMESPACE"
    );

    @Bean
    public SpanHandler kubernetesTraceTagsSpanHandler(Environment environment) {
        KubernetesMetadata metadata = resolveKubernetesMetadata(environment);
        LOG.info("Kubernetes trace tags configured: container={}, pod={}, namespace={}, node={}",
                metadata.containerName,
                metadata.podName,
                metadata.namespaceName,
                metadata.nodeName);

        return new SpanHandler() {
            @Override
            public boolean end(brave.propagation.TraceContext context, MutableSpan span, Cause cause) {
                span.tag("k8s.container.name", metadata.containerName);
                span.tag("k8s.pod.name", metadata.podName);
                span.tag("k8s.namespace.name", metadata.namespaceName);
                span.tag("k8s.node.name", metadata.nodeName);

                span.tag("container", metadata.containerName);
                span.tag("pod", metadata.podName);
                span.tag("namespace", metadata.namespaceName);
                span.tag("node", metadata.nodeName);
                return true;
            }
        };
    }

    private KubernetesMetadata resolveKubernetesMetadata(Environment environment) {
        String podName = firstNonBlank(environment, POD_ENV_KEYS, "unknown");
        String containerName = firstNonBlank(environment, CONTAINER_ENV_KEYS, null);
        if (!StringUtils.hasText(containerName)) {
            containerName = firstNonBlank(environment, List.of("SERVICE_NAME", "spring.application.name"), "unknown");
        }
        String nodeName = firstNonBlank(environment, NODE_ENV_KEYS, "unknown");
        String namespaceName = firstNonBlank(environment, NAMESPACE_ENV_KEYS, null);
        if (!StringUtils.hasText(namespaceName)) {
            namespaceName = readNamespaceFromServiceAccount();
        }
        if (!StringUtils.hasText(namespaceName)) {
            namespaceName = "unknown";
        }

        return new KubernetesMetadata(containerName, podName, namespaceName, nodeName);
    }

    private String firstNonBlank(Environment environment, List<String> keys, String fallback) {
        for (String key : keys) {
            String value = environment.getProperty(key);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return fallback;
    }

    private String readNamespaceFromServiceAccount() {
        try {
            if (!Files.exists(SERVICE_ACCOUNT_NAMESPACE_PATH)) {
                return null;
            }
            String content = Files.readString(SERVICE_ACCOUNT_NAMESPACE_PATH, StandardCharsets.UTF_8);
            if (StringUtils.hasText(content)) {
                return content.trim();
            }
        } catch (IOException e) {
            LOG.debug("Could not read Kubernetes namespace from service account file", e);
        }
        return null;
    }

    private record KubernetesMetadata(
            String containerName,
            String podName,
            String namespaceName,
            String nodeName
    ) {
    }
}
