package com.qlikview.mcp.analysis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts variable declarations from QlikView load script text. This is a best-effort, line and
 * pattern based parser matching {@code SET name = value;} and {@code LET name = value;}
 * statements - it does not evaluate expressions, so a {@code LET} value that depends on another
 * variable or a function call is returned as the literal script text, not a resolved result.
 * <p>
 * When a variable is declared more than once, the last declaration in the script wins, matching
 * how QlikView itself would apply the statements in order during a reload.
 */
public class VariableParser {

    private static final Pattern DECLARATION =
        Pattern.compile("(?im)^\\s*(SET|LET)\\s+([A-Za-z_][A-Za-z0-9_.]*)\\s*=\\s*(.*?);\\s*$");

    /**
     * One SET/LET declaration found in the script: its name, the literal right-hand-side text
     * (unevaluated), and whether it was a LET (evaluated at reload) or SET (literal) statement.
     */
    public record VariableDeclaration(String name, String value, boolean isLet) { }

    public List<VariableDeclaration> parse(String script) {
        Map<String, VariableDeclaration> byName = new LinkedHashMap<>();
        Matcher matcher = DECLARATION.matcher(script);
        while (matcher.find()) {
            String keyword = matcher.group(1);
            String name = matcher.group(2);
            String value = matcher.group(3).trim();
            byName.put(name, new VariableDeclaration(name, value, "LET".equalsIgnoreCase(keyword)));
        }
        return new ArrayList<>(byName.values());
    }
}
