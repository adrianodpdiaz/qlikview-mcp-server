package com.qlikview.mcp.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ScriptReaderTest {

    @TempDir
    Path tempDir;

    private final ScriptReader scriptReader = new ScriptReader();

    @Test
    void readsScriptFromPrjFolder() throws IOException {
        Path document = tempDir.resolve("report.qvw");
        Path prjFolder = Files.createDirectory(tempDir.resolve("report-prj"));
        Files.writeString(prjFolder.resolve("LoadScript.txt"), "///$tab Main\nLOAD 1 as X;", StandardCharsets.UTF_8);

        Optional<String> script = scriptReader.readScript(document);
        assertThat(script).contains("///$tab Main\nLOAD 1 as X;");
    }

    @Test
    void stripsLeadingByteOrderMark() throws IOException {
        Path document = tempDir.resolve("report.qvw");
        Path prjFolder = Files.createDirectory(tempDir.resolve("report-prj"));
        Files.writeString(prjFolder.resolve("LoadScript.txt"), "﻿///$tab Main\nLOAD 1 as X;", StandardCharsets.UTF_8);

        Optional<String> script = scriptReader.readScript(document);
        assertThat(script).contains("///$tab Main\nLOAD 1 as X;");
        assertThat(script.orElseThrow()).doesNotStartWith("﻿");
    }

    @Test
    void returnsEmptyWhenNoPrjFolderExists() {
        Path document = tempDir.resolve("report.qvw");
        Optional<String> script = scriptReader.readScript(document);
        assertThat(script).isEmpty();
    }

    @Test
    void returnsEmptyWhenPrjFolderExistsButHasNoLoadScript() throws IOException {
        Path document = tempDir.resolve("report.qvw");
        Files.createDirectory(tempDir.resolve("report-prj"));

        Optional<String> script = scriptReader.readScript(document);
        assertThat(script).isEmpty();
    }
}
