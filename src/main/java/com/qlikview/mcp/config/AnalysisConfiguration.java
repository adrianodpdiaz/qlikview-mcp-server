package com.qlikview.mcp.config;

import com.qlikview.mcp.analysis.DocumentScanner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up the static-path analysis beans: file-system scanning and (later) script/`-prj` XML
 * parsing, none of which depend on QlikView being installed or running.
 */
@Configuration
public class AnalysisConfiguration {

    @Bean
    public DocumentScanner documentScanner(QlikViewProperties properties) {
        return new DocumentScanner(properties);
    }
}
