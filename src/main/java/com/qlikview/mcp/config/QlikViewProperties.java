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
        Worker worker,
        Call call,
        Output output
) {

    /**
     * Location of the PowerShell worker script the gateway launches for each call.
     */
    public record Worker(String script) { }

    /**
     * How long a single gateway call may run before the worker process is force-killed.
     */
    public record Call(Duration timeout) { }

    /**
     * Caps on how much a tool response may return before it is truncated: {@code maxChars} for
     * free text, {@code maxItems} for each list a tool returns.
     */
    public record Output(int maxChars, int maxItems) { }

}
