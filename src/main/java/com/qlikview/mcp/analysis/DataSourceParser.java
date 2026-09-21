package com.qlikview.mcp.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts data-source references from QlikView load script text: connection statements, file
 * sources in {@code LOAD}/{@code SELECT} statements, and include references. This is a
 * best-effort, line and pattern based parser, the same approach as {@link VariableParser} and
 * {@link TableNameParser} - it does not evaluate the script, so a source path built from a
 * variable (e.g. {@code $(vPath)\file.qvd}) is returned as the literal, unevaluated text.
 */
public class DataSourceParser {

    private static final Pattern CONNECT_STATEMENT =
        Pattern.compile("(?im)^\\s*(ODBC|OLEDB|CUSTOM)\\s+CONNECT\\s+TO\\s+(.+?);\\s*$");

    private static final Pattern GENERIC_CONNECT =
        Pattern.compile("(?im)^\\s*CONNECT\\s+TO\\s+(.+?);\\s*$");

    /**
     * Matches a FROM clause naming a file: FROM 'path' or FROM [path], optionally followed by a
     * format spec such as (qvd) or (ooxml, embedded labels, table is Foo). Deliberately does not
     * try to parse FROM used with a SQL SELECT against an already-open ODBC/OLEDB connection,
     * since that form has no file path to report.
     */
    private static final Pattern FROM_FILE =
        Pattern.compile("(?im)FROM\\s+(\\[[^]]+]|'[^']+'|\"[^\"]+\")\\s*(\\([^)]*\\))?");

    private static final Pattern INCLUDE =
        Pattern.compile("(?i)\\$\\((Must_)?Include\\s*=\\s*(\\[[^]]+]|'[^']+'|\"[^\"]+\"|[^)]+)\\)");

    private static final Pattern BINARY_STATEMENT = Pattern.compile("(?im)^\\s*BINARY\\s+(.+?);\\s*$");

    /**
     * One connection statement: its declared type ({@code ODBC}, {@code OLEDB}, {@code CUSTOM},
     * or {@code CONNECT} for a generic/other form) and its connection string, exactly as written
     * in the script - callers are responsible for redacting credentials before this leaves the
     * process, the same as for {@code get_script}.
     */
    public record ConnectionReference(String type, String connectionString) { }

    /**
     * One file referenced by a FROM clause, with its optional format specification (e.g.
     * {@code (qvd)}, {@code (ooxml, embedded labels, table is Foo)}) if one was given.
     */
    public record FileSource(String path, String format) { }

    /**
     * One {@code $(Include=...)}/{@code $(Must_Include=...)} reference.
     */
    public record IncludeReference(String path, boolean mustInclude) { }

    /**
     * Everything this parser found in a script, grouped by kind.
     */
    public record DataSources(
        List<ConnectionReference> connections,
        List<FileSource> fileSources,
        List<IncludeReference> includes,
        String binarySource) {
    }

    public DataSources parse(String script) {
        return new DataSources(
            parseConnections(script),
            parseFileSources(script),
            parseIncludes(script),
            parseBinarySource(script));
    }

    private static List<ConnectionReference> parseConnections(String script) {
        List<ConnectionReference> connections = new ArrayList<>();
        Matcher typed = CONNECT_STATEMENT.matcher(script);
        while (typed.find()) {
            connections.add(new ConnectionReference(typed.group(1).toUpperCase(), typed.group(2).trim()));
        }
        Matcher generic = GENERIC_CONNECT.matcher(script);
        while (generic.find()) {
            String candidate = generic.group(1).trim();
            boolean alreadyCaptured = connections.stream().anyMatch(c -> c.connectionString().equals(candidate));
            if (!alreadyCaptured) {
                connections.add(new ConnectionReference("CONNECT", candidate));
            }
        }
        return connections;
    }

    private static List<FileSource> parseFileSources(String script) {
        List<FileSource> sources = new ArrayList<>();
        Matcher matcher = FROM_FILE.matcher(script);
        while (matcher.find()) {
            String path = stripQuotes(matcher.group(1));
            String format = matcher.group(2) == null ? null : matcher.group(2).trim();
            sources.add(new FileSource(path, format));
        }
        return sources;
    }

    private static List<IncludeReference> parseIncludes(String script) {
        List<IncludeReference> includes = new ArrayList<>();
        Matcher matcher = INCLUDE.matcher(script);
        while (matcher.find()) {
            boolean mustInclude = matcher.group(1) != null;
            includes.add(new IncludeReference(stripQuotes(matcher.group(2).trim()), mustInclude));
        }
        return includes;
    }

    private static String parseBinarySource(String script) {
        Matcher matcher = BINARY_STATEMENT.matcher(script);
        return matcher.find() ? stripQuotes(matcher.group(1).trim()) : null;
    }

    private static String stripQuotes(String value) {
        if (value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        boolean bracketed = first == '[' && last == ']';
        boolean quoted = (first == '\'' && last == '\'') || (first == '"' && last == '"');
        return (bracketed || quoted) ? value.substring(1, value.length() - 1) : value;
    }
}
