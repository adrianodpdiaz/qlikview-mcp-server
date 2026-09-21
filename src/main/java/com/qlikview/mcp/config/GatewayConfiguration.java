package com.qlikview.mcp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qlikview.mcp.gateway.GatewayException;
import com.qlikview.mcp.gateway.PowerShellQlikViewGateway;
import com.qlikview.mcp.gateway.QlikViewGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Configuration
public class GatewayConfiguration {

    @Bean
    public QlikViewGateway qlikViewGateway(QlikViewProperties properties, ObjectMapper objectMapper,
            ResourceLoader resourceLoader, @Value("${qlikview.worker.script}") String workerScriptLocation) {
        Path workerScript = extractWorkerScript(resourceLoader, workerScriptLocation);
        return new PowerShellQlikViewGateway(workerScript, properties, objectMapper);
    }

    /**
     * The worker script ships as a classpath resource (inside the jar in production), but
     * {@link ProcessBuilder} needs a real file on disk. Copy it to a temp file once at startup
     * rather than requiring an unpacked jar layout.
     */
    private Path extractWorkerScript(ResourceLoader resourceLoader, String location) {
        Resource resource = resourceLoader.getResource(location);
        try {
            Path target = Files.createTempFile("qlikview-worker", ".ps1");
            target.toFile().deleteOnExit();
            try (InputStream in = resource.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (IOException e) {
            throw new GatewayException("Failed to extract QlikView worker script from " + location, e);
        }
    }
}
