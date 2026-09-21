package com.qlikview.mcp.analysis;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a chart object's dimensions and expressions from its {@code -prj} export file (a
 * {@code GraphProperties} document). This mirrors the COM property chain
 * {@code GetProperties().Dimensions.Item(i).PseudoDef.Name} for dimensions and
 * {@code .Expressions.Item(i).Item(0).Data.ExpressionData.Definition.v} for expressions - the XML
 * carries the same structure under the same element names, so the same navigation applies here.
 */
public class ChartReader {

    /**
     * One dimension's field name, as resolved from {@code PseudoDef/Name}.
     */
    public record ChartDimension(String fieldName) { }

    /**
     * One expression's definition text, as resolved from {@code ExpressionData/Definition/v}.
     */
    public record ChartExpression(String definition) { }

    /**
     * A chart's dimensions and expressions, in the order QlikView stores them.
     */
    public record ChartContent(List<ChartDimension> dimensions, List<ChartExpression> expressions) { }

    public ChartContent read(Path graphPropertiesFile) {
        Document document = loadXml(graphPropertiesFile);
        return new ChartContent(readDimensions(document), readExpressions(document));
    }

    private static List<ChartDimension> readDimensions(Document document) {
        List<ChartDimension> dimensions = new ArrayList<>();
        NodeList dimensionDefs = document.getElementsByTagName("ChartDimensionDataDef");
        for (int i = 0; i < dimensionDefs.getLength(); i++) {
            Element dimensionDef = (Element) dimensionDefs.item(i);
            firstDirectChild(dimensionDef, "PseudoDef")
                .flatMap(pseudoDef -> firstDirectChild(pseudoDef, "Name"))
                .ifPresent(name -> dimensions.add(new ChartDimension(name.getTextContent())));
        }
        return dimensions;
    }

    private static List<ChartExpression> readExpressions(Document document) {
        List<ChartExpression> expressions = new ArrayList<>();
        NodeList expressionDataNodes = document.getElementsByTagName("ExpressionData");
        for (int i = 0; i < expressionDataNodes.getLength(); i++) {
            Element expressionData = (Element) expressionDataNodes.item(i);
            firstDirectChild(expressionData, "Definition")
                .flatMap(definition -> firstDirectChild(definition, "v"))
                .ifPresent(v -> expressions.add(new ChartExpression(v.getTextContent())));
        }
        return expressions;
    }

    /**
     * QlikView's `-prj` XML nests same-named elements at different depths (for example, many
     * unrelated elements are also called {@code Name}), so a plain {@code getElementsByTagName}
     * search from the document root would pick up the wrong one. This restricts the search to
     * direct children of a given element.
     */
    private static java.util.Optional<Element> firstDirectChild(Element parent, String childName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && child.getNodeName().equals(childName)) {
                return java.util.Optional.of(child);
            }
        }
        return java.util.Optional.empty();
    }

    private static Document loadXml(Path path) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(path.toFile());
        } catch (ParserConfigurationException | SAXException e) {
            throw new IllegalStateException("Failed to parse " + path, e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path, e);
        }
    }
}
