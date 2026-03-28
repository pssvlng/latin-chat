package com.passivlingo.latinchat.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record LanguageConfig(String code, String baseCode, String displayName, boolean nonRomanScript, boolean transcribed) {
    private static final String TRANSCRIBED_SUFFIX = "-la";

    private static final Map<String, String> BASE_LANGUAGE_NAMES;
    private static final Set<String> NON_ROMAN_LANGUAGES;

    static {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("la", "Latin");
        names.put("en", "English");
        names.put("af", "Afrikaans");
        names.put("de", "German");
        names.put("fr", "French");
        names.put("es", "Spanish");
        names.put("it", "Italian");
        names.put("pt", "Portuguese");
        names.put("nl", "Dutch");
        names.put("sv", "Swedish");
        names.put("no", "Norwegian");
        names.put("da", "Danish");
        names.put("fi", "Finnish");
        names.put("pl", "Polish");
        names.put("cs", "Czech");
        names.put("sk", "Slovak");
        names.put("sl", "Slovenian");
        names.put("hr", "Croatian");
        names.put("ro", "Romanian");
        names.put("hu", "Hungarian");
        names.put("tr", "Turkish");
        names.put("id", "Indonesian");
        names.put("ms", "Malay");
        names.put("vi", "Vietnamese");
        names.put("tl", "Filipino");
        names.put("sw", "Swahili");
        names.put("xh", "Xhosa");
        names.put("zu", "Zulu");
        names.put("ar", "Arabic");
        names.put("fa", "Persian");
        names.put("he", "Hebrew");
        names.put("ur", "Urdu");
        names.put("ru", "Russian");
        names.put("uk", "Ukrainian");
        names.put("bg", "Bulgarian");
        names.put("el", "Greek");
        names.put("sr", "Serbian");
        names.put("hi", "Hindi");
        names.put("bn", "Bengali");
        names.put("pa", "Punjabi");
        names.put("mr", "Marathi");
        names.put("gu", "Gujarati");
        names.put("ta", "Tamil");
        names.put("te", "Telugu");
        names.put("kn", "Kannada");
        names.put("ml", "Malayalam");
        names.put("th", "Thai");
        names.put("zh", "Chinese");
        names.put("ja", "Japanese");
        names.put("ko", "Korean");
        BASE_LANGUAGE_NAMES = Collections.unmodifiableMap(names);

        NON_ROMAN_LANGUAGES = Set.of(
                "ar", "fa", "he", "ur", "ru", "uk", "bg", "el", "sr",
                "hi", "bn", "pa", "mr", "gu", "ta", "te", "kn", "ml",
                "th", "zh", "ja", "ko"
        );
    }

    public String codeLabel() {
        return code.toUpperCase(Locale.ROOT);
    }

    public static List<LanguageConfig> dialogOptions() {
        List<LanguageConfig> result = new ArrayList<>();
        for (String baseCode : BASE_LANGUAGE_NAMES.keySet()) {
            fromCode(baseCode).ifPresent(result::add);
            if (NON_ROMAN_LANGUAGES.contains(baseCode)) {
                fromCode(baseCode + TRANSCRIBED_SUFFIX).ifPresent(result::add);
            }
        }
        return result;
    }

    public static List<LanguageConfig> configuredLanguages(String csv) {
        LinkedHashSet<LanguageConfig> result = new LinkedHashSet<>();
        for (String token : splitCodes(csv)) {
            fromCode(token).ifPresent(result::add);
        }
        if (result.isEmpty()) {
            result.add(defaultLanguage());
        }
        return new ArrayList<>(result);
    }

    public static String toCsv(List<LanguageConfig> languages) {
        if (languages == null || languages.isEmpty()) {
            return defaultLanguage().code();
        }
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (LanguageConfig language : languages) {
            if (language == null) {
                continue;
            }
            fromCode(language.code()).ifPresent(valid -> codes.add(valid.code()));
        }
        if (codes.isEmpty()) {
            codes.add(defaultLanguage().code());
        }
        return String.join(",", codes);
    }

    public static LanguageConfig defaultLanguage() {
        return fromCode("la").orElse(new LanguageConfig("la", "la", "Latin", false, false));
    }

    public static Optional<LanguageConfig> fromCode(String rawCode) {
        String normalized = normalizeCode(rawCode);
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        boolean transcribed = normalized.endsWith(TRANSCRIBED_SUFFIX);
        String base = transcribed ? normalized.substring(0, normalized.length() - TRANSCRIBED_SUFFIX.length()) : normalized;
        String name = BASE_LANGUAGE_NAMES.get(base);
        if (name == null) {
            return Optional.empty();
        }

        boolean nonRoman = NON_ROMAN_LANGUAGES.contains(base);
        if (transcribed && !nonRoman) {
            transcribed = false;
            normalized = base;
        }

        String display = transcribed ? name + " Transcribed" : name;
        return Optional.of(new LanguageConfig(normalized, base, display, nonRoman, transcribed));
    }

    private static List<String> splitCodes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(LanguageConfig::normalizeCode)
                .filter(token -> !token.isBlank())
                .toList();
    }

    private static String normalizeCode(String code) {
        if (code == null) {
            return "";
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 2) {
            return "";
        }
        return normalized;
    }
}