package com.qlikview.mcp.gateway;

/**
 * Wraps any failure to complete a QlikView gateway call: timeout, worker process failure, or a
 * QlikView/COM-side error.
 */
public class GatewayException extends RuntimeException {

    public GatewayException(String message) {
        super(message);
    }

    public GatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
