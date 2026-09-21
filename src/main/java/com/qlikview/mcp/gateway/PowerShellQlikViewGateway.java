package com.qlikview.mcp.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qlikview.mcp.config.QlikViewProperties;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Gateway Option A: one PowerShell process per call, COM opened and torn down within that
 * process, no persistent worker to manage.
 * <p>
 * QlikView's COM layer does not always fail fast (opening a nonexistent document can hang
 * instead of throwing), so every call here is bounded by {@code qlikview.call.timeout} and the
 * worker process is force-killed if that bound is exceeded. An unconditional
 * {@link Process#waitFor()} must never be used here, by design, since it would hang the entire
 * MCP tool call along with it.
 */
@RequiredArgsConstructor
public class PowerShellQlikViewGateway implements QlikViewGateway {

    private final Path workerScript;
    private final QlikViewProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public String getScript(Path document) {
        JsonNode result = call("getScript", Map.of("documentPath", document.toString()));
        return result.get("script").asText();
    }

    @Override
    public VariableDescription[] getVariables(Path document) {
        JsonNode result = call("getVariables", Map.of("documentPath", document.toString()));
        List<VariableDescription> variables = new ArrayList<>();
        for (JsonNode v : result.get("variables")) {
            variables.add(new VariableDescription(
                v.get("name").asText(),
                v.get("rawValue").asText(),
                v.get("isSystem").asBoolean()));
        }
        return variables.toArray(new VariableDescription[0]);
    }

    @Override
    public DataModel getDataModel(Path document) {
        JsonNode result = call("getDataModel", Map.of("documentPath", document.toString()));

        List<TableInfo> tables = new ArrayList<>();
        for (JsonNode t : result.get("tables")) {
            tables.add(new TableInfo(t.get("name").asText()));
        }

        List<FieldInfo> fields = new ArrayList<>();
        for (JsonNode f : result.get("fields")) {
            List<String> srcTables = new ArrayList<>();
            for (JsonNode s : f.get("srcTables")) {
                srcTables.add(s.asText());
            }
            fields.add(new FieldInfo(
                f.get("name").asText(),
                f.get("cardinal").asLong(),
                f.get("isSystem").asBoolean(),
                f.get("isNumeric").asBoolean(),
                srcTables.toArray(new String[0])));
        }

        return new DataModel(tables.toArray(new TableInfo[0]), fields.toArray(new FieldInfo[0]));
    }

    @Override
    public SheetInfo[] getSheets(Path document) {
        JsonNode result = call("getSheets", Map.of("documentPath", document.toString()));

        List<SheetInfo> sheets = new ArrayList<>();
        for (JsonNode s : result.get("sheets")) {
            List<SheetObjectInfo> objects = new ArrayList<>();
            for (JsonNode o : s.get("objects")) {
                objects.add(new SheetObjectInfo(o.get("objectId").asText(), o.get("objectType").asText()));
            }
            sheets.add(new SheetInfo(s.get("caption").asText(), objects.toArray(new SheetObjectInfo[0])));
        }
        return sheets.toArray(new SheetInfo[0]);
    }

    @Override
    public String evaluate(Path document, String expression) {
        JsonNode result = call("evaluate", Map.of(
            "documentPath", document.toString(),
            "expression", expression));
        return result.get("value").asText();
    }

    private JsonNode call(String operation, Map<String, String> params) {
        Map<String, String> request = new java.util.HashMap<>(params);
        request.put("operation", operation);

        ProcessBuilder builder = new ProcessBuilder(
            "powershell", "-ExecutionPolicy", "Bypass", "-NoProfile", "-File", workerScript.toString());
        builder.redirectErrorStream(false);

        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new GatewayException("Failed to start PowerShell worker process", e);
        }

        try {
            writeRequest(process, request);

            Duration callTimeout = properties.call().timeout();
            boolean finished = process.waitFor(callTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new GatewayException(
                    "QlikView worker call timed out after " + callTimeout
                        + " (operation=" + operation + "). "
                        + "QlikView Desktop may be showing a blocked dialog on this document; "
                        + "check the Desktop session before retrying.");
            }

            String stdout = readAll(process.getInputStream());
            String stderr = readAll(process.getErrorStream());

            JsonNode response;
            try {
                response = objectMapper.readTree(stdout);
            } catch (IOException e) {
                throw new GatewayException(
                    "QlikView worker returned non-JSON output (exit=" + process.exitValue()
                            + "): " + truncate(stdout) + (stderr.isBlank() ? "" : " | stderr: " + truncate(stderr)), e);
            }

            if (!response.path("ok").asBoolean(false)) {
                String error = response.path("error").asText("unknown worker error");
                throw new GatewayException("QlikView worker error (operation=" + operation + "): " + error);
            }

            return response.get("result");

        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new GatewayException("Interrupted while waiting for QlikView worker", e);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private void writeRequest(Process process, Map<String, String> request) {
        try (OutputStream stdin = process.getOutputStream()) {
            String json = objectMapper.writeValueAsString(request);
            stdin.write(json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write request to QlikView worker stdin", e);
        }
    }

    private static String readAll(InputStream in) {
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String truncate(String s) {
        int max = 500;
        return s.length() > max ? s.substring(0, max) + "...(truncated)" : s;
    }
}
