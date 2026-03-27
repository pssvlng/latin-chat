package com.passivlingo.latinchat.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class EnvVarInstaller {
    private static final String DEFAULT_MODEL = "gpt-5.4-mini";

    private EnvVarInstaller() {
    }

    public static String installOpenAiKey(String key) {
        return installOpenAiSettings(key, null);
    }

    public static String installOpenAiSettings(String key, String model) {
        String effectiveModel = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                return installWindows(key, effectiveModel);
            }
            return installUnixLike(key, effectiveModel);
        } catch (Exception ex) {
            return "Could not persist environment variable: " + ex.getMessage();
        }
    }

    public static String clearOpenAiSettings() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                return clearWindows();
            }
            return clearUnixLike();
        } catch (Exception ex) {
            return "Could not clear environment variable: " + ex.getMessage();
        }
    }

    private static String installWindows(String key, String model) throws IOException, InterruptedException {
        Process keyProcess = new ProcessBuilder("cmd", "/c", "setx", "OPENAI_KEY", key).start();
        int keyCode = keyProcess.waitFor();
        Process modelProcess = new ProcessBuilder("cmd", "/c", "setx", "OPENAI_MODEL", model).start();
        int modelCode = modelProcess.waitFor();
        if (keyCode == 0 && modelCode == 0) {
            return "OPENAI_KEY and OPENAI_MODEL set for Windows user profile (new terminals/sessions).";
        }
        return "Windows setx returned codes key=" + keyCode + ", model=" + modelCode + ".";
    }

    private static String installUnixLike(String key, String model) throws IOException {
        String shell = System.getenv().getOrDefault("SHELL", "");
        String fileName = shell.endsWith("zsh") ? ".zshrc" : ".bash_profile";
        Path profile = Path.of(System.getProperty("user.home"), fileName);

        String keyLine = "export OPENAI_KEY=\"" + key.replace("\"", "\\\"") + "\"";
        String modelLine = "export OPENAI_MODEL=\"" + model.replace("\"", "\\\"") + "\"";
        List<String> lines = Files.exists(profile) ? Files.readAllLines(profile) : List.of();
        boolean replacedKey = false;
        boolean replacedModel = false;
        StringBuilder newContent = new StringBuilder();
        for (String existing : lines) {
            if (existing.startsWith("export OPENAI_KEY=")) {
                newContent.append(keyLine).append(System.lineSeparator());
                replacedKey = true;
            } else if (existing.startsWith("export OPENAI_MODEL=")) {
                newContent.append(modelLine).append(System.lineSeparator());
                replacedModel = true;
            } else {
                newContent.append(existing).append(System.lineSeparator());
            }
        }

        if (!replacedKey) {
            if (!lines.isEmpty()) {
                newContent.append(System.lineSeparator());
            }
            newContent.append(keyLine).append(System.lineSeparator());
        }

        if (!replacedModel) {
            newContent.append(modelLine).append(System.lineSeparator());
        }

        Files.writeString(profile, newContent.toString());
        return "OPENAI_KEY and OPENAI_MODEL added to " + profile.getFileName() + " (effective in new shell sessions).";
    }

    private static String clearWindows() throws IOException, InterruptedException {
        Process keyProcess = new ProcessBuilder("cmd", "/c", "setx", "OPENAI_KEY", "").start();
        int keyCode = keyProcess.waitFor();
        Process modelProcess = new ProcessBuilder("cmd", "/c", "setx", "OPENAI_MODEL", "").start();
        int modelCode = modelProcess.waitFor();
        if (keyCode == 0 && modelCode == 0) {
            return "OPENAI_KEY and OPENAI_MODEL cleared from Windows user profile (new terminals/sessions).";
        }
        return "Windows setx returned codes key=" + keyCode + ", model=" + modelCode + ".";
    }

    private static String clearUnixLike() throws IOException {
        String shell = System.getenv().getOrDefault("SHELL", "");
        String fileName = shell.endsWith("zsh") ? ".zshrc" : ".bash_profile";
        Path profile = Path.of(System.getProperty("user.home"), fileName);
        if (!Files.exists(profile)) {
            return "No shell profile found; nothing to clear.";
        }

        List<String> lines = Files.readAllLines(profile);
        StringBuilder newContent = new StringBuilder();
        for (String existing : lines) {
            if (existing.startsWith("export OPENAI_KEY=") || existing.startsWith("export OPENAI_MODEL=")) {
                continue;
            }
            newContent.append(existing).append(System.lineSeparator());
        }

        Files.writeString(profile, newContent.toString());
        return "OPENAI_KEY and OPENAI_MODEL removed from " + profile.getFileName() + " (effective in new shell sessions).";
    }
}
