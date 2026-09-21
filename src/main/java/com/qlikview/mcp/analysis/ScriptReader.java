package com.qlikview.mcp.analysis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads a document's load script from its {@code -prj} export folder. QlikView writes the full
 * script, tab markers included, to {@code LoadScript.txt} inside that folder whenever the
 * document is saved with the folder already present alongside it - this is a plain text file, no
 * QlikView installation needed to read it.
 */
public class ScriptReader {

    private static final String LOAD_SCRIPT_FILE_NAME = "LoadScript.txt";
    private static final char BYTE_ORDER_MARK = '﻿';

    /**
     * Reads the script for the given document, or an empty result if no {@code -prj} export
     * exists for it (the folder was never created, or the document has not been saved since it was).
     */
    public Optional<String> readScript(Path documentPath) {
        Path scriptFile = PrjFolder.forDocument(documentPath).resolve(LOAD_SCRIPT_FILE_NAME);
        if (!Files.isRegularFile(scriptFile)) {
            return Optional.empty();
        }
        try {
            String content = Files.readString(scriptFile, StandardCharsets.UTF_8);
            return Optional.of(stripLeadingByteOrderMark(content));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read load script: " + scriptFile, e);
        }
    }

    /**
     * QlikView writes {@code LoadScript.txt} with a leading UTF-8 byte order mark, which is not
     * meaningful content and would otherwise be passed straight through to the MCP client.
     */
    private static String stripLeadingByteOrderMark(String content) {
        return content.isEmpty() || content.charAt(0) != BYTE_ORDER_MARK ?
            content : content.substring(1);
    }
}
