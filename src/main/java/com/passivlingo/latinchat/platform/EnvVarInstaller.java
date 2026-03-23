package com.passivlingo.latinchat.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class EnvVarInstaller {
    private EnvVarInstaller() {
    }

    public static String installOpenAiKey(String key) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                return installWindows(key);
            }
            return installUnixLike(key);
        } catch (Exception ex) {
            return "Could not persist environment variable: " + ex.getMessage();
        }
    }

    private static String installWindows(String key) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("cmd", "/c", "setx", "OPENAI_KEY", key).start();
        int code = process.waitFor();
        if (code == 0) {
            return "OPENAI_KEY set for Windows user profile (new terminals/sessions).";
        }
        return "Windows setx returned code " + code + ".";
    }

    private static String installUnixLike(String key) throws IOException {
        String shell = System.getenv().getOrDefault("SHELL", "");
        String fileName = shell.endsWith("zsh") ? ".zshrc" : ".bash_profile";
        Path profile = Path.of(System.getProperty("user.home"), fileName);

        String line = "export OPENAI_KEY=\"" + key.replace("\"", "\\\"") + "\"";
        List<String> lines = Files.exists(profile) ? Files.readAllLines(profile) : List.of();
        boolean replaced = false;
        StringBuilder newContent = new StringBuilder();
        for (String existing : lines) {
            if (existing.startsWith("export OPENAI_KEY=")) {
                newContent.append(line).append(System.lineSeparator());
                replaced = true;
            } else {
                newContent.append(existing).append(System.lineSeparator());
            }
        }

        if (!replaced) {
            if (!lines.isEmpty()) {
                newContent.append(System.lineSeparator());
            }
            newContent.append(line).append(System.lineSeparator());
        }

        Files.writeString(profile, newContent.toString());
        return "OPENAI_KEY added to " + profile.getFileName() + " (effective in new shell sessions).";
    }
}
