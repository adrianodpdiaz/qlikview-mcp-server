package com.qlikview.mcp.config;

import com.qlikview.mcp.guard.DocumentPathGuard;
import com.qlikview.mcp.guard.OutputLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up the guard beans (path allowlisting, output truncation) used by tool classes before any
 * call reaches the gateway.
 */
@Configuration
public class GuardConfiguration {

    @Bean
    public DocumentPathGuard documentPathGuard(QlikViewProperties properties) {
        return new DocumentPathGuard(properties);
    }

    @Bean
    public OutputLimiter outputLimiter(QlikViewProperties properties) {
        return new OutputLimiter(properties);
    }
}
