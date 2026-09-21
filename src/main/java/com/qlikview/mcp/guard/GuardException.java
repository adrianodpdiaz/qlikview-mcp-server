package com.qlikview.mcp.guard;

/**
 * Raised when a tool call fails one of the guard checks: an out-of-allowlist document path, a
 * disallowed file extension, or any other input that must never reach the gateway.
 */
public class GuardException extends RuntimeException {

    public GuardException(String message) {
        super(message);
    }

    public GuardException(String message, Throwable cause) {
        super(message, cause);
    }
}
