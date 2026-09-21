package com.qlikview.mcp.guard;

import com.qlikview.mcp.config.QlikViewProperties;
import lombok.RequiredArgsConstructor;

/**
 * Caps how much text a tool response may return, per {@code qlikview.output.max-chars}. A result
 * that exceeds the cap is truncated rather than rejected outright, so a client still gets a usable
 * partial answer instead of an error for what is otherwise a valid call.
 */
@RequiredArgsConstructor
public class OutputLimiter {

    private final QlikViewProperties properties;

    /**
     * The text to return, truncated to the configured cap if needed, alongside whether truncation
     * occurred.
     */
    public record Result(String text, boolean truncated) { }

    public Result limit(String text) {
        int maxChars = properties.output().maxChars();
        if (text.length() <= maxChars) {
            return new Result(text, false);
        }
        return new Result(text.substring(0, maxChars), true);
    }
}
