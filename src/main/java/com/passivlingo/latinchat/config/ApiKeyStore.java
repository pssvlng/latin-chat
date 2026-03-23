package com.passivlingo.latinchat.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public final class ApiKeyStore {
    private static final String KEY = "openai.key";
    private final Path path;

    public ApiKeyStore(Path appHome) {
        this.path = appHome.resolve("config.properties");
    }

    public Optional<String> read() {
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException ex) {
            return Optional.empty();
        }

        String value = props.getProperty(KEY);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }

    public void save(String value) throws IOException {
        Files.createDirectories(path.getParent());
        Properties props = new Properties();
        props.setProperty(KEY, value);
        try (OutputStream out = Files.newOutputStream(path)) {
            props.store(out, "SPQR Latin Chat local configuration");
        }
    }

    public void clear() throws IOException {
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }
}