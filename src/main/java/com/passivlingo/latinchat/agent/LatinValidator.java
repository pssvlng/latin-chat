package com.passivlingo.latinchat.agent;

import java.util.Locale;
import java.util.Set;

public final class LatinValidator {
    private static final Set<String> COMMON_NON_LATIN_MARKERS = Set.of(
            " the ", " and ", " with ", " from ", " are ", " is ", "you ", "that ",
            " und ", " der ", " die ", " das ", " ich ",
            " el ", " la ", " los ", " las ", " para ", " que ",
            " le ", " les ", " des ", " est ", " avec ");

    private LatinValidator() {
    }

    public static boolean looksLatin(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        String normalized = " " + text.toLowerCase(Locale.ROOT).replace('\n', ' ') + " ";
        int matches = 0;
        for (String marker : COMMON_NON_LATIN_MARKERS) {
            if (normalized.contains(marker)) {
                matches++;
            }
        }

        return matches <= 1;
    }
}
