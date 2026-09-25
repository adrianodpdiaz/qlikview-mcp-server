package com.qlikview.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Externalised settings for the QlikView gateway, bound from {@code qlikview.*} properties.
 *
 * @param roots allowlisted folders a document path must resolve under before any gateway call is made
 */
@ConfigurationProperties(prefix = "qlikview")
public record QlikViewProperties(
        List<String> roots,
        Call call,
        Output output
) {

    /**
     * How long a single gateway call may run before its COM thread is abandoned as stuck.
     */
    public record Call(Duration timeout) { }

    /**
     * Caps on how much a tool response may return before it is truncated: {@code maxChars} for
     * free text, {@code maxItems} for each list a tool returns.
     */
    public record Output(int maxChars, int maxItems) { }

}
