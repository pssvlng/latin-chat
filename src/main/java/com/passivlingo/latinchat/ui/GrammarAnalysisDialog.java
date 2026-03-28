package com.passivlingo.latinchat.ui;

import com.passivlingo.latinchat.model.GrammarAnalysisResult;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebView;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class GrammarAnalysisDialog {
    private static final int MAX_RENDERED_SENTENCE_CONTEXT_WORDS = 70;
    private static final Pattern WORD_PATTERN = Pattern.compile("\\p{L}[\\p{L}\\p{M}\\-]*");

    private final Stage stage;
    private final WebView contentView = new WebView();
    private final Label titleLabel = new Label("Grammatical Analysis");
    private final String selectedText;
    private final String sourceLanguageName;

    public GrammarAnalysisDialog(Stage owner, String selectedText, String sourceLanguageName) {
        this.selectedText = selectedText == null ? "" : selectedText.trim();
        this.sourceLanguageName = sourceLanguageName == null || sourceLanguageName.isBlank() ? "Source" : sourceLanguageName.trim();
        this.stage = new Stage();
        this.stage.initOwner(owner);
        this.stage.initModality(Modality.APPLICATION_MODAL);
        this.stage.setTitle("Analyze");

        titleLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: 700;");

        Button close = new Button("Close");
        close.setOnAction(event -> stage.close());

        HBox top = new HBox(titleLabel);
        top.setPadding(new Insets(10, 12, 8, 12));
        top.setAlignment(Pos.CENTER_LEFT);

        HBox bottom = new HBox(close);
        bottom.setPadding(new Insets(8, 12, 12, 12));
        bottom.setAlignment(Pos.CENTER_RIGHT);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setTop(top);
        root.setCenter(contentView);
        root.setBottom(bottom);

        Scene scene = new Scene(root, 980, 760);
        scene.getStylesheets().add(ChatTheme.stylesheetUri());
        stage.setScene(scene);
    }

    public void showLoading() {
        contentView.getEngine().loadContent(baseHtml("<p class='muted'>Analyzing selected text...</p>"));
    }

    public void showResult(GrammarAnalysisResult result) {
        GrammarAnalysisResult normalized = result == null
                ? GrammarAnalysisResult.error("No analysis result returned.")
                : result.normalized();

        if (!normalized.isSuccess()) {
            showError(normalized.errorMessage().isBlank() ? "Analysis failed." : normalized.errorMessage());
            return;
        }

        StringBuilder html = new StringBuilder();
        html.append("<section>");
        html.append("<h2>Selected Text</h2>");
        html.append("<p>").append(escape(selectedText)).append("</p>");
        html.append("</section>");

        List<GrammarAnalysisResult.SentencePair> renderableSentencePairs = normalized.sentencePairs().stream()
                .filter(pair -> wordCount(pair.latin()) <= MAX_RENDERED_SENTENCE_CONTEXT_WORDS)
                .toList();

        if (!renderableSentencePairs.isEmpty()) {
            html.append("<section>");
            html.append("<h2>Sentence Context</h2>");
            for (GrammarAnalysisResult.SentencePair pair : renderableSentencePairs) {
                html.append("<div class='sentence-grid'>");
                html.append("<div><h3>").append(escape(sourceLanguageName)).append("</h3><p>")
                    .append(escape(pair.latin()))
                        .append("</p></div>");
                html.append("<div><h3>English</h3><p>")
                        .append(escape(pair.english()))
                        .append("</p></div>");
                html.append("</div>");
            }
            html.append("</section>");
        }

        html.append("<section>");
        html.append("<h2>Word Analysis</h2>");
        for (GrammarAnalysisResult.WordAnalysis word : normalized.words()) {
            html.append(renderWordCard(word));
        }
        html.append("</section>");

        contentView.getEngine().loadContent(baseHtml(html.toString()));
    }

    public void showError(String message) {
        contentView.getEngine().loadContent(baseHtml("<p class='error'>" + escape(message) + "</p>"));
    }

    public void show() {
        stage.showAndWait();
    }

    private String renderWordCard(GrammarAnalysisResult.WordAnalysis word) {
        String pos = word.partOfSpeech().toLowerCase(Locale.ROOT);

        StringBuilder html = new StringBuilder();
        html.append("<article class='word-card'>");
        html.append("<h3>").append(escape(word.selectedWord())).append("</h3>");
        html.append("<p><strong>Lemma:</strong> ").append(escape(word.lemma())).append("</p>");
        html.append("<p><strong>Part of speech:</strong> ").append(escape(word.partOfSpeech())).append("</p>");
        html.append("<p><strong>Morphology:</strong> ").append(escape(word.morphology())).append("</p>");
        html.append("<p><strong>Base forms:</strong> ").append(escape(word.baseForms())).append("</p>");
        html.append("<p><strong>Context translation:</strong> ").append(escape(word.contextTranslation())).append("</p>");

        if (!word.notes().isEmpty()) {
            html.append("<p><strong>Notes:</strong></p><ul>");
            for (String note : word.notes()) {
                html.append("<li>").append(escape(note)).append("</li>");
            }
            html.append("</ul>");
        }

        html.append("<p class='template'><strong>Template:</strong> ")
                .append(escape(templateLabel(pos)))
                .append("</p>");

        if (isSingleLexicalWord(selectedText)) {
            GrammarAnalysisResult.TableData table = word.detailedTable() == null
                    ? word.fallbackTable()
                    : word.detailedTable().normalized();
            html.append(renderTable(table));
        }

        if (!word.alternatives().isEmpty()) {
            html.append("<p><strong>Alternatives:</strong></p><ul>");
            for (String alt : word.alternatives()) {
                html.append("<li>").append(escape(alt)).append("</li>");
            }
            html.append("</ul>");
        }

        html.append("</article>");
        return html.toString();
    }

    private String renderTable(GrammarAnalysisResult.TableData table) {
        StringBuilder html = new StringBuilder();
        html.append("<div class='table-wrap'>");
        if (!table.title().isBlank()) {
            html.append("<h4>").append(escape(table.title())).append("</h4>");
        }
        html.append("<table><thead><tr>");
        for (String header : table.headers()) {
            html.append("<th>").append(escape(header)).append("</th>");
        }
        html.append("</tr></thead><tbody>");
        for (List<String> row : table.rows()) {
            html.append("<tr>");
            for (String cell : row) {
                html.append("<td>").append(escape(cell)).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</tbody></table></div>");
        return html.toString();
    }

    private static String templateLabel(String pos) {
        if (pos.contains("verb") || pos.contains("participle")) {
            return "Verb/participle template (principal parts, tense, mood, voice, person/number).";
        }
        if (pos.contains("noun") || pos.contains("adjective") || pos.contains("pronoun")) {
            return "Nominal template (case, number, gender, declension and lemma).";
        }
        if (pos.contains("adverb")) {
            return "Adverb template (degree, lexical base, syntactic role).";
        }
        if (pos.contains("preposition") || pos.contains("conjunction") || pos.contains("particle")) {
            return "Function-word template (governed case and contextual meaning).";
        }
        return "General morphology template (lemma, role, and context meaning).";
    }

    private String withFallbackHighlights(String highlightedHtml, String plainText) {
        String highlighted = highlightedHtml == null ? "" : highlightedHtml.trim();
        if (!highlighted.isBlank()) {
            return highlighted;
        }
        String base = plainText == null ? "" : plainText;
        return highlightSelection(base, selectedText);
    }

    private static String highlightSelection(String text, String selection) {
        String escaped = escape(text);
        if (selection == null || selection.isBlank()) {
            return escaped;
        }
        String[] tokens = WORD_PATTERN.matcher(selection).results()
                .map(match -> match.group())
                .distinct()
                .toArray(String[]::new);

        String highlighted = escaped;
        for (String token : tokens) {
            highlighted = highlighted.replaceAll("(?i)\\b" + Pattern.quote(token) + "\\b", "<mark>$0</mark>");
        }
        return highlighted;
    }

    private static boolean isSingleLexicalWord(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        long count = WORD_PATTERN.matcher(text).results().count();
        return count == 1;
    }

    private static long wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return WORD_PATTERN.matcher(text.trim()).results().count();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String baseHtml(String body) {
        return """
                <html>
                <head>
                  <style>
                                        body { font-family: Inter, 'SF Pro Text', 'Segoe UI', sans-serif; color: #dbe4f0; margin: 0; padding: 14px; background: #0b1326; }
                                        h2 { margin: 8px 0 8px; color: #93c5fd; font-size: 18px; }
                                        h3 { margin: 0 0 6px; color: #eef4ff; }
                    h4 { margin: 10px 0 8px; }
                    p { margin: 4px 0 8px; }
                                        .muted { color: #9caec8; }
                                        .error { color: #fca5a5; font-weight: 600; }
                    section { margin-bottom: 16px; }
                    .sentence-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 12px; }
                                        .sentence-grid > div { background: #111c34; border: 1px solid #2b3a54; border-radius: 8px; padding: 10px; }
                                        .word-card { background: #111c34; border: 1px solid #2b3a54; border-radius: 8px; padding: 10px; margin-bottom: 10px; }
                                        .template { color: #b9c8dd; }
                                        mark { background: #facc15; color: #111827; padding: 0 2px; border-radius: 2px; }
                    ul { margin: 6px 0 8px 22px; }
                                        table { border-collapse: collapse; width: 100%; background: #0f172a; }
                                        th, td { border: 1px solid #2b3a54; padding: 6px 8px; text-align: left; vertical-align: top; }
                                        th { background: #1a2740; }
                    .table-wrap { margin-top: 10px; }
                  </style>
                </head>
                <body>
                """ + body + "</body></html>";
    }
}
