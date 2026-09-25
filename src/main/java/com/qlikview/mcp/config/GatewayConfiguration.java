package com.qlikview.mcp.config;

import com.qlikview.mcp.gateway.JnaQlikViewGateway;
import com.qlikview.mcp.gateway.QlikViewGateway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfiguration {

    @Bean
    public QlikViewGateway qlikViewGateway(QlikViewProperties properties) {
        return new JnaQlikViewGateway(properties);
    }
}
