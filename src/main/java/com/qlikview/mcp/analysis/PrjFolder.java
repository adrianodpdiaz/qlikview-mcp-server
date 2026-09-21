package com.qlikview.mcp.analysis;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

/**
 * QlikView writes a document's structural export to a sibling folder named {@code <documentName>-prj}
 * (e.g. {@code report.qvw} -> {@code report-prj}), but only once that folder already exists
 * next to the document at save time - it is not created automatically.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PrjFolder {

    static Path forDocument(Path documentPath) {
        String fileName = documentPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot < 0 ? fileName : fileName.substring(0, dot);
        return documentPath.resolveSibling(baseName + "-prj");
    }
}
