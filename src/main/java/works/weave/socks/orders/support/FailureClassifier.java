package works.weave.socks.orders.support;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

import jakarta.validation.ConstraintViolationException;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;

public final class FailureClassifier {
    private FailureClassifier() {
    }

    public static String classify(Throwable throwable) {
        Throwable rootCause = rootCause(throwable);
        String className = rootCause.getClass().getName();
        String message = rootCause.getMessage() == null ? "" : rootCause.getMessage().toLowerCase(Locale.ROOT);

        if (rootCause instanceof TaskRejectedException || rootCause instanceof RejectedExecutionException) {
            return "thread_pool_saturated";
        }
        if (rootCause instanceof MethodArgumentNotValidException
                || rootCause instanceof BindException
                || rootCause instanceof ConstraintViolationException) {
            return "validation_error";
        }
        if (rootCause instanceof HttpMessageConversionException
                || className.endsWith("HttpMessageNotReadableException")) {
            return "serialization_error";
        }
        if (className.endsWith("CallNotPermittedException")) {
            return "circuit_breaker_open";
        }
        if (rootCause instanceof HttpStatusCodeException httpStatusCodeException) {
            if (httpStatusCodeException.getStatusCode().is5xxServerError()) {
                return "http_5xx";
            }
            if (httpStatusCodeException.getStatusCode().is4xxClientError()) {
                return "http_4xx";
            }
        }
        if (rootCause instanceof ResourceAccessException) {
            return classifyNetworkFailure(rootCause);
        }
        if (rootCause instanceof SocketTimeoutException || rootCause instanceof InterruptedIOException) {
            return "http_timeout";
        }
        if (rootCause instanceof UnknownHostException) {
            return "dns_failure";
        }
        if (rootCause instanceof ConnectException) {
            return message.contains("refused") ? "connection_refused" : "connection_failure";
        }
        if (rootCause instanceof DataAccessException || className.startsWith("com.mongodb.")) {
            return classifyDatabaseFailure(message);
        }
        if (rootCause instanceof OutOfMemoryError || className.endsWith("OutOfMemoryError")) {
            return "oom_risk";
        }
        if (rootCause instanceof NullPointerException) {
            return "missing_data";
        }
        return "unexpected";
    }

    public static Throwable rootCause(Throwable throwable) {
        Throwable mostSpecificCause = NestedExceptionUtils.getMostSpecificCause(throwable);
        return mostSpecificCause == null ? throwable : mostSpecificCause;
    }

    private static String classifyNetworkFailure(Throwable throwable) {
        Throwable nestedCause = rootCause(throwable);
        if (nestedCause instanceof SocketTimeoutException || nestedCause instanceof InterruptedIOException) {
            return "http_timeout";
        }
        if (nestedCause instanceof UnknownHostException) {
            return "dns_failure";
        }
        if (nestedCause instanceof ConnectException) {
            String message = nestedCause.getMessage() == null ? "" : nestedCause.getMessage().toLowerCase(Locale.ROOT);
            return message.contains("refused") ? "connection_refused" : "connection_failure";
        }
        return "http_io_error";
    }

    private static String classifyDatabaseFailure(String message) {
        if (message.contains("wait queue") || message.contains("connection pool")) {
            return "db_pool_exhausted";
        }
        if (message.contains("timed out") || message.contains("timeout")) {
            return "db_timeout";
        }
        if (message.contains("deadlock")) {
            return "db_deadlock";
        }
        if (message.contains("rollback") || message.contains("transaction aborted")) {
            return "db_transaction_rollback";
        }
        if (message.contains("connection refused")
                || message.contains("connection reset")
                || message.contains("server selection")) {
            return "db_connection_failure";
        }
        return "db_error";
    }
}
