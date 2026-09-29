package com.hazely.laborlens.jobs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;

/** Uses the same immutable input bytes for hashing, parsing and provenance. */
@Component
public class JobFiles {
    public static final int MAX_BYTES = 50 * 1024 * 1024;
    private final Path root;

    public JobFiles(@Value("${app.data.root:../data}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    public record Input(byte[] bytes, String checksum) {
        public static Input of(byte[] bytes) {
            if (bytes.length == 0 || bytes.length > MAX_BYTES) {
                throw new IllegalArgumentException("Source file has an unsupported size");
            }
            return new Input(bytes, hash(bytes));
        }
    }

    public Path path(SourceSpec source) throws IOException {
        Path base = root();
        Path relative = Path.of(source.path());
        if (relative.isAbsolute() || relative.normalize().startsWith("..")) {
            throw new IOException("Invalid source path");
        }
        Path dir = directory(relative.getParent());
        Path path = dir.resolve(relative.getFileName());
        if (Files.isSymbolicLink(path) || (Files.exists(path) && !path.toRealPath().startsWith(base))) {
            throw new IOException("Invalid source file path");
        }
        return path;
    }

    public Input read(SourceSpec source) throws IOException {
        Path path = path(source);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IOException("Source file is not readable");
        }
        try (var stream = Files.newInputStream(path)) {
            return Input.of(stream.readNBytes(MAX_BYTES + 1));
        }
    }

    public Path archive(long run, SourceSpec source, Input input, String version) throws IOException {
        Path dir = directory(Path.of("runs")).resolve(Long.toString(run));
        Files.createDirectory(dir);
        Files.write(dir.resolve(source.fileName()), input.bytes(), StandardOpenOption.CREATE_NEW);
        properties(dir.resolve("input.properties"), Map.of("file", source.fileName(), "url", source.url(),
                "checksum", input.checksum(), "process_version", version,
                "size", Integer.toString(input.bytes().length)));
        return dir;
    }

    public void publish(SourceSpec source, Input input) throws IOException {
        Path target = path(source);
        Path temp = Files.createTempFile(target.getParent(), ".download-", ".tmp");
        try {
            Files.write(temp, input.bytes());
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static void properties(Path path, Map<String, String> values) throws IOException {
        Properties properties = new Properties();
        properties.putAll(values);
        try (var out = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
            properties.store(out, "Job evidence and processing metadata");
        }
    }

    public static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private Path root() throws IOException {
        Files.createDirectories(root);
        return root.toRealPath();
    }

    private Path directory(Path relative) throws IOException {
        Path base = root();
        Path dir = base;
        for (Path part : relative) {
            dir = dir.resolve(part);
            if (Files.isSymbolicLink(dir)) throw new IOException("Symlink directories are not supported");
            Files.createDirectories(dir);
            if (!dir.toRealPath().startsWith(base)) throw new IOException("Invalid data directory");
        }
        return dir;
    }
}
