package com.passivlingo.latinchat.ui;

import com.passivlingo.latinchat.agent.GrammarAgentGraph;
import com.passivlingo.latinchat.agent.LatinAgentGraph;
import com.passivlingo.latinchat.config.ApiKeyStore;
import com.passivlingo.latinchat.config.AppPaths;
import com.passivlingo.latinchat.data.ChatService;
import com.passivlingo.latinchat.model.Conversation;
import com.passivlingo.latinchat.model.ConversationWebTab;
import com.passivlingo.latinchat.model.GrammarAnalysisResult;
import com.passivlingo.latinchat.model.Message;
import com.passivlingo.latinchat.platform.EnvVarInstaller;
import com.passivlingo.latinchat.util.MarkdownRenderer;
import javafx.animation.PauseTransition;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import javafx.util.Duration;

import java.sql.SQLException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

public final class MainWindow {
    private static final int CONTEXT_MESSAGE_LIMIT = 24;
    private static final int RENDER_MESSAGE_LIMIT = 120;
    private static final int MAX_SENTENCE_CONTEXT_WORDS = 70;
    private static final String DEFAULT_OPENAI_MODEL = "gpt-5.4-mini";
    private static final DateTimeFormatter CHAT_TITLE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    private static final Pattern WORD_PATTERN = Pattern.compile("\\p{L}[\\p{L}\\p{M}\\-]*");
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+");
    private static final String THEME_STYLESHEET = ChatTheme.stylesheetUri();

    private final Stage stage;
    private final ChatService chatService;
    private final LatinAgentGraph agentGraph;
    private final GrammarAgentGraph grammarAgentGraph;
    private final ApiKeyStore keyStore;
    private final MarkdownRenderer markdownRenderer;
    private final HostServices hostServices;

    private final ListView<Conversation> conversationsList = new ListView<>();
    private final TabPane contentTabs = new TabPane();
    private final Tab chatTab = new Tab("Chat");
    private final WebView transcriptView = new WebView();
    private final TextArea inputArea = new TextArea();
    private final Button sendButton = new Button("Send");
    private final Label copyToast = new Label();
    private final PauseTransition copyToastTimer = new PauseTransition(Duration.seconds(1.2));
    private final Map<Long, List<WebTab>> webTabsByConversation = new HashMap<>();
    private ContextMenu activeLinkMenu;
    private Long activeConversationId;

    private String runtimeApiKey;
    private String runtimeModelName;
    private boolean ignoreEnvironmentKey;
    private boolean ignoreEnvironmentModel;

    public MainWindow(Stage stage,
                      ChatService chatService,
                      LatinAgentGraph agentGraph,
                      GrammarAgentGraph grammarAgentGraph,
                      ApiKeyStore keyStore,
                      MarkdownRenderer markdownRenderer,
                      String initialApiKey,
                      HostServices hostServices) {
        this.stage = stage;
        this.chatService = chatService;
        this.agentGraph = agentGraph;
        this.grammarAgentGraph = grammarAgentGraph;
        this.keyStore = keyStore;
        this.markdownRenderer = markdownRenderer;
        this.runtimeApiKey = initialApiKey;
        this.runtimeModelName = resolveCurrentModelName();
        this.ignoreEnvironmentKey = false;
        this.ignoreEnvironmentModel = false;
        System.setProperty("OPENAI_MODEL", this.runtimeModelName);
        this.hostServices = hostServices;
    }

    public void show() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        BorderPane mainContent = new BorderPane();
        mainContent.setCenter(createCenterPane());
        mainContent.setBottom(createComposer());

        root.setTop(createMenuBar());
        root.setLeft(createSidebar());
        root.setCenter(mainContent);

        copyToast.setVisible(false);
        copyToast.setManaged(false);
        copyToast.setMouseTransparent(true);
        copyToast.setStyle("-fx-background-color: rgba(20, 25, 34, 0.92);"
            + "-fx-text-fill: white;"
            + "-fx-padding: 8 12;"
            + "-fx-background-radius: 8;"
            + "-fx-font-size: 12px;");

        StackPane rootStack = new StackPane(root, copyToast);
        StackPane.setAlignment(copyToast, Pos.BOTTOM_CENTER);
        StackPane.setMargin(copyToast, new Insets(0, 0, 18, 0));

        Scene scene = new Scene(rootStack, 1400, 900);
        scene.getStylesheets().add(THEME_STYLESHEET);

        stage.setScene(scene);
        stage.setTitle("SPQR Latin Chat");
        stage.setOnCloseRequest(e -> disposeAllWebTabs());
        stage.show();

        copyToastTimer.setOnFinished(e -> copyToast.setVisible(false));

        setupEvents();
        refreshConversations();
    }

    private MenuBar createMenuBar() {
        Menu fileMenu = new Menu("File");
        Menu helpMenu = new Menu("Help");

        MenuItem setKey = new MenuItem("Set OpenAI Key and Model");
        MenuItem clearKey = new MenuItem("Clear OpenAI Key");
        MenuItem exit = new MenuItem("Exit");
        MenuItem databaseLocation = new MenuItem("Database Location");
        MenuItem about = new MenuItem("About");

        setKey.setOnAction(e -> setOpenAiKey());
        clearKey.setOnAction(e -> clearOpenAiKey());
        exit.setOnAction(e -> Platform.exit());
        databaseLocation.setOnAction(e -> showDatabaseLocation());
        about.setOnAction(e -> showAboutDialog());

        fileMenu.getItems().addAll(setKey, clearKey, new SeparatorMenuItem(), exit);
        helpMenu.getItems().addAll(databaseLocation, new SeparatorMenuItem(), about);
        return new MenuBar(fileMenu, helpMenu);
    }

    private void showDatabaseLocation() {
        Path dbPath = AppPaths.appHome().resolve("latin_chat.db").toAbsolutePath();
        Alert alert = new Alert(Alert.AlertType.INFORMATION, dbPath.toString(), ButtonType.OK);
        alert.setTitle("Database Location");
        alert.setHeaderText("SQLite Database File");
        applyDialogTheme(alert);
        alert.showAndWait();
    }

    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.OK);
        alert.setTitle("About");
        alert.setHeaderText("SPQR Latin Chat");

        ImageView icon = new ImageView(AppIconFactory.createSpqrIcon(64));
        icon.setFitWidth(64);
        icon.setFitHeight(64);
        alert.getDialogPane().setGraphic(icon);

        Label description = new Label(
                "SPQR Latin Chat helps you practice Latin conversations, analyze grammar in context, and translate selected text."
        );
        description.setWrapText(true);

        Label designNote = new Label(
                "All images and design elements were created manually or with AI assistance."
        );
        designNote.setWrapText(true);
        designNote.setStyle("-fx-text-fill: #9caec8;");

        VBox content = new VBox(10, description, designNote);
        alert.getDialogPane().setContent(content);
        applyDialogTheme(alert);
        alert.showAndWait();
    }

    private Pane createSidebar() {
        Label title = new Label("Conversations");
        Button newChat = new Button("+ New Chat");
        Button deleteChat = new Button("Delete");
        Button deleteAllChats = new Button("Delete All");

        newChat.setMaxWidth(Double.MAX_VALUE);
        deleteChat.setMaxWidth(Double.MAX_VALUE);
        deleteAllChats.setMaxWidth(Double.MAX_VALUE);

        newChat.setOnAction(e -> createConversation());
        deleteChat.setOnAction(e -> deleteSelectedConversation());
        deleteAllChats.setOnAction(e -> deleteAllConversations());

        conversationsList.getStyleClass().add("conversation-list");

        conversationsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Conversation item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setTooltip(null);
                    return;
                }

                Label titleLabel = new Label(item.title());
                titleLabel.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(titleLabel, Priority.ALWAYS);

                if (item.includeWebSearch()) {
                    Label globe = new Label("🌐");
                    globe.setStyle("-fx-font-size: 13px;");
                    Tooltip.install(globe, new Tooltip("Web search is enabled for this chat"));

                    HBox row = new HBox(8, titleLabel, globe);
                    row.setAlignment(Pos.CENTER_LEFT);
                    setText(null);
                    setGraphic(row);
                    setTooltip(null);
                } else {
                    setText(null);
                    setGraphic(titleLabel);
                    setTooltip(null);
                }
            }
        });

        VBox box = new VBox(10, title, newChat, deleteChat, deleteAllChats, new Separator(Orientation.HORIZONTAL), conversationsList);
        box.setPadding(new Insets(12));
        box.setPrefWidth(280);
        box.getStyleClass().add("sidebar");
        VBox.setVgrow(conversationsList, Priority.ALWAYS);
        return box;
    }

    private Node createCenterPane() {
        transcriptView.setContextMenuEnabled(false);
        transcriptView.getEngine().loadContent(baseHtml("<p>Start a new conversation.</p>"));
        BorderPane chatPane = new BorderPane(transcriptView);
        chatPane.setPadding(new Insets(10));

        chatTab.setClosable(false);
        chatTab.setContent(chatPane);

        contentTabs.getTabs().setAll(chatTab);
        contentTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        return contentTabs;
    }

    private Pane createComposer() {
        inputArea.setWrapText(true);
        inputArea.setPrefRowCount(3);
        inputArea.getStyleClass().add("chat-input");

        sendButton.setDefaultButton(true);

        HBox actions = new HBox(sendButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(10, inputArea, actions);
        box.setPadding(new Insets(10));
        return box;
    }

    private void setupEvents() {
        conversationsList.getSelectionModel().selectedItemProperty().addListener((obs, oldItem, newItem) -> {
            if (newItem != null) {
                switchConversationTabs(newItem.id());
                refreshMessages(false, false, true);
            } else {
                switchConversationTabs(null);
                refreshMessages();
            }
        });
        sendButton.setOnAction(e -> sendMessage());

        transcriptView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                bindTranscriptEnhancements();
            }
        });

        transcriptView.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            MouseButton button = event.getButton();
            if (button != MouseButton.PRIMARY && button != MouseButton.SECONDARY) {
                return;
            }

            String href = hrefAtPoint(event.getX(), event.getY());
            if (href == null || href.isBlank()) {
                if (button == MouseButton.SECONDARY) {
                    String selectedText = selectedTranscriptText();
                    long selectedWordCount = lexicalWordCount(selectedText);
                    if (selectedWordCount == 1) {
                        event.consume();
                        showAnalyzeMenu(selectedText, event.getX(), event.getY());
                        return;
                    }
                    if (selectedWordCount > 1) {
                        event.consume();
                        showTranslateMenu(selectedText, event.getX(), event.getY());
                        return;
                    }
                }
                if (activeLinkMenu != null && activeLinkMenu.isShowing()) {
                    activeLinkMenu.hide();
                }
                return;
            }

            event.consume();
            new TranscriptBridge().showLinkMenu(href, event.getX(), event.getY());
        });

        inputArea.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
                sendMessage();
                event.consume();
            }
        });
    }

    private String selectedTranscriptText() {
        Object result = transcriptView.getEngine().executeScript(
                "window.getSelection ? window.getSelection().toString() : ''"
        );
        return result instanceof String value ? value.trim() : "";
    }

    private String transcriptPlainText() {
        Object result = transcriptView.getEngine().executeScript(
                "document && document.body ? document.body.innerText : ''"
        );
        if (!(result instanceof String value)) {
            return "";
        }
        String normalized = value.replace('\u00A0', ' ').trim();
        if (normalized.length() <= 8000) {
            return normalized;
        }
        return normalized.substring(0, 8000);
    }

        private String selectedSentenceContext(String selectedText) {
            if (selectedText == null || selectedText.isBlank()) {
                return "";
            }

            Object result = transcriptView.getEngine().executeScript("""
                            (() => {
                                const sel = window.getSelection ? window.getSelection() : null;
                                if (!sel || sel.rangeCount === 0 || sel.isCollapsed) return '';
                                const range = sel.getRangeAt(0);

                                const body = document && document.body ? document.body : null;
                                if (!body) return '';
                                const bodyText = body.innerText || '';
                                if (!bodyText) return '';

                                const preRange = document.createRange();
                                preRange.selectNodeContents(body);
                                preRange.setEnd(range.startContainer, range.startOffset);

                                const startIndex = preRange.toString().length;
                                const selectedText = range.toString();
                                if (!selectedText || !selectedText.trim()) return '';

                                const boundary = (ch) => ch === '.' || ch === '!' || ch === '?' || ch === '\n';

                                let s = startIndex;
                                while (s > 0 && !boundary(bodyText[s - 1])) {
                                    s--;
                                }

                                let e = startIndex + selectedText.length;
                                if (e < 0) e = 0;
                                if (e > bodyText.length) e = bodyText.length;
                                while (e < bodyText.length && !boundary(bodyText[e])) {
                                    e++;
                                }
                                if (e < bodyText.length) {
                                    e++;
                                }

                                return bodyText.substring(s, Math.min(e, bodyText.length)).replace(/\s+/g, ' ').trim();
                            })();
                            """);

            return result instanceof String value ? value.trim() : "";
        }

    private void showAnalyzeMenu(String selectedText, double x, double y) {
        if (selectedText == null || selectedText.isBlank()) {
            return;
        }

        if (activeLinkMenu != null) {
            activeLinkMenu.hide();
        }

        MenuItem analyze = new MenuItem("Analyze");
        analyze.setOnAction(event -> Platform.runLater(() -> beginGrammarAnalysis(selectedText)));

        ContextMenu menu = new ContextMenu(analyze);
        menu.setAutoHide(true);
        activeLinkMenu = menu;

        Point2D p = transcriptView.localToScreen(x, y);
        if (p != null) {
            menu.show(transcriptView, p.getX(), p.getY());
        } else {
            menu.show(transcriptView, stage.getX() + stage.getWidth() / 2.0, stage.getY() + stage.getHeight() / 2.0);
        }
    }

    private void showTranslateMenu(String selectedText, double x, double y) {
        if (selectedText == null || selectedText.isBlank()) {
            return;
        }

        if (activeLinkMenu != null) {
            activeLinkMenu.hide();
        }

        MenuItem translate = new MenuItem("Translate");
        translate.setOnAction(event -> Platform.runLater(() -> beginSelectionTranslation(selectedText)));

        ContextMenu menu = new ContextMenu(translate);
        menu.setAutoHide(true);
        activeLinkMenu = menu;

        Point2D p = transcriptView.localToScreen(x, y);
        if (p != null) {
            menu.show(transcriptView, p.getX(), p.getY());
        } else {
            menu.show(transcriptView, stage.getX() + stage.getWidth() / 2.0, stage.getY() + stage.getHeight() / 2.0);
        }
    }

    private void beginSelectionTranslation(String selectedText) {
        String cleanedSelection = selectedText == null ? "" : selectedText.trim();
        if (lexicalWordCount(cleanedSelection) <= 1) {
            info("Multi-word selection required", "Select more than one word before using Translate.");
            return;
        }

        if (!ensureOpenAiSettingsConfigured()) {
            return;
        }
        String key = resolvedKey();

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Translate");
        dialog.setHeaderText("Selected Text Translation");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        String analysisPopupFont = "-fx-font-family: Inter, 'SF Pro Text', 'Segoe UI', sans-serif; -fx-font-size: 16px;";

        Label latinLabel = new Label("Selected Text");
        latinLabel.setStyle(analysisPopupFont);
        TextArea latinArea = new TextArea(cleanedSelection);
        latinArea.setEditable(false);
        latinArea.setWrapText(true);
        latinArea.setPrefRowCount(8);
        latinArea.setPrefHeight(240);
        latinArea.setMaxHeight(Double.MAX_VALUE);
        latinArea.setStyle(analysisPopupFont);

        Label englishLabel = new Label("English Translation");
        englishLabel.setStyle(analysisPopupFont);
        TextArea englishArea = new TextArea("Translating...");
        englishArea.setEditable(false);
        englishArea.setWrapText(true);
        englishArea.setPrefRowCount(8);
        englishArea.setPrefHeight(240);
        englishArea.setMaxHeight(Double.MAX_VALUE);
        englishArea.setStyle(analysisPopupFont);

        GridPane sideBySide = new GridPane();
        sideBySide.setHgap(12);
        sideBySide.setVgap(6);

        ColumnConstraints leftColumn = new ColumnConstraints();
        leftColumn.setPercentWidth(50);
        leftColumn.setHgrow(Priority.ALWAYS);
        ColumnConstraints rightColumn = new ColumnConstraints();
        rightColumn.setPercentWidth(50);
        rightColumn.setHgrow(Priority.ALWAYS);
        sideBySide.getColumnConstraints().addAll(leftColumn, rightColumn);

        GridPane.setHgrow(latinArea, Priority.ALWAYS);
        GridPane.setVgrow(latinArea, Priority.ALWAYS);
        GridPane.setHgrow(englishArea, Priority.ALWAYS);
        GridPane.setVgrow(englishArea, Priority.ALWAYS);

        sideBySide.add(latinLabel, 0, 0);
        sideBySide.add(englishLabel, 1, 0);
        sideBySide.add(latinArea, 0, 1);
        sideBySide.add(englishArea, 1, 1);

        dialog.getDialogPane().setContent(sideBySide);
        dialog.getDialogPane().setPrefWidth(820);
        applyDialogTheme(dialog);

        CompletableFuture.supplyAsync(() -> {
            try {
                return grammarAgentGraph.translateSelection(key, cleanedSelection);
            } catch (Exception ex) {
                return "Translation failed: " + ex.getMessage();
            }
        }).thenAccept(translation -> Platform.runLater(() -> englishArea.setText(translation == null ? "" : translation.trim())));

        dialog.showAndWait();
    }

    private void beginGrammarAnalysis(String selectedText) {
        try {
            String cleanedSelection = selectedText == null ? "" : selectedText.trim();
            if (cleanedSelection.isBlank()) {
                return;
            }

            if (!isSingleLexicalWord(cleanedSelection)) {
                info("Single-word only", "Select exactly one Latin word before using Analyze.");
                return;
            }

            if (!ensureOpenAiSettingsConfigured()) {
                return;
            }
            String key = resolvedKey();

            String transcriptContext;
            try {
                transcriptContext = transcriptPlainText();
            } catch (Exception ex) {
                transcriptContext = "";
            }

            String sentenceContext;
            try {
                sentenceContext = selectedSentenceContext(cleanedSelection);
            } catch (Exception ex) {
                sentenceContext = "";
            }
            sentenceContext = sanitizeSentenceContext(sentenceContext);

            if (looksIncompleteSentenceContext(sentenceContext, cleanedSelection)) {
                sentenceContext = fallbackSentenceFromTranscript(cleanedSelection, transcriptContext);
                sentenceContext = sanitizeSentenceContext(sentenceContext);
            }

            String analysisContext;
            if (!sentenceContext.isBlank() && !transcriptContext.isBlank()) {
                analysisContext = "Sentence containing selected word:\n" + sentenceContext + "\n\nTranscript excerpt:\n" + transcriptContext;
            } else if (!sentenceContext.isBlank()) {
                analysisContext = "Sentence containing selected word:\n" + sentenceContext;
            } else if (!transcriptContext.isBlank()) {
                analysisContext = transcriptContext;
            } else {
                analysisContext = cleanedSelection;
            }

            GrammarAnalysisDialog dialog = new GrammarAnalysisDialog(stage, cleanedSelection);
            dialog.showLoading();

            CompletableFuture.supplyAsync(() -> {
                try {
                    return grammarAgentGraph.run(key, cleanedSelection, analysisContext, true);
                } catch (Exception ex) {
                    return GrammarAnalysisResult.error("Analysis failed: " + ex.getMessage());
                }
            }).thenAccept(result -> Platform.runLater(() -> dialog.showResult(result)));

            dialog.show();
        } catch (Exception ex) {
            error("Analysis failed", "Could not start analysis: " + ex.getMessage());
        }
    }

    private static boolean isSingleLexicalWord(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return lexicalWordCount(text) == 1;
    }

    private static long lexicalWordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return WORD_PATTERN.matcher(text.trim()).results().count();
    }

    private static boolean looksIncompleteSentenceContext(String sentence, String selectedWord) {
        String value = sentence == null ? "" : sentence.trim();
        if (value.isBlank()) {
            return true;
        }
        if (selectedWord != null && value.equalsIgnoreCase(selectedWord.trim())) {
            return true;
        }
        return WORD_PATTERN.matcher(value).results().count() < 4;
    }

    private static String sanitizeSentenceContext(String sentence) {
        String value = sentence == null ? "" : sentence.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
        if (value.isBlank()) {
            return "";
        }
        long words = WORD_PATTERN.matcher(value).results().count();
        if (words > MAX_SENTENCE_CONTEXT_WORDS) {
            return "";
        }
        return value;
    }

    private static String fallbackSentenceFromTranscript(String selectedWord, String transcript) {
        String word = selectedWord == null ? "" : selectedWord.trim();
        String text = transcript == null ? "" : transcript.replace('\u00A0', ' ').trim();
        if (word.isBlank() || text.isBlank()) {
            return "";
        }

        for (String candidate : SENTENCE_BOUNDARY.split(text)) {
            String normalized = candidate == null ? "" : candidate.trim();
            if (normalized.isBlank()) {
                continue;
            }
            Pattern containsWord = Pattern.compile("(?iu)\\b" + Pattern.quote(word) + "\\b");
            if (containsWord.matcher(normalized).find()) {
                return normalized;
            }
        }

        String lower = text.toLowerCase();
        int idx = lower.indexOf(word.toLowerCase());
        if (idx < 0) {
            return "";
        }

        int start = idx;
        while (start > 0) {
            char ch = text.charAt(start - 1);
            if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
                break;
            }
            start--;
        }

        int end = idx + word.length();
        while (end < text.length()) {
            char ch = text.charAt(end);
            if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
                end++;
                break;
            }
            end++;
        }

        return text.substring(Math.max(0, start), Math.min(end, text.length())).replaceAll("\\s+", " ").trim();
    }

    private String hrefAtPoint(double x, double y) {
        String script = """
                (() => {
                  const el = document.elementFromPoint(%s, %s);
                  const link = el && el.closest ? el.closest('a[href]') : null;
                  return link ? link.href : '';
                })();
                """.formatted(x, y);

        Object result = transcriptView.getEngine().executeScript(script);
        return result instanceof String value ? value : "";
    }

    private void refreshConversations() {
        try {
            List<Conversation> conversations = chatService.listConversations();
            conversationsList.setItems(FXCollections.observableArrayList(conversations));
            if (!conversations.isEmpty() && conversationsList.getSelectionModel().getSelectedItem() == null) {
                conversationsList.getSelectionModel().select(0);
            }
        } catch (SQLException ex) {
            error("Database error", ex.getMessage());
        }
    }

    private void refreshMessages() {
        refreshMessages(false, false, false);
    }

    private void refreshMessages(boolean scrollToLatestAssistant) {
        refreshMessages(scrollToLatestAssistant, false, false);
    }

    private void refreshMessages(boolean scrollToLatestAssistant,
                                 boolean showThinkingIndicator,
                                 boolean scrollToBottom) {
        Conversation selected = conversationsList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            transcriptView.getEngine().loadContent(baseHtml("<p>No conversation selected.</p>"));
            return;
        }

        try {
            List<Message> messages = chatService.listMessages(selected.id());
            StringBuilder markdown = new StringBuilder();
            int start = Math.max(0, messages.size() - RENDER_MESSAGE_LIMIT);
            if (start > 0) {
                markdown.append("> Showing latest ")
                        .append(RENDER_MESSAGE_LIMIT)
                        .append(" messages (")
                        .append(start)
                        .append(" older hidden for performance).\\n\\n");
            }

            for (int i = start; i < messages.size(); i++) {
                Message message = messages.get(i);
                if ("user".equals(message.role())) {
                    markdown.append("## You\n\n").append(message.content()).append("\n\n");
                } else {
                    markdown.append("## SPQR\n\n").append(message.content()).append("\n\n");
                }
            }
            String html = markdownRenderer.renderHtml(markdown.toString());
            if (showThinkingIndicator) {
                html = html + thinkingIndicatorHtml(selected.includeWebSearch());
            }
            if (scrollToLatestAssistant) {
                scrollToLatestAssistantOnLoad();
            } else if (showThinkingIndicator || scrollToBottom) {
                scrollToBottomOnLoad();
            }
            transcriptView.getEngine().loadContent(baseHtml(html));
        } catch (SQLException ex) {
            error("Database error", ex.getMessage());
        }
    }

    private void scrollToBottomOnLoad() {
        ChangeListener<Worker.State> listener = new ChangeListener<>() {
            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends Worker.State> obs,
                                Worker.State oldState,
                                Worker.State newState) {
                if (newState != Worker.State.SUCCEEDED) {
                    return;
                }

                transcriptView.getEngine().executeScript("window.scrollTo(0, document.body.scrollHeight);");
                transcriptView.getEngine().getLoadWorker().stateProperty().removeListener(this);
            }
        };
        transcriptView.getEngine().getLoadWorker().stateProperty().addListener(listener);
    }

    private void scrollToLatestAssistantOnLoad() {
                ChangeListener<Worker.State> listener = new ChangeListener<>() {
                        @Override
                        public void changed(javafx.beans.value.ObservableValue<? extends Worker.State> obs,
                                                                Worker.State oldState,
                                                                Worker.State newState) {
                                if (newState != Worker.State.SUCCEEDED) {
                                        return;
                                }

                                transcriptView.getEngine().executeScript("""
                                                (() => {
                                                    const headings = Array.from(document.querySelectorAll('h2'));
                                                    const target = headings.reverse().find(h => (h.textContent || '').trim().toLowerCase() === 'spqr');
                                                    if (target) {
                                                        target.scrollIntoView({behavior: 'auto', block: 'start'});
                                                    } else {
                                                        window.scrollTo(0, document.body.scrollHeight);
                                                    }
                                                })();
                                                """);
                                transcriptView.getEngine().getLoadWorker().stateProperty().removeListener(this);
                        }
                };
                transcriptView.getEngine().getLoadWorker().stateProperty().addListener(listener);
    }

    private void createConversation() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("New Chat");
        dialog.setHeaderText("Conversation Options");

        ButtonType createButton = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createButton, ButtonType.CANCEL);

        TextField titleField = new TextField(defaultConversationTitle());
        titleField.setPromptText("Conversation Title");
        titleField.setPrefWidth(340);

        CheckBox includeWebSearchBox = new CheckBox("Include Web Search");
        includeWebSearchBox.setSelected(false);

        Label titleLabel = new Label("Conversation Title");
        VBox content = new VBox(12,
            titleLabel,
            titleField,
            includeWebSearchBox);
        content.setPadding(new Insets(12, 4, 4, 4));

        dialog.getDialogPane().setMinWidth(440);
        dialog.getDialogPane().setPrefWidth(440);
        dialog.getDialogPane().setContent(content);
        applyDialogTheme(dialog);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != createButton) {
            return;
        }

        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        if (title.isBlank()) {
            return;
        }

        try {
            long id = chatService.createConversation(title, includeWebSearchBox.isSelected());
            refreshConversations();
            selectConversation(id);
        } catch (SQLException ex) {
            error("Could not create conversation", ex.getMessage());
        }
    }

    private void deleteSelectedConversation() {
        Conversation selected = conversationsList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete selected conversation?",
                ButtonType.YES,
                ButtonType.NO);
        alert.setHeaderText("Confirm Deletion");
        applyDialogTheme(alert);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }

        try {
            chatService.deleteConversation(selected.id());
            disposeConversationWebTabs(selected.id());
            refreshConversations();
            refreshMessages();
        } catch (SQLException ex) {
            error("Could not delete conversation", ex.getMessage());
        }
    }

    private void deleteAllConversations() {
        if (conversationsList.getItems().isEmpty()) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete all conversations? This cannot be undone.",
                ButtonType.YES,
                ButtonType.NO);
        alert.setHeaderText("Confirm Delete All");
        applyDialogTheme(alert);
        Optional<ButtonType> result = alert.showAndWait();
        if (result.orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }

        try {
            chatService.deleteAllConversations();
            disposeAllWebTabs();
            activeConversationId = null;
            refreshConversations();
            refreshMessages();
        } catch (SQLException ex) {
            error("Could not delete all conversations", ex.getMessage());
        }
    }

    private void sendMessage() {
        Conversation selected = ensureConversationSelected();
        String message = inputArea.getText().trim();
        if (selected == null || message.isBlank()) {
            return;
        }

        if (!ensureOpenAiSettingsConfigured()) {
            return;
        }
        String key = resolvedKey();

        String context = conversationContext(selected.id());

        sendButton.setDisable(true);
        inputArea.clear();

        try {
            chatService.addUserMessage(selected.id(), message);
            refreshMessages(false, true, false);
        } catch (SQLException ex) {
            sendButton.setDisable(false);
            error("Database error", ex.getMessage());
            return;
        }

        CompletableFuture.supplyAsync(() -> {
            try {
                return agentGraph.run(key, message, context, selected.includeWebSearch());
            } catch (Exception ex) {
                return "I encountered an error: " + ex.getMessage();
            }
        }).thenAccept(response -> Platform.runLater(() -> {
            try {
                chatService.addAssistantMessage(selected.id(), response);
                refreshConversations();
                refreshMessages(true);
            } catch (SQLException ex) {
                error("Database error", ex.getMessage());
            } finally {
                sendButton.setDisable(false);
            }
        }));
    }

    private Conversation ensureConversationSelected() {
        Conversation selected = conversationsList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            return selected;
        }

        try {
            long id = chatService.createConversation(defaultConversationTitle());
            refreshConversations();
            selectConversation(id);
            return conversationsList.getSelectionModel().getSelectedItem();
        } catch (SQLException ex) {
            error("Could not create conversation", ex.getMessage());
            return null;
        }
    }

    private static String defaultConversationTitle() {
        return LocalDateTime.now().format(CHAT_TITLE_FORMATTER) + " chat";
    }

    private String conversationContext(long conversationId) {
        try {
            List<Message> messages = chatService.listMessages(conversationId);
            int start = Math.max(0, messages.size() - CONTEXT_MESSAGE_LIMIT);
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < messages.size(); i++) {
                Message m = messages.get(i);
                sb.append(m.role()).append(": ").append(m.content()).append("\n");
            }
            return sb.toString();
        } catch (SQLException ex) {
            return "";
        }
    }

    private void setOpenAiKey() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("OpenAI Settings");
        dialog.setHeaderText("Set OPENAI_KEY and OPENAI_MODEL");

        ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        TextField keyField = new TextField(resolveCurrentKey());
        keyField.setPromptText("OPENAI_KEY");

        TextField modelField = new TextField(resolveCurrentModelName());
        modelField.setPromptText("OPENAI_MODEL");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(6, 2, 2, 2));
        grid.add(new Label("OPENAI_KEY"), 0, 0);
        grid.add(keyField, 1, 0);
        grid.add(new Label("OPENAI_MODEL"), 0, 1);
        grid.add(modelField, 1, 1);
        GridPane.setHgrow(keyField, Priority.ALWAYS);
        GridPane.setHgrow(modelField, Priority.ALWAYS);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().setPrefWidth(640);
        applyDialogTheme(dialog);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveButton) {
            return;
        }

        String key = keyField.getText() == null ? "" : keyField.getText().trim();
        String model = modelField.getText() == null ? "" : modelField.getText().trim();
        if (model.isBlank()) {
            model = DEFAULT_OPENAI_MODEL;
        }
        if (key.isBlank()) {
            info("Missing OPENAI_KEY", "OPENAI_KEY cannot be empty.");
            return;
        }

        try {
            keyStore.save(key);
            runtimeApiKey = key;
            runtimeModelName = model;
            ignoreEnvironmentKey = false;
            ignoreEnvironmentModel = false;
            System.setProperty("OPENAI_MODEL", model);
            String envMessage = EnvVarInstaller.installOpenAiSettings(key, model);
            info("OpenAI Settings Saved", "Stored in app config. " + envMessage);
        } catch (Exception ex) {
            error("Could Not Save Settings", ex.getMessage());
        }
    }

    private void clearOpenAiKey() {
        try {
            keyStore.clear();
            runtimeApiKey = null;
            runtimeModelName = DEFAULT_OPENAI_MODEL;
            ignoreEnvironmentKey = true;
            ignoreEnvironmentModel = true;
            System.setProperty("OPENAI_MODEL", runtimeModelName);
            String envMessage = EnvVarInstaller.clearOpenAiSettings();
            info("Done", "API key cleared for this session. " + envMessage + " OPENAI_MODEL reset to default for this session.");
        } catch (Exception ex) {
            error("Could Not Clear Key", ex.getMessage());
        }
    }

    private String resolvedKey() {
        if (runtimeApiKey != null && !runtimeApiKey.isBlank()) {
            return runtimeApiKey;
        }
        if (!ignoreEnvironmentKey) {
            String env = System.getenv("OPENAI_KEY");
            if (env != null && !env.isBlank()) {
                return env;
            }
        }
        return keyStore.read().orElse(null);
    }

    private String resolveCurrentKey() {
        if (!ignoreEnvironmentKey) {
            String env = System.getenv("OPENAI_KEY");
            if (env != null && !env.isBlank()) {
                return env.trim();
            }
        }
        String resolved = resolvedKey();
        return resolved == null ? "" : resolved;
    }

    private String resolveCurrentModelName() {
        if (!ignoreEnvironmentModel) {
            String envModel = System.getenv("OPENAI_MODEL");
            if (envModel != null && !envModel.isBlank()) {
                return envModel.trim();
            }
        }
        if (runtimeModelName != null && !runtimeModelName.isBlank()) {
            return runtimeModelName.trim();
        }
        String runtimeProperty = System.getProperty("OPENAI_MODEL");
        if (runtimeProperty != null && !runtimeProperty.isBlank()) {
            return runtimeProperty.trim();
        }
        return DEFAULT_OPENAI_MODEL;
    }

    private boolean ensureOpenAiSettingsConfigured() {
        String key = resolvedKey();
        if (key == null || key.isBlank()) {
            error("Missing OPENAI_KEY", "Set it in environment or File -> Set OpenAI Key and Model.");
            return false;
        }

        String model = resolveCurrentModelName();
        if (model == null || model.isBlank()) {
            error("Missing OPENAI_MODEL", "Set OPENAI_MODEL in File -> Set OpenAI Key and Model.");
            return false;
        }

        runtimeApiKey = key.trim();
        runtimeModelName = model.trim();
        System.setProperty("OPENAI_MODEL", runtimeModelName);
        return true;
    }

    private void selectConversation(long id) {
        for (Conversation item : conversationsList.getItems()) {
            if (item.id() == id) {
                conversationsList.getSelectionModel().select(item);
                break;
            }
        }
    }

    private String baseHtml(String body) {
        return """
                <html><head><meta charset=\"UTF-8\"><style>
                body { font-family: Inter, 'SF Pro Text', 'Segoe UI', sans-serif; color: #dbe4f0; padding: 18px; background: #0b1326; }
                h1,h2,h3 { margin-top: 1.2em; color: #eef4ff; }
                a { color: #93c5fd; }
                pre { background: #111c34; border: 1px solid #2b3a54; border-radius: 8px; padding: 10px; overflow-x: auto; }
                code { background: #111c34; color: #dbe4f0; padding: 2px 5px; border-radius: 4px; }
                blockquote { border-left: 4px solid #3b82f6; margin: 10px 0; padding-left: 10px; color: #b9c8dd; }
                                .copy-code-btn {
                                    position: absolute;
                                    top: 8px;
                                    right: 8px;
                                    border: 1px solid #3a4a66;
                                    background: #0f172a;
                                    color: #dbe4f0;
                                    border-radius: 7px;
                                    width: 28px;
                                    height: 28px;
                                    cursor: pointer;
                                    font-size: 15px;
                                    line-height: 1;
                                }
                                .copy-code-btn:hover {
                                    background: #1a2740;
                                    color: #f8fafc;
                                }
                                .thinking { margin-top: 14px; color: #9caec8; display: flex; align-items: center; gap: 8px; }
                                .thinking-label { font-weight: 600; color: #93c5fd; }
                                .thinking-dots { display: inline-flex; gap: 4px; }
                                .thinking-dots span { width: 7px; height: 7px; border-radius: 50%; background: #6f84a7; display: inline-block; animation: thinking-bounce 1.2s infinite ease-in-out; }
                                .thinking-dots span:nth-child(2) { animation-delay: 0.18s; }
                                .thinking-dots span:nth-child(3) { animation-delay: 0.36s; }
                                @keyframes thinking-bounce {
                                    0%, 80%, 100% { transform: scale(0.6); opacity: 0.45; }
                                    40% { transform: scale(1); opacity: 1; }
                                }
                </style></head><body>
                """ + body + "</body></html>";
    }

        private String thinkingIndicatorHtml(boolean includeWebSearch) {
                String status = includeWebSearch ? "Searching the web and thinking" : "Thinking";
                return """
                                <div class="thinking">
                                    <span class="thinking-label">SPQR</span>
                                    <span>%s</span>
                                    <span class="thinking-dots"><span></span><span></span><span></span></span>
                                </div>
                                """.formatted(status);
        }

                    private void showCopyToast(String text) {
                        copyToastTimer.stop();
                        copyToast.setText(text);
                        copyToast.setVisible(true);
                        copyToastTimer.playFromStart();
                    }

    private void info(String title, String text) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, text, ButtonType.OK);
        alert.setHeaderText(title);
        applyDialogTheme(alert);
        alert.showAndWait();
    }

    private void error(String title, String text) {
        Alert alert = new Alert(Alert.AlertType.ERROR, text, ButtonType.OK);
        alert.setHeaderText(title);
        applyDialogTheme(alert);
        alert.showAndWait();
    }

    private static void applyDialogTheme(Dialog<?> dialog) {
        DialogPane pane = dialog.getDialogPane();
        if (!pane.getStylesheets().contains(THEME_STYLESHEET)) {
            pane.getStylesheets().add(THEME_STYLESHEET);
        }
        if (!pane.getStyleClass().contains("app-dialog")) {
            pane.getStyleClass().add("app-dialog");
        }
    }

        private void bindTranscriptEnhancements() {
            JSObject window = (JSObject) transcriptView.getEngine().executeScript("window");
            window.setMember("chatBridge", new TranscriptBridge());
            transcriptView.getEngine().executeScript("""
                    (() => {
                      const ensureCodeButtons = () => {
                        const blocks = Array.from(document.querySelectorAll('pre'));
                        for (const pre of blocks) {
                          if (pre.dataset.copyReady === '1') {
                            continue;
                          }
                          pre.dataset.copyReady = '1';
                          pre.style.position = 'relative';

                          const btn = document.createElement('button');
                          btn.type = 'button';
                          btn.className = 'copy-code-btn';
                          btn.title = 'Copy code';
                          btn.textContent = '⧉';

                          btn.addEventListener('click', (event) => {
                            event.preventDefault();
                            event.stopPropagation();
                                                        const code = pre.querySelector('code');
                                                        let text = code ? (code.textContent || '') : (pre.textContent || '');
                                                        text = text.replace(/\u29c9|✓/g, '').replace(/\s+$/g, '');
                                                        if (window.chatBridge && window.chatBridge.copyCode) {
                                                            window.chatBridge.copyCode(text || '');
                                                        }
                            btn.textContent = '✓';
                            btn.title = 'Copied';
                            setTimeout(() => {
                              btn.textContent = '⧉';
                              btn.title = 'Copy code';
                            }, 1200);
                          });

                          pre.appendChild(btn);
                        }
                      };

                      const ensureLinkMenu = () => {
                        // Left-click link interception is handled in Java via WebView mouse events.
                      };

                      ensureCodeButtons();
                      ensureLinkMenu();
                    })();
                    """);
        }

        private void switchConversationTabs(Long conversationId) {
            if (activeConversationId != null && !activeConversationId.equals(conversationId)) {
                disposeConversationWebTabs(activeConversationId);
            }

            activeConversationId = conversationId;
            contentTabs.getTabs().setAll(chatTab);

            if (conversationId == null) {
                return;
            }

            List<WebTab> tabs = webTabsByConversation.computeIfAbsent(conversationId, this::loadPersistedWebTabs);
            for (WebTab tab : tabs) {
                contentTabs.getTabs().add(tab.tab());
            }
            contentTabs.getSelectionModel().select(chatTab);
        }

        private void disposeAllWebTabs() {
            List<Long> ids = new ArrayList<>(webTabsByConversation.keySet());
            for (Long id : ids) {
                if (id != null) {
                    disposeConversationWebTabs(id);
                }
            }
        }

        private void disposeConversationWebTabs(long conversationId) {
            List<WebTab> tabs = webTabsByConversation.remove(conversationId);
            if (tabs == null) {
                return;
            }
            for (WebTab tab : tabs) {
                disposeWebTab(tab);
            }
        }

        private void disposeWebTab(WebTab tab) {
            if (tab == null) {
                return;
            }

            try {
                tab.webView().getEngine().getLoadWorker().cancel();
            } catch (Exception ignored) {
                // No-op
            }

            try {
                tab.webView().getEngine().load("about:blank");
            } catch (Exception ignored) {
                // No-op
            }

            try {
                tab.tab().setContent(null);
            } catch (Exception ignored) {
                // No-op
            }
        }

        private List<WebTab> loadPersistedWebTabs(long conversationId) {
            List<WebTab> tabs = new ArrayList<>();
            try {
                List<ConversationWebTab> persistedTabs = chatService.listWebTabs(conversationId);
                for (ConversationWebTab persistedTab : persistedTabs) {
                    tabs.add(createWebTab(conversationId, persistedTab.url(), persistedTab.id()));
                }
            } catch (SQLException ex) {
                error("Could not load web tabs", ex.getMessage());
            }
            return tabs;
        }

        private void openInAppTab(String url) {
            Conversation selected = conversationsList.getSelectionModel().getSelectedItem();
            if (selected == null || url == null || url.isBlank()) {
                return;
            }

            long conversationId = selected.id();
            Long persistedId = null;
            try {
                persistedId = chatService.addWebTab(conversationId, url);
            } catch (SQLException ex) {
                error("Could not persist web tab", ex.getMessage());
            }

            WebTab tab = createWebTab(conversationId, url, persistedId);
            webTabsByConversation.computeIfAbsent(conversationId, ignored -> new ArrayList<>()).add(tab);

            if (activeConversationId != null && activeConversationId == conversationId) {
                contentTabs.getTabs().add(tab.tab());
                contentTabs.getSelectionModel().select(tab.tab());
            }
        }

        private WebTab createWebTab(long conversationId, String url, Long persistedId) {
            WebView webView = new WebView();
            WebEngine engine = webView.getEngine();
            WebHistory history = engine.getHistory();

            Button back = new Button("<");
            Button forward = new Button(">");
            back.setDisable(true);
            forward.setDisable(true);

            Runnable updateNav = () -> {
                int index = history.getCurrentIndex();
                int size = history.getEntries().size();
                back.setDisable(index <= 0);
                forward.setDisable(index >= size - 1);
            };

            back.setOnAction(e -> {
                if (history.getCurrentIndex() > 0) {
                    history.go(-1);
                }
            });
            forward.setOnAction(e -> {
                if (history.getCurrentIndex() < history.getEntries().size() - 1) {
                    history.go(1);
                }
            });

            history.currentIndexProperty().addListener((obs, oldVal, newVal) -> updateNav.run());

            Label location = new Label(url);
            location.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(location, Priority.ALWAYS);

            HBox toolbar = new HBox(8, back, forward, location);
            toolbar.setPadding(new Insets(8, 10, 8, 10));
            toolbar.setAlignment(Pos.CENTER_LEFT);

            BorderPane webPane = new BorderPane();
            webPane.setTop(toolbar);
            webPane.setCenter(webView);

            String tabTitle = abbreviatedHost(url);
            Tab tab = new Tab(tabTitle, webPane);
            tab.setClosable(true);
            tab.setOnClosed(e -> {
                List<WebTab> tabs = webTabsByConversation.get(conversationId);
                if (tabs != null) {
                    tabs.removeIf(item -> item.tab() == tab);
                    if (tabs.isEmpty()) {
                        webTabsByConversation.remove(conversationId);
                    }
                }
                disposeWebTab(new WebTab(tab, webView, persistedId));
                if (persistedId != null) {
                    try {
                        chatService.deleteWebTab(persistedId);
                    } catch (SQLException ex) {
                        error("Could not delete web tab", ex.getMessage());
                    }
                }
            });

            engine.locationProperty().addListener((obs, oldVal, newVal) -> {
                location.setText(newVal);
                tab.setText(abbreviatedHost(newVal));
                updateNav.run();
            });

            engine.load(url);
            return new WebTab(tab, webView, persistedId);
        }

        private String abbreviatedHost(String url) {
            if (url == null || url.isBlank()) {
                return "Web";
            }
            String value = url;
            int scheme = value.indexOf("://");
            if (scheme >= 0 && scheme + 3 < value.length()) {
                value = value.substring(scheme + 3);
            }
            int slash = value.indexOf('/');
            if (slash > 0) {
                value = value.substring(0, slash);
            }
            if (value.length() > 28) {
                return value.substring(0, 25) + "...";
            }
            return value;
        }

        public final class TranscriptBridge {
            public void openLinkMenu(String url) {
                if (url == null || url.isBlank()) {
                    return;
                }

                Platform.runLater(() -> {
                    if (activeLinkMenu != null) {
                        activeLinkMenu.hide();
                    }

                    MenuItem openTab = new MenuItem("Open in New Tab");
                    openTab.setOnAction(e -> openInAppTab(url));

                    MenuItem openBrowser = new MenuItem("Open in Browser");
                    openBrowser.setOnAction(e -> hostServices.showDocument(url));

                    ContextMenu menu = new ContextMenu(openTab, openBrowser);
                    menu.setAutoHide(true);
                    activeLinkMenu = menu;

                    Point2D anchor = transcriptView.localToScreen(
                            Math.max(24, transcriptView.getWidth() * 0.22),
                            Math.max(24, transcriptView.getHeight() * 0.16));
                    if (anchor != null) {
                        menu.show(transcriptView, anchor.getX(), anchor.getY());
                    } else {
                        menu.show(transcriptView, stage.getX() + stage.getWidth() / 2.0, stage.getY() + stage.getHeight() / 2.0);
                    }
                });
            }

            public void copyCode(String value) {
                String text = value == null ? "" : value.replace("\r\n", "\n");
                Runnable copyAction = () -> {
                    ClipboardContent content = new ClipboardContent();
                    content.putString(text);
                    boolean copied = Clipboard.getSystemClipboard().setContent(content);
                    showCopyToast(copied ? "Code copied" : "Copy failed");
                };

                if (Platform.isFxApplicationThread()) {
                    copyAction.run();
                } else {
                    Platform.runLater(copyAction);
                }
            }

            public void showLinkMenu(String url, double x, double y) {
                if (url == null || url.isBlank()) {
                    return;
                }

                Platform.runLater(() -> {
                    if (activeLinkMenu != null) {
                        activeLinkMenu.hide();
                    }

                    MenuItem openTab = new MenuItem("Open in New Tab");
                    openTab.setOnAction(e -> openInAppTab(url));

                    MenuItem openBrowser = new MenuItem("Open in Browser");
                    openBrowser.setOnAction(e -> hostServices.showDocument(url));

                    ContextMenu menu = new ContextMenu(openTab, openBrowser);
                    menu.setAutoHide(true);
                    activeLinkMenu = menu;

                    Point2D p = transcriptView.localToScreen(x, y);
                    if (p != null) {
                        menu.show(transcriptView, p.getX(), p.getY());
                    } else {
                        menu.show(transcriptView, stage.getX() + stage.getWidth() / 2.0, stage.getY() + stage.getHeight() / 2.0);
                    }
                });
            }
        }

        private record WebTab(Tab tab, WebView webView, Long persistedId) {}
}
