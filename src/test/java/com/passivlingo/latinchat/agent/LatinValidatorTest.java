package com.passivlingo.latinchat.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatinValidatorTest {

    @Test
    void acceptsLikelyLatinText() {
        String text = "Salve! Quid agis hodie? Scribam responsum Latine.";
        assertTrue(LatinValidator.looksLatin(text));
    }

    @Test
    void rejectsEmptyOrNull() {
        assertFalse(LatinValidator.looksLatin(null));
        assertFalse(LatinValidator.looksLatin("   "));
    }

    @Test
    void rejectsClearlyNonLatinText() {
        String text = "This is an English answer and it is not in Latin.";
        assertFalse(LatinValidator.looksLatin(text));
    }
}
