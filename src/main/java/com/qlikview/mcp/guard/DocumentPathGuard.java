package com.qlikview.mcp.guard;

import com.qlikview.mcp.config.QlikViewProperties;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Resolves and validates a document path supplied by an MCP tool call before it is ever passed to
 * the gateway. Every path is canonicalised and checked against the configured allowlist of root
 * folders, and only {@code .qvw}/{@code .qvf} files are accepted, so a client can never point a
 * tool at an arbitrary file on the machine.
 */
public class DocumentPathGuard {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("qvw", "qvf");

    private final List<Path> roots;

    public DocumentPathGuard(QlikViewProperties properties) {
        this.roots = properties.roots().stream()
            .map(Path::of)
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .toList();
    }

    /**
     * Resolves the given document path, throwing {@link GuardException} if it does not have an
     * allowed extension, does not resolve under any configured root, or is not a valid path at all.
     * The returned path is canonical and safe to pass to the gateway.
     */
    public Path resolve(String documentPath) {
        if (documentPath == null || documentPath.isBlank()) {
            throw new GuardException("Document path must not be blank");
        }

        Path candidate;
        try {
            candidate = Path.of(documentPath).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new GuardException("Not a valid path: " + documentPath, e);
        }

        String extension = extensionOf(candidate);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new GuardException(
                "Document path must end in .qvw or .qvf, got: " + documentPath);
        }

        if (roots.isEmpty()) {
            throw new GuardException(
                "No allowlisted roots are configured (qlikview.roots); every document path is rejected");
        }

        boolean underAllowedRoot = roots.stream().anyMatch(candidate::startsWith);
        if (!underAllowedRoot) {
            throw new GuardException(
                "Document path does not resolve under any allowlisted root: " + documentPath);
        }

        return candidate;
    }

    private static String extensionOf(Path path) {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase();
    }
}
