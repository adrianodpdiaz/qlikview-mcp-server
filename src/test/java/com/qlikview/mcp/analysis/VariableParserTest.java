package com.qlikview.mcp.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VariableParserTest {

    private final VariableParser parser = new VariableParser();

    @Test
    void parsesSimpleSetStatement() {
        List<VariableParser.VariableDeclaration> result = parser.parse("SET RowCount = 5000000;");
        assertThat(result).containsExactly(new VariableParser.VariableDeclaration("RowCount", "5000000", false));
    }

    @Test
    void parsesLetStatement() {
        List<VariableParser.VariableDeclaration> result = parser.parse("LET vToday = Today();");
        assertThat(result).containsExactly(new VariableParser.VariableDeclaration("vToday", "Today()", true));
    }

    @Test
    void parsesMultipleDeclarationsInOrder() {
        String script = """
            SET ThousandSep=',';
            SET DecimalSep='.';
            LET vRowCount = NoOfRows();
            """;

        List<VariableParser.VariableDeclaration> result = parser.parse(script);
        assertThat(result).extracting(VariableParser.VariableDeclaration::name)
            .containsExactly("ThousandSep", "DecimalSep", "vRowCount");
    }

    @Test
    void laterDeclarationOverridesEarlierOneWithSameName() {
        String script = """
            SET vLimit = 10;
            SET vLimit = 20;
            """;

        List<VariableParser.VariableDeclaration> result = parser.parse(script);
        assertThat(result).containsExactly(new VariableParser.VariableDeclaration("vLimit", "20", false));
    }

    @Test
    void ignoresLinesThatAreNotDeclarations() {
        String script = """
            // SET vCommentedOut = 1;
            LOAD * FROM foo.qvd;
            Fact:
            LOAD RecNo() as ID AUTOGENERATE 10;
            """;

        List<VariableParser.VariableDeclaration> result = parser.parse(script);
        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyForBlankScript() {
        assertThat(parser.parse("")).isEmpty();
    }

    @Test
    void trimsWhitespaceAroundValue() {
        List<VariableParser.VariableDeclaration> result = parser.parse("SET vName =   'hello'  ;");
        assertThat(result).containsExactly(new VariableParser.VariableDeclaration("vName", "'hello'", false));
    }
}
