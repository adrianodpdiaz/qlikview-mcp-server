package com.qlikview.mcp.analysis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts table names from QlikView load script text, using the standard {@code TableName:}
 * labelling syntax that precedes a {@code LOAD}/{@code SELECT} statement. There is no reliable
 * source of table names in the {@code -prj} export's XML files - {@code DocInternals.xml}'s
 * {@code TableTags} element is present but consistently empty, and its {@code FieldTags} section
 * mixes table and field names in the same flat lists with no way to tell them apart - so the
 * script is the only static source for this.
 */
public class TableNameParser {

    private static final Pattern TABLE_LABEL = Pattern.compile("(?m)^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*$");

    public List<String> parse(String script) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        Matcher matcher = TABLE_LABEL.matcher(script);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return new ArrayList<>(names);
    }
}
