package com.qlikview.mcp.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TableNameParserTest {

    private final TableNameParser parser = new TableNameParser();

    @Test
    void findsTableLabelsBeforeLoadStatements() {
        String script = """
            Fact:
            LOAD RecNo() as FactID AUTOGENERATE 10;

            Customer:
            LOAD RecNo() as CustomerID AUTOGENERATE 5;
            """;

        List<String> tables = parser.parse(script);
        assertThat(tables).containsExactly("Fact", "Customer");
    }

    @Test
    void doesNotTreatVariableDeclarationsAsTableLabels() {
        String script = """
            SET RowCount = 5000000;
            Fact:
            LOAD RecNo() as FactID AUTOGENERATE $(RowCount);
            """;

        List<String> tables = parser.parse(script);
        assertThat(tables).containsExactly("Fact");
    }

    @Test
    void deduplicatesRepeatedLabels() {
        String script = """
            Fact:
            LOAD RecNo() as FactID AUTOGENERATE 10;

            Fact:
            LOAD RecNo() as FactID2 AUTOGENERATE 10;
            """;

        List<String> tables = parser.parse(script);
        assertThat(tables).containsExactly("Fact");
    }

    @Test
    void returnsEmptyWhenNoTableLabelsPresent() {
        String script = "SET vFoo = 1;\nLOAD * FROM foo.qvd (qvd);";
        assertThat(parser.parse(script)).isEmpty();
    }
}
