package com.qlikview.mcp.analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceParserTest {

    private final DataSourceParser parser = new DataSourceParser();

    @Test
    void parsesOdbcConnectStatement() {
        String script = "ODBC CONNECT TO [MyDataSource];";

        DataSourceParser.DataSources result = parser.parse(script);
        assertThat(result.connections()).containsExactly(
            new DataSourceParser.ConnectionReference("ODBC", "[MyDataSource]"));
    }

    @Test
    void parsesOledbConnectStatement() {
        String script = "OLEDB CONNECT TO [Provider=SQLOLEDB;Server=myserver;];";

        DataSourceParser.DataSources result = parser.parse(script);
        assertThat(result.connections()).containsExactly(
            new DataSourceParser.ConnectionReference("OLEDB", "[Provider=SQLOLEDB;Server=myserver;]"));
    }

    @Test
    void parsesGenericConnectStatement() {
        String script = "CONNECT TO [My QVD Folder];";

        DataSourceParser.DataSources result = parser.parse(script);
        assertThat(result.connections()).containsExactly(
            new DataSourceParser.ConnectionReference("CONNECT", "[My QVD Folder]"));
    }

    @Test
    void parsesQuotedFromClauseWithFormat() {
        String script = "LOAD * FROM 'C:\\data\\customers.qvd' (qvd);";

        DataSourceParser.DataSources result = parser.parse(script);
        assertThat(result.fileSources()).containsExactly(
            new DataSourceParser.FileSource("C:\\data\\customers.qvd", "(qvd)"));
    }

    @Test
    void parsesBracketedFromClauseWithOoxmlFormat() {
        String script = "LOAD * FROM [C:\\data\\Sales.xlsx] (ooxml, embedded labels, table is Sheet1);";

        DataSourceParser.DataSources result = parser.parse(script);

        assertThat(result.fileSources()).hasSize(1);
        assertThat(result.fileSources().getFirst().path()).isEqualTo("C:\\data\\Sales.xlsx");
        assertThat(result.fileSources().getFirst().format()).contains("ooxml");
    }

    @Test
    void parsesFromClauseWithoutFormat() {
        String script = "LOAD * FROM 'C:\\data\\raw.qvd';";

        DataSourceParser.DataSources result = parser.parse(script);
        assertThat(result.fileSources()).containsExactly(new DataSourceParser.FileSource("C:\\data\\raw.qvd", null));
    }

    @Test
    void parsesIncludeReference() {
        String script = "$(Include=common.qvs)";

        DataSourceParser.DataSources result = parser.parse(script);
        assertThat(result.includes()).containsExactly(new DataSourceParser.IncludeReference("common.qvs", false));
    }

    @Test
    void parsesMustIncludeReference() {
        String script = "$(Must_Include=connections.qvs)";

        DataSourceParser.DataSources result = parser.parse(script);
        assertThat(result.includes()).containsExactly(new DataSourceParser.IncludeReference("connections.qvs", true));
    }

    @Test
    void parsesBinaryStatement() {
        String script = "BINARY [C:\\data\\base.qvw];";

        DataSourceParser.DataSources result = parser.parse(script);
        assertThat(result.binarySource()).isEqualTo("C:\\data\\base.qvw");
    }

    @Test
    void binarySourceIsNullWhenScriptHasNoBinaryStatement() {
        DataSourceParser.DataSources result = parser.parse("LOAD 1 as X AUTOGENERATE 1;");
        assertThat(result.binarySource()).isNull();
    }

    @Test
    void parsesMultipleSourcesInOneScript() {
        String script = """
            ODBC CONNECT TO [Prod];
            Fact:
            SQL SELECT * FROM SalesTable;

            Customer:
            LOAD * FROM [C:\\data\\Customer.qvd] (qvd);

            $(Include=common.qvs);
            """;

        DataSourceParser.DataSources result = parser.parse(script);

        assertThat(result.connections()).hasSize(1);
        assertThat(result.fileSources()).hasSize(1);
        assertThat(result.includes()).hasSize(1);
    }

    @Test
    void returnsEmptyResultsForScriptWithNoDataSources() {
        DataSourceParser.DataSources result = parser.parse("LOAD RecNo() as X AUTOGENERATE 10;");

        assertThat(result.connections()).isEmpty();
        assertThat(result.fileSources()).isEmpty();
        assertThat(result.includes()).isEmpty();
        assertThat(result.binarySource()).isNull();
    }
}
