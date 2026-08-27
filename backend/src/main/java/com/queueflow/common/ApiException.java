package com.queueflow.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    public ApiException(HttpStatus status, String message) { super(message); this.status = status; }
    public HttpStatus getStatus() { return status; }
    public static class NotFound extends ApiException {
        public NotFound(String m) { super(HttpStatus.NOT_FOUND, m); }
    }
    public static class Rule extends ApiException {
        public Rule(String m) { super(HttpStatus.CONFLICT, m); }
    }
}
