package com.fairhome.testkit;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads {@code .json} files from a classpath directory into {@link JsonCase} records.
 *
 * <p>A file may be a single case, an object with a {@code cases} array, or a top-level array of
 * cases. That is the only convention the parent executor imposes; each suite decides what lives
 * inside {@code input} and {@code expected}.
 */
public final class JsonCaseCatalog {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private JsonCaseCatalog() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static List<JsonCase> load(String classpathDirectory) {
        List<Path> files = listJsonFiles(classpathDirectory);
        if (files.isEmpty()) {
            throw new IllegalStateException("No JSON cases under classpath:" + classpathDirectory);
        }
        List<JsonCase> cases = new ArrayList<>();
        for (Path file : files) {
            cases.addAll(parseFile(file.getFileName().toString(), read(file)));
        }
        return cases;
    }

    static List<JsonCase> parseFile(String filename, String json) {
        JsonNode root = MAPPER.readTree(json);
        List<JsonCase> cases = new ArrayList<>();
        if (root.isArray()) {
            int i = 1;
            for (JsonNode node : root) {
                cases.add(toCase(filename + "#" + i, node, i));
                i++;
            }
        } else if (root.has("cases") && root.get("cases").isArray()) {
            int i = 1;
            for (JsonNode node : root.get("cases")) {
                cases.add(toCase(filename + "#" + i, node, i));
                i++;
            }
        } else {
            cases.add(toCase(filename, root, 1));
        }
        return cases;
    }

    private static JsonCase toCase(String fallbackName, JsonNode node, int index) {
        if (!node.has("input") || !node.has("expected")) {
            throw new IllegalArgumentException(fallbackName
                    + " is missing required \"input\" or \"expected\" fields");
        }
        String name = node.has("name") && node.get("name").isString()
                ? node.get("name").asString()
                : fallbackName + " case " + index;
        JsonCase.CompareMode mode = JsonCase.CompareMode.SUBTREE;
        if (node.has("compare") && "STRICT".equalsIgnoreCase(node.get("compare").asString())) {
            mode = JsonCase.CompareMode.STRICT;
        }
        return new JsonCase(name, fallbackName, node.get("input"), node.get("expected"), mode);
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + file, e);
        }
    }

    private static List<Path> listJsonFiles(String classpathDirectory) {
        String resource = classpathDirectory.startsWith("/") ? classpathDirectory.substring(1) : classpathDirectory;
        var url = Thread.currentThread().getContextClassLoader().getResource(resource);
        if (url == null) {
            throw new IllegalStateException("Classpath directory not found: " + classpathDirectory);
        }
        try {
            URI uri = url.toURI();
            if ("jar".equals(uri.getScheme())) {
                try (FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                    Path root = fs.getPath(resource);
                    return list(root);
                }
            }
            return list(Paths.get(uri));
        } catch (URISyntaxException | IOException e) {
            throw new IllegalStateException("Cannot list " + classpathDirectory, e);
        }
    }

    private static List<Path> list(Path directory) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
    }

    static JsonNode readTree(InputStream in) {
        return MAPPER.readTree(in);
    }
}
