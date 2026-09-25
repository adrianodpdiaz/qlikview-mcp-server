package com.qlikview.mcp.config;

import com.qlikview.mcp.analysis.DataSourceParser;
import com.qlikview.mcp.analysis.DocumentScanner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up the beans that don't talk to QlikView directly: file-system scanning for
 * {@code list_documents}, and script-text parsing for {@code get_data_sources} (which is fed live
 * script text from the gateway, not read from disk itself).
 */
@Configuration
public class AnalysisConfiguration {

    @Bean
    public DocumentScanner documentScanner(QlikViewProperties properties) {
        return new DocumentScanner(properties);
    }

    @Bean
    public DataSourceParser dataSourceParser() {
        return new DataSourceParser();
    }
}
