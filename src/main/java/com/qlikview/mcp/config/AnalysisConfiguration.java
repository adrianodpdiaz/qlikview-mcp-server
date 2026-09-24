package com.qlikview.mcp.config;

import com.qlikview.mcp.analysis.DocumentScanner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up the file-system scanning bean used by {@code list_documents}, the only part of the
 * server that reads from disk rather than talking to a running QlikView Desktop instance.
 */
@Configuration
public class AnalysisConfiguration {

    @Bean
    public DocumentScanner documentScanner(QlikViewProperties properties) {
        return new DocumentScanner(properties);
    }
}
