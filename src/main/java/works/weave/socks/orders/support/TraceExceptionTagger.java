package works.weave.socks.orders.support;

import brave.Span;
import brave.Tracer;

public final class TraceExceptionTagger {
    private TraceExceptionTagger() {
    }

    public static void tagCurrentSpan(Tracer tracer, Throwable throwable) {
        if (tracer == null || throwable == null) {
            return;
        }

        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) {
            return;
        }

        Throwable rootCause = FailureClassifier.rootCause(throwable);
        String type = rootCause.getClass().getName();
        String message = rootCause.getMessage() == null ? "" : rootCause.getMessage();

        currentSpan.tag("exception.type", type);
        currentSpan.tag("exception.message", message);
        currentSpan.tag("error.type", type);
        currentSpan.tag("error.message", message);
    }
}
