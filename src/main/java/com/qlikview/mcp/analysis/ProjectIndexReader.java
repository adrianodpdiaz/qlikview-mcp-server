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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads a document's sheet/object index from its {@code -prj} export ({@code
 * QlikViewProject.xml}), which lists every sheet and, per sheet, the object IDs placed on it.
 * Each object's own detail lives in a separate file named after its ID's last path segment (for
 * example {@code Document\CH03} in the index corresponds to {@code CH03.xml}); that file's root
 * XML element name identifies the object's type ({@code GraphProperties} for a chart,
 * {@code CurrentSelectionProperties} for a current-selections box, and so on).
 */
public class ProjectIndexReader {

    private static final String PROJECT_FILE_NAME = "QlikViewProject.xml";

    /**
     * One sheet: its id (as recorded in the project index, e.g. {@code Document\SH01}) and the
     * ids of the objects placed on it.
     */
    public record SheetEntry(String sheetId, List<String> objectIds) { }

    /**
     * One object as found on disk: its id, its type (the root XML element name of its own
     * `-prj` file, e.g. {@code GraphProperties}), and the path to that file.
     */
    public record ObjectEntry(String objectId, String type, Path file) { }

    public Optional<List<SheetEntry>> readSheets(Path documentPath) {
        Path projectFile = PrjFolder.forDocument(documentPath).resolve(PROJECT_FILE_NAME);
        if (!Files.isRegularFile(projectFile)) {
            return Optional.empty();
        }
        return Optional.of(parseSheets(loadXml(projectFile)));
    }

    /**
     * Reads one object's type and file location, or an empty result if no {@code -prj} export
     * exists for the document or the object id is not present in it.
     */
    public Optional<ObjectEntry> readObject(Path documentPath, String objectId) {
        Path prjFolder = PrjFolder.forDocument(documentPath);
        String fileBaseName = lastSegmentOf(objectId);
        Path objectFile = prjFolder.resolve(fileBaseName + ".xml");
        if (!Files.isRegularFile(objectFile)) {
            return Optional.empty();
        }
        String type = loadXml(objectFile).getDocumentElement().getNodeName();
        return Optional.of(new ObjectEntry(objectId, type, objectFile));
    }

    private static List<SheetEntry> parseSheets(Document project) {
        List<SheetEntry> sheets = new ArrayList<>();
        NodeList sheetNodes = project.getElementsByTagName("PrjSheetProperties");
        for (int i = 0; i < sheetNodes.getLength(); i++) {
            Element sheetElement = (Element) sheetNodes.item(i);
            String sheetId = textContentOf(sheetElement, "SheetId");
            List<String> objectIds = new ArrayList<>();
            NodeList objectIdNodes = sheetElement.getElementsByTagName("ObjectId");
            for (int j = 0; j < objectIdNodes.getLength(); j++) {
                objectIds.add(objectIdNodes.item(j).getTextContent());
            }
            sheets.add(new SheetEntry(sheetId, objectIds));
        }
        return sheets;
    }

    private static String lastSegmentOf(String objectId) {
        int backslash = objectId.lastIndexOf('\\');
        return backslash < 0 ? objectId : objectId.substring(backslash + 1);
    }

    private static String textContentOf(Element parent, String childTagName) {
        NodeList children = parent.getElementsByTagName(childTagName);
        return children.getLength() == 0 ? null : children.item(0).getTextContent();
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
