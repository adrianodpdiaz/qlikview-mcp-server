package com.qlikview.mcp.config;

import com.qlikview.mcp.analysis.DocumentScanner;
import com.qlikview.mcp.analysis.ScriptReader;
import com.qlikview.mcp.analysis.VariableParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires up the static-path analysis beans: file-system scanning and `-prj` export parsing,
 * neither of which depends on QlikView being installed or running.
 */
@Configuration
public class AnalysisConfiguration {

    @Bean
    public DocumentScanner documentScanner(QlikViewProperties properties) {
        return new DocumentScanner(properties);
    }

    @Bean
    public ScriptReader scriptReader() {
        return new ScriptReader();
    }

    @Bean
    public VariableParser variableParser() {
        return new VariableParser();
    }
}
