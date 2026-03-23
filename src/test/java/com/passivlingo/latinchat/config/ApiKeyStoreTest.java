package com.passivlingo.latinchat.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiKeyStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndReadsApiKey() throws IOException {
        ApiKeyStore store = new ApiKeyStore(tempDir);

        store.save("sk-test-value");

        assertTrue(store.read().isPresent());
        assertEquals("sk-test-value", store.read().orElseThrow());
    }

    @Test
    void clearsStoredApiKey() throws IOException {
        ApiKeyStore store = new ApiKeyStore(tempDir);

        store.save("sk-test-value");
        store.clear();

        assertTrue(store.read().isEmpty());
    }
}
