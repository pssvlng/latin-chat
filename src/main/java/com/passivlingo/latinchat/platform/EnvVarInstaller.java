package com.passivlingo.latinchat.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class EnvVarInstaller {
    private static final String DEFAULT_MODEL = "gpt-5.4-mini";
    private static final String DEFAULT_LANGUAGES = "la";

    private EnvVarInstaller() {
    }

    public static String installOpenAiKey(String key) {
        return installOpenAiSettings(key, null);
    }

    public static String installOpenAiSettings(String key, String model) {
        return installOpenAiSettings(key, model, null);
    }

    public static String installOpenAiSettings(String key, String model, String languagesCsv) {
        String effectiveModel = model == null || model.isBlank() ? DEFAULT_MODEL : model.trim();
        String effectiveLanguages = languagesCsv == null || languagesCsv.isBlank() ? DEFAULT_LANGUAGES : languagesCsv.trim();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                return installWindows(key, effectiveModel, effectiveLanguages);
            }
            return installUnixLike(key, effectiveModel, effectiveLanguages);
        } catch (Exception ex) {
            return "Could not persist environment variable: " + ex.getMessage();
        }
    }

    public static String installLanguageConfig(String languagesCsv) {
        String effectiveLanguages = languagesCsv == null || languagesCsv.isBlank() ? DEFAULT_LANGUAGES : languagesCsv.trim();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                Process langProcess = new ProcessBuilder("cmd", "/c", "setx", "OPENAI_LANG", effectiveLanguages).start();
                int langCode = langProcess.waitFor();
                if (langCode == 0) {
                    return "OPENAI_LANG set for Windows user profile (new terminals/sessions).";
                }
                return "Windows setx returned code OPENAI_LANG=" + langCode + ".";
            }
            return installUnixLike(null, null, effectiveLanguages);
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

    private static String installWindows(String key, String model, String languages) throws IOException, InterruptedException {
        Process keyProcess = new ProcessBuilder("cmd", "/c", "setx", "OPENAI_KEY", key).start();
        int keyCode = keyProcess.waitFor();
        Process modelProcess = new ProcessBuilder("cmd", "/c", "setx", "OPENAI_MODEL", model).start();
        int modelCode = modelProcess.waitFor();
        Process langProcess = new ProcessBuilder("cmd", "/c", "setx", "OPENAI_LANG", languages).start();
        int langCode = langProcess.waitFor();
        if (keyCode == 0 && modelCode == 0 && langCode == 0) {
            return "OPENAI_KEY, OPENAI_MODEL and OPENAI_LANG set for Windows user profile (new terminals/sessions).";
        }
        return "Windows setx returned codes key=" + keyCode + ", model=" + modelCode + ", lang=" + langCode + ".";
    }

    private static String installUnixLike(String key, String model, String languages) throws IOException {
        String shell = System.getenv().getOrDefault("SHELL", "");
        String fileName = shell.endsWith("zsh") ? ".zshrc" : ".bash_profile";
        Path profile = Path.of(System.getProperty("user.home"), fileName);

        String keyLine = key == null ? null : "export OPENAI_KEY=\"" + key.replace("\"", "\\\"") + "\"";
        String modelLine = model == null ? null : "export OPENAI_MODEL=\"" + model.replace("\"", "\\\"") + "\"";
        String langLine = "export OPENAI_LANG=\"" + languages.replace("\"", "\\\"") + "\"";
        List<String> lines = Files.exists(profile) ? Files.readAllLines(profile) : List.of();
        boolean replacedKey = keyLine == null;
        boolean replacedModel = modelLine == null;
        boolean replacedLang = false;
        StringBuilder newContent = new StringBuilder();
        for (String existing : lines) {
            if (keyLine != null && existing.startsWith("export OPENAI_KEY=")) {
                newContent.append(keyLine).append(System.lineSeparator());
                replacedKey = true;
            } else if (modelLine != null && existing.startsWith("export OPENAI_MODEL=")) {
                newContent.append(modelLine).append(System.lineSeparator());
                replacedModel = true;
            } else if (existing.startsWith("export OPENAI_LANG=")) {
                newContent.append(langLine).append(System.lineSeparator());
                replacedLang = true;
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

        if (!replacedLang) {
            newContent.append(langLine).append(System.lineSeparator());
        }

        Files.writeString(profile, newContent.toString());
        if (keyLine == null && modelLine == null) {
            return "OPENAI_LANG added to " + profile.getFileName() + " (effective in new shell sessions).";
        }
        return "OPENAI_KEY, OPENAI_MODEL and OPENAI_LANG added to " + profile.getFileName() + " (effective in new shell sessions).";
    }

    private static String clearWindows() throws IOException, InterruptedException {
        Process keyProcess = new ProcessBuilder("cmd", "/c", "setx", "OPENAI_KEY", "").start();
        int keyCode = keyProcess.waitFor();
        Process modelProcess = new ProcessBuilder("cmd", "/c", "setx", "OPENAI_MODEL", "").start();
        int modelCode = modelProcess.waitFor();
        Process langProcess = new ProcessBuilder("cmd", "/c", "setx", "OPENAI_LANG", "").start();
        int langCode = langProcess.waitFor();
        if (keyCode == 0 && modelCode == 0 && langCode == 0) {
            return "OPENAI_KEY, OPENAI_MODEL and OPENAI_LANG cleared from Windows user profile (new terminals/sessions).";
        }
        return "Windows setx returned codes key=" + keyCode + ", model=" + modelCode + ", lang=" + langCode + ".";
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
            if (existing.startsWith("export OPENAI_KEY=")
                || existing.startsWith("export OPENAI_MODEL=")
                || existing.startsWith("export OPENAI_LANG=")) {
                continue;
            }
            newContent.append(existing).append(System.lineSeparator());
        }

        Files.writeString(profile, newContent.toString());
        return "OPENAI_KEY, OPENAI_MODEL and OPENAI_LANG removed from " + profile.getFileName() + " (effective in new shell sessions).";
    }
}
