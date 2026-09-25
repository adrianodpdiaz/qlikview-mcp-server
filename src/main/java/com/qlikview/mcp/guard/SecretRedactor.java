package com.qlikview.mcp.guard;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scrubs credentials out of text before it leaves the process. QlikView connection strings
 * commonly embed a password directly as a {@code key=value;} pair (ODBC/OLEDB style, e.g.
 * {@code PWD=hunter2;} or {@code Password=hunter2;}), and QlikView returns this text completely
 * unredacted from every COM call that contains it - nothing upstream of this class does any scrubbing.
 */
public class SecretRedactor {

    private static final String MASK = "***REDACTED***";

    /**
     * Matches {@code key=value} pairs inside a connection string where the key looks like a
     * credential: password/pwd, uid/user id/user, and their common punctuation variants. The
     * value runs up to the next {@code ;}, the next matching quote, or the end of the string.
     */
    private static final Pattern CREDENTIAL_PAIR = Pattern.compile(
        "(?i)\\b(pwd|password|uid|user\\s*id|user)\\s*=\\s*"
            + "('[^']*'|\"[^\"]*\"|[^;'\"]*)");

    private static final Pattern SECRET_LIKE_NAME = Pattern.compile(
        "(?i)(pass|pwd|secret|token|apikey|api_key|credential)");

    /**
     * Replaces every credential-shaped {@code key=value} pair in the given text with a masked
     * placeholder, keeping the key so the structure of the connection string is still readable.
     * Text with no credential-shaped content is returned unchanged.
     */
    public String redactConnectionCredentials(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = CREDENTIAL_PAIR.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1) + "=" + MASK));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * True if a variable's name suggests it holds a secret (password, token, API key, and
     * similar) - a caller-side signal to redact or flag that variable's value, since the value
     * itself carries no marker QlikView exposes.
     */
    public boolean looksLikeSecretName(String variableName) {
        return variableName != null && SECRET_LIKE_NAME.matcher(variableName).find();
    }
}
