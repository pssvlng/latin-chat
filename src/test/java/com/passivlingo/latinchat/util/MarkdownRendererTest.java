package com.passivlingo.latinchat.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkdownRendererTest {

    @Test
    void rendersBasicMarkdown() {
        MarkdownRenderer renderer = new MarkdownRenderer();

        String html = renderer.renderHtml("# Titulus\n\n**fortis**");

        assertTrue(html.contains("<h1"));
        assertTrue(html.contains("Titulus"));
        assertTrue(html.contains("<strong>fortis</strong>"));
    }
}
