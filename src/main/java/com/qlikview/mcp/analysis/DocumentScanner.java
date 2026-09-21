package com.qlikview.mcp.analysis;

import com.qlikview.mcp.config.QlikViewProperties;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Scans the allowlisted root folders (`qlikview.roots`) for QlikView documents. This is a plain
 * file-system walk with no QlikView dependency: it never opens or reads inside a document, only
 * lists what is on disk.
 */
@RequiredArgsConstructor
public class DocumentScanner {

    private static final Set<String> DOCUMENT_EXTENSIONS = Set.of("qvw", "qvf");

    private final QlikViewProperties properties;

    /**
     * One document found on disk: its name, absolute path, size, last-modified time, and
     * whether a `-prj` export folder sits alongside it.
     */
    public record DocumentInfo(String name, Path path, long sizeBytes, Instant modifiedAt, boolean hasPrjFolder) { }

    public List<DocumentInfo> listDocuments() {
        List<DocumentInfo> results = new ArrayList<>();
        for (String root : properties.roots()) {
            Path rootPath = Path.of(root);
            if (!Files.isDirectory(rootPath)) {
                continue;
            }
            results.addAll(scan(rootPath));
        }
        results.sort(Comparator.comparing(DocumentInfo::name));
        return results;
    }

    /**
     * Walks the tree with a visitor rather than {@link Files#walk}, so that a single unreadable
     * file or directory (permission-denied system folders, Windows library junctions such as
     * "My Music", broken symlinks) is skipped instead of aborting the entire scan.
     */
    private List<DocumentInfo> scan(Path root) {
        List<DocumentInfo> found = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile() && hasDocumentExtension(path)) {
                        toDocumentInfo(path, attrs).ifPresent(found::add);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path path, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            // walkFileTree itself only throws for a failure at the root; per-entry failures are
            // already absorbed by visitFileFailed above.
        }
        return found;
    }

    private Optional<DocumentInfo> toDocumentInfo(Path path, BasicFileAttributes attrs) {
        try {
            boolean hasPrj = Files.isDirectory(PrjFolder.forDocument(path));
            return Optional.of(new DocumentInfo(
                path.getFileName().toString(),
                path.toAbsolutePath().normalize(),
                attrs.size(),
                attrs.lastModifiedTime().toInstant(),
                hasPrj));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static boolean hasDocumentExtension(Path path) {
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
        return DOCUMENT_EXTENSIONS.contains(extension);
    }
}
