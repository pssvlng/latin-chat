package com.passivlingo.latinchat.config;

import java.nio.file.Path;

public final class AppPaths {
    private AppPaths() {
    }

    public static Path appHome() {
        return Path.of(System.getProperty("user.home"), ".spqr-latin-chat");
    }
}
