package com.qlikview.mcp;

import com.qlikview.mcp.config.QlikViewProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(QlikViewProperties.class)
public class QlikViewMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(QlikViewMcpServerApplication.class, args);
    }
}
