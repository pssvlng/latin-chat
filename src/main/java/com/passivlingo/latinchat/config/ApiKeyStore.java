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
    private static final String LANGUAGES_KEY = "openai.lang";
    private final Path path;

    public ApiKeyStore(Path appHome) {
        this.path = appHome.resolve("config.properties");
    }

    public Optional<String> read() {
        Properties props = loadProperties();

        String value = props.getProperty(KEY);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }

    public Optional<String> readLanguageCsv() {
        Properties props = loadProperties();
        String value = props.getProperty(LANGUAGES_KEY);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }

    public void save(String value) throws IOException {
        Files.createDirectories(path.getParent());
        Properties props = loadProperties();
        props.setProperty(KEY, value);
        storeProperties(props);
    }

    public void saveLanguageCsv(String csv) throws IOException {
        Files.createDirectories(path.getParent());
        Properties props = loadProperties();
        props.setProperty(LANGUAGES_KEY, csv == null ? "la" : csv.trim());
        storeProperties(props);
    }

    public void clear() throws IOException {
        if (Files.exists(path)) {
            Files.delete(path);
        }
    }

    private Properties loadProperties() {
        Properties props = new Properties();
        if (!Files.exists(path)) {
            return props;
        }
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
        } catch (IOException ignored) {
            // Return empty properties on read issues; callers apply safe defaults.
        }
        return props;
    }

    private void storeProperties(Properties props) throws IOException {
        try (OutputStream out = Files.newOutputStream(path)) {
            props.store(out, "SPQR Latin Chat local configuration");
        }
    }
}