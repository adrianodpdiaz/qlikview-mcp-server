package com.qlikview.mcp.gateway;

import com.qlikview.mcp.config.QlikViewProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies that a COM-side failure from {@code CoCreateInstance} against the
 * {@code QlikTech.QlikView} ProgID is wrapped into a {@link GatewayException} by every gateway
 * method, and that the underlying {@link com.sun.jna.platform.win32.COM.util.Factory} is reused
 * correctly across calls.
 */
@EnabledOnOs(OS.WINDOWS)
class JnaQlikViewGatewayTest {

    private final JnaQlikViewGateway gateway = new JnaQlikViewGateway(properties(Duration.ofSeconds(5)));

    @Test
    void wrapsComFailureAsGatewayException() {
        Path document = Path.of("C:\\does-not-matter.qvw");

        assertThatThrownBy(() -> gateway.getScript(document))
            .isInstanceOf(GatewayException.class)
            .hasMessageContaining("QlikView COM call failed");
    }

    @Test
    void reusesTheSameFactoryAcrossCalls() {
        Path document = Path.of("C:\\does-not-matter.qvw");

        assertThatThrownBy(() -> gateway.getScript(document)).isInstanceOf(GatewayException.class);
        assertThatThrownBy(() -> gateway.getScript(document)).isInstanceOf(GatewayException.class);
    }

    @Test
    void everyToolMethodWrapsComFailureConsistently() {
        Path document = Path.of("C:\\does-not-matter.qvw");

        assertThatThrownBy(() -> gateway.getVariables(document)).isInstanceOf(GatewayException.class);
        assertThatThrownBy(() -> gateway.getDataModel(document)).isInstanceOf(GatewayException.class);
        assertThatThrownBy(() -> gateway.getSheets(document)).isInstanceOf(GatewayException.class);
        assertThatThrownBy(() -> gateway.getObject(document, "Document\\CH01")).isInstanceOf(GatewayException.class);
        assertThatThrownBy(() -> gateway.evaluate(document, "=1")).isInstanceOf(GatewayException.class);
    }

    private static QlikViewProperties properties(Duration timeout) {
        return new QlikViewProperties(
            List.of("C:\\"),
            new QlikViewProperties.Call(timeout),
            new QlikViewProperties.Output(1000, 1000));
    }
}
