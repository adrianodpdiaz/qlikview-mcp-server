package com.qlikview.mcp.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qlikview.mcp.config.QlikViewProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the gateway's most important property first: a call to the worker process is always
 * bounded by {@code qlikview.call.timeout}, even when the worker process itself never returns,
 * the way some real QlikView COM calls do on certain failure paths.
 * <p>
 * Every test here launches a real {@code powershell.exe} process (against fake worker scripts, no
 * QlikView needed), so the whole class is skipped on non-Windows runners rather than failing.
 */
@EnabledOnOs(OS.WINDOWS)
class PowerShellQlikViewGatewayTest {

    private static final String QVW_FAKE_FILE = "document.qvw";
    private static final String HANGING_WORKER = "worker/hanging-worker.ps1";
    private static final String ECHO_WORKER = "worker/echo-worker.ps1";
    private static final String ERROR_WORKER = "worker/error-worker.ps1";
    private static final String SHEETS_ECHO_WORKER = "worker/sheets-echo-worker.ps1";
    private static final String OBJECT_ECHO_WORKER = "worker/object-echo-worker.ps1";

    @Test
    @Timeout(5)
    void timesOutAndKillsWorkerWhenItHangs() {
        PowerShellQlikViewGateway gateway = gatewayFor(HANGING_WORKER, Duration.ofMillis(500));

        assertThatThrownBy(() -> gateway.getScript(Path.of(QVW_FAKE_FILE)))
            .isInstanceOf(GatewayException.class)
            .hasMessageContaining("timed out");
    }

    @Test
    void returnsResultOnSuccessfulResponse() {
        PowerShellQlikViewGateway gateway = gatewayFor(ECHO_WORKER, Duration.ofSeconds(10));

        String script = gateway.getScript(Path.of(QVW_FAKE_FILE));
        assertThat(script).isEqualTo("fake script for getScript");
    }

    @Test
    void getSheetsParsesSheetsAndObjectsFromWorkerResponse() {
        PowerShellQlikViewGateway gateway = gatewayFor(SHEETS_ECHO_WORKER, Duration.ofSeconds(10));

        QlikViewGateway.SheetInfo[] sheets = gateway.getSheets(Path.of(QVW_FAKE_FILE));

        assertThat(sheets).hasSize(1);
        assertThat(sheets[0].caption()).isEqualTo("Main");
        assertThat(sheets[0].objects()).extracting(QlikViewGateway.SheetObjectInfo::objectId)
                .containsExactly("Document\\CS01", "Document\\CH03");
        assertThat(sheets[0].objects()[1].objectType()).isEqualTo("11");
    }

    @Test
    void getObjectParsesTypeDimensionsAndExpressionsFromWorkerResponse() {
        PowerShellQlikViewGateway gateway = gatewayFor(OBJECT_ECHO_WORKER, Duration.ofSeconds(10));

        QlikViewGateway.ObjectDetail detail = gateway.getObject(Path.of(QVW_FAKE_FILE), "Document\\CH03");

        assertThat(detail.objectId()).isEqualTo("Document\\CH03");
        assertThat(detail.objectType()).isEqualTo("11");
        assertThat(detail.dimensions()).containsExactly("CustomerName");
        assertThat(detail.expressions()).containsExactly("Sum(SalesAmount)");
    }

    @Test
    void wrapsWorkerErrorResponseInGatewayException() {
        PowerShellQlikViewGateway gateway = gatewayFor(ERROR_WORKER, Duration.ofSeconds(10));

        assertThatThrownBy(() -> gateway.getScript(Path.of(QVW_FAKE_FILE)))
            .isInstanceOf(GatewayException.class)
            .hasMessageContaining("simulated worker error");
    }

    private PowerShellQlikViewGateway gatewayFor(String classpathScript, Duration timeout) {
        Path script = resolveTestResource(classpathScript);
        QlikViewProperties properties = new QlikViewProperties(
            List.of(),
            new QlikViewProperties.Worker(script.toString()),
            new QlikViewProperties.Call(timeout),
            new QlikViewProperties.Output(200_000, 1000));
        return new PowerShellQlikViewGateway(script, properties, new ObjectMapper());
    }

    private Path resolveTestResource(String classpathLocation) {
        URL url = getClass().getClassLoader().getResource(classpathLocation);
        if (url == null) {
            throw new IllegalStateException("Test resource not found on classpath: " + classpathLocation);
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
