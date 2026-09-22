package com.qlikview.mcp.guard;

import com.qlikview.mcp.config.QlikViewProperties;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Caps how much a tool response may return, per {@code qlikview.output.max-chars} for free text
 * and {@code qlikview.output.max-items} for lists. A result that exceeds a cap is truncated rather
 * than rejected outright, so a client still gets a usable partial answer instead of an error for
 * what is otherwise a valid call.
 */
@RequiredArgsConstructor
public class OutputLimiter {

    private final QlikViewProperties properties;

    /**
     * The text to return, truncated to the configured cap if needed, alongside whether truncation
     * occurred.
     */
    public record Result(String text, boolean truncated) { }

    /**
     * The list to return, truncated to the configured cap if needed, alongside whether truncation
     * occurred.
     */
    public record ListResult<T>(List<T> items, boolean truncated) { }

    public Result limit(String text) {
        int maxChars = properties.output().maxChars();
        if (text.length() <= maxChars) {
            return new Result(text, false);
        }
        return new Result(text.substring(0, maxChars), true);
    }

    public <T> ListResult<T> limitList(List<T> items) {
        int maxItems = properties.output().maxItems();
        if (items.size() <= maxItems) {
            return new ListResult<>(items, false);
        }
        return new ListResult<>(items.subList(0, maxItems), true);
    }
}
