package com.qlikview.mcp.analysis;

import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ChartReaderTest {

    @TempDir
    Path tempDir;

    private final ChartReader chartReader = new ChartReader();

    /**
     * Mirrors the real structure found in a QlikView -prj export's chart file, including a
     * decoy Name/v element elsewhere in the document (as real exports have, e.g. gauge segment
     * names), to prove the reader only picks up the Dimensions/Expressions ones.
     */
    @Language("XML")
    private static final String CHART_FIXTURE = """
        <GraphProperties>
          <GaugeSegments>
            <GaugeSegmentDef>
              <Name>
                <v>Segment 1</v>
              </Name>
            </GaugeSegmentDef>
          </GaugeSegments>
          <Dimensions>
            <ChartDimensionDataDef>
              <PseudoDef>
                <Name>CustomerName</Name>
                <Type>PRESENT</Type>
              </PseudoDef>
            </ChartDimensionDataDef>
            <ChartDimensionDataDef>
              <PseudoDef>
                <Name>RegionName</Name>
                <Type>PRESENT</Type>
              </PseudoDef>
            </ChartDimensionDataDef>
          </Dimensions>
          <Expressions>
            <ArrayOfMainExpressionData>
              <MainExpressionData>
                <Data>
                  <ExpressionData>
                    <Definition>
                      <v>Sum(SalesAmount)</v>
                    </Definition>
                  </ExpressionData>
                </Data>
              </MainExpressionData>
            </ArrayOfMainExpressionData>
          </Expressions>
        </GraphProperties>
        """;

    @Test
    void readsDimensionFieldNames() throws IOException {
        Path file = writeFixture();

        ChartReader.ChartContent chart = chartReader.read(file);
        assertThat(chart.dimensions()).extracting(ChartReader.ChartDimension::fieldName)
            .containsExactly("CustomerName", "RegionName");
    }

    @Test
    void readsExpressionDefinitions() throws IOException {
        Path file = writeFixture();

        ChartReader.ChartContent chart = chartReader.read(file);
        assertThat(chart.expressions()).extracting(ChartReader.ChartExpression::definition)
            .containsExactly("Sum(SalesAmount)");
    }

    @Test
    void doesNotPickUpUnrelatedNameElementsElsewhereInTheDocument() throws IOException {
        Path file = writeFixture();

        ChartReader.ChartContent chart = chartReader.read(file);
        assertThat(chart.dimensions()).extracting(ChartReader.ChartDimension::fieldName)
            .doesNotContain("Segment 1");
    }

    @Test
    void returnsEmptyListsForAChartWithNoDimensionsOrExpressions() throws IOException {
        Path file = tempDir.resolve("empty.xml");
        Files.writeString(file, "<GraphProperties></GraphProperties>", StandardCharsets.UTF_8);

        ChartReader.ChartContent chart = chartReader.read(file);
        assertThat(chart.dimensions()).isEmpty();
        assertThat(chart.expressions()).isEmpty();
    }

    private Path writeFixture() throws IOException {
        Path file = tempDir.resolve("CH01.xml");
        Files.writeString(file, CHART_FIXTURE, StandardCharsets.UTF_8);
        return file;
    }
}
