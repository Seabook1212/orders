package works.weave.socks.orders.controllers;

import brave.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;
import works.weave.socks.orders.support.FailureClassifier;
import works.weave.socks.orders.support.TraceExceptionTagger;

@ControllerAdvice
public class GlobalExceptionLoggingHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionLoggingHandler.class);

    @Autowired(required = false)
    private Tracer tracer;

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        Throwable rootCause = FailureClassifier.rootCause(ex);
        TraceExceptionTagger.tagCurrentSpan(tracer, ex);
        LOG.warn("request_rejected path={} method={} status={} error_classification={} cause_type={} cause_message={}",
                servletRequest.getRequestURI(),
                servletRequest.getMethod(),
                status.value(),
                FailureClassifier.classify(ex),
                rootCause.getClass().getSimpleName(),
                rootCause.getMessage());
        return super.handleHttpMessageNotReadable(ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        TraceExceptionTagger.tagCurrentSpan(tracer, ex);
        LOG.warn("request_rejected path={} method={} status={} error_classification=validation_error field_errors={}",
                servletRequest.getRequestURI(),
                servletRequest.getMethod(),
                status.value(),
                ex.getBindingResult().getErrorCount());
        return super.handleMethodArgumentNotValid(ex, headers, status, request);
    }
}
