package com.qlikview.mcp.analysis;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Reads field tags from a document's {@code -prj} export ({@code DocInternals.xml}'s
 * {@code FieldTags} section). QlikView tags every field by role and type here as part of its own
 * internal bookkeeping - {@code $key} for fields that link multiple tables, {@code $hidden} and
 * {@code $system} for its own built-in fields ({@code $Field}, {@code $Table}, and so on), and
 * type tags such as {@code $numeric}, {@code $integer}, {@code $date}. This file mixes field and
 * table names in the same flat lists with no way to tell them apart, so table names are read from
 * the script instead (see {@link TableNameParser}); this class only reports field-level tags.
 */
public class FieldTagReader {

    private static final String DOC_INTERNALS_FILE_NAME = "DocInternals.xml";
    private static final String KEY_TAG = "$key";
    private static final String SYSTEM_TAG = "$system";

    /**
     * One field's tags as recorded by QlikView: whether it links multiple tables ({@code $key}),
     * whether it is one of QlikView's own built-in fields ({@code $system}), and every raw tag
     * name QlikView recorded for it (including type tags such as {@code $numeric}).
     */
    public record FieldTag(String name, boolean isKey, boolean isSystem, Set<String> tags) { }

    public Optional<Set<FieldTag>> readFieldTags(Path documentPath) {
        Path docInternals = PrjFolder.forDocument(documentPath).resolve(DOC_INTERNALS_FILE_NAME);
        if (!Files.isRegularFile(docInternals)) {
            return Optional.empty();
        }
        return Optional.of(parse(docInternals));
    }

    private Set<FieldTag> parse(Path docInternals) {
        java.util.Map<String, Set<String>> tagsByField = new java.util.LinkedHashMap<>();

        Document document = loadXml(docInternals);
        NodeList tagDataNodes = document.getElementsByTagName("FieldTagData");
        for (int i = 0; i < tagDataNodes.getLength(); i++) {
            Element tagData = (Element) tagDataNodes.item(i);
            String tag = textContentOf(tagData, "Tag");
            if (tag == null) {
                continue;
            }
            for (String fieldName : fieldNamesOf(tagData)) {
                tagsByField.computeIfAbsent(fieldName, n -> new LinkedHashSet<>()).add(tag);
            }
        }

        Set<FieldTag> result = new LinkedHashSet<>();
        tagsByField.forEach((name, tags) ->
            result.add(new FieldTag(name, tags.contains(KEY_TAG), tags.contains(SYSTEM_TAG), tags)));
        return result;
    }

    private static java.util.List<String> fieldNamesOf(Element tagData) {
        java.util.List<String> names = new java.util.ArrayList<>();
        NodeList fieldNamesNodes = tagData.getElementsByTagName("FieldNames");
        if (fieldNamesNodes.getLength() == 0) {
            return names;
        }
        Element fieldNames = (Element) fieldNamesNodes.item(0);
        NodeList strings = fieldNames.getElementsByTagName("String");
        for (int i = 0; i < strings.getLength(); i++) {
            names.add(strings.item(i).getTextContent());
        }
        return names;
    }

    private static String textContentOf(Element parent, String childTagName) {
        NodeList children = parent.getElementsByTagName(childTagName);
        if (children.getLength() == 0) {
            return null;
        }
        Node first = children.item(0);
        return first.getTextContent();
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
