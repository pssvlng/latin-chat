package com.passivlingo.latinchat.ui;

import com.passivlingo.latinchat.agent.LatinAgentGraph;
import com.passivlingo.latinchat.config.ApiKeyStore;
import com.passivlingo.latinchat.data.ChatService;
import com.passivlingo.latinchat.model.Conversation;
import com.passivlingo.latinchat.model.ConversationWebTab;
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
import javafx.scene.layout.*;
import javafx.scene.web.WebHistory;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import javafx.util.Duration;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class MainWindow {
    private static final int CONTEXT_MESSAGE_LIMIT = 24;

    private final Stage stage;
    private final ChatService chatService;
    private final LatinAgentGraph agentGraph;
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

    public MainWindow(Stage stage,
                      ChatService chatService,
                      LatinAgentGraph agentGraph,
                      ApiKeyStore keyStore,
                      MarkdownRenderer markdownRenderer,
                      String initialApiKey,
                      HostServices hostServices) {
        this.stage = stage;
        this.chatService = chatService;
        this.agentGraph = agentGraph;
        this.keyStore = keyStore;
        this.markdownRenderer = markdownRenderer;
        this.runtimeApiKey = initialApiKey;
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
        scene.getStylesheets().add("data:text/css," + ChatTheme.APP_CSS.replace("\n", "%0A").replace(" ", "%20"));

        stage.setScene(scene);
        stage.setTitle("SPQR Latin Chat");
        stage.show();

        copyToastTimer.setOnFinished(e -> copyToast.setVisible(false));

        setupEvents();
        refreshConversations();
    }

    private MenuBar createMenuBar() {
        Menu fileMenu = new Menu("File");
        MenuItem setKey = new MenuItem("Set OpenAI Key");
        MenuItem clearKey = new MenuItem("Clear OpenAI Key");
        MenuItem exit = new MenuItem("Exit");

        setKey.setOnAction(e -> setOpenAiKey());
        clearKey.setOnAction(e -> clearOpenAiKey());
        exit.setOnAction(e -> Platform.exit());

        fileMenu.getItems().addAll(setKey, clearKey, new SeparatorMenuItem(), exit);
        return new MenuBar(fileMenu);
    }

    private Pane createSidebar() {
        Label title = new Label("Conversations");
        Button newChat = new Button("+ New Chat");
        Button deleteChat = new Button("Delete");

        newChat.setMaxWidth(Double.MAX_VALUE);
        deleteChat.setMaxWidth(Double.MAX_VALUE);

        newChat.setOnAction(e -> createConversation());
        deleteChat.setOnAction(e -> deleteSelectedConversation());

        conversationsList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Conversation item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.title());
            }
        });

        VBox box = new VBox(10, title, newChat, deleteChat, new Separator(Orientation.HORIZONTAL), conversationsList);
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
            for (Message message : messages) {
                if ("user".equals(message.role())) {
                    markdown.append("## You\n\n").append(message.content()).append("\n\n");
                } else {
                    markdown.append("## SPQR\n\n").append(message.content()).append("\n\n");
                }
            }
            String html = markdownRenderer.renderHtml(markdown.toString());
            if (showThinkingIndicator) {
                html = html + thinkingIndicatorHtml();
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
        TextInputDialog dialog = new TextInputDialog("New Chat");
        dialog.setHeaderText("Conversation title");
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty() || result.get().isBlank()) {
            return;
        }

        try {
            long id = chatService.createConversation(result.get().trim());
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
        alert.setHeaderText("Confirm deletion");
        Optional<ButtonType> result = alert.showAndWait();
        if (result.orElse(ButtonType.NO) != ButtonType.YES) {
            return;
        }

        try {
            chatService.deleteConversation(selected.id());
            webTabsByConversation.remove(selected.id());
            refreshConversations();
            refreshMessages();
        } catch (SQLException ex) {
            error("Could not delete conversation", ex.getMessage());
        }
    }

    private void sendMessage() {
        Conversation selected = ensureConversationSelected();
        String message = inputArea.getText().trim();
        if (selected == null || message.isBlank()) {
            return;
        }

        String key = resolvedKey();
        if (key == null || key.isBlank()) {
            error("Missing OPENAI_KEY", "Set the key in environment or File -> Set OpenAI Key.");
            return;
        }

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
                return agentGraph.run(key, message, context);
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
            long id = chatService.createConversation("New Chat");
            refreshConversations();
            selectConversation(id);
            return conversationsList.getSelectionModel().getSelectedItem();
        } catch (SQLException ex) {
            error("Could not create conversation", ex.getMessage());
            return null;
        }
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
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("OpenAI Key");
        dialog.setHeaderText("Enter OPENAI_KEY");
        Optional<String> result = dialog.showAndWait();

        if (result.isEmpty() || result.get().isBlank()) {
            return;
        }

        String key = result.get().trim();
        try {
            keyStore.save(key);
            runtimeApiKey = key;
            String envMessage = EnvVarInstaller.installOpenAiKey(key);
            info("API key saved", "Stored in app config. " + envMessage);
        } catch (Exception ex) {
            error("Could not save key", ex.getMessage());
        }
    }

    private void clearOpenAiKey() {
        try {
            keyStore.clear();
            runtimeApiKey = null;
            info("Done", "Local API key store cleared.");
        } catch (Exception ex) {
            error("Could not clear key", ex.getMessage());
        }
    }

    private String resolvedKey() {
        if (runtimeApiKey != null && !runtimeApiKey.isBlank()) {
            return runtimeApiKey;
        }
        String env = System.getenv("OPENAI_KEY");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return keyStore.read().orElse(null);
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
                <html><head><style>
                body { font-family: Inter, 'SF Pro Text', 'Segoe UI', sans-serif; color: #212529; padding: 18px; background: #ffffff; }
                h1,h2,h3 { margin-top: 1.2em; }
                pre { background: #f1f4f9; border: 1px solid #d6dee8; border-radius: 8px; padding: 10px; overflow-x: auto; }
                code { background: #f1f4f9; padding: 2px 5px; border-radius: 4px; }
                blockquote { border-left: 4px solid #b22020; margin: 10px 0; padding-left: 10px; color: #444; }
                                .copy-code-btn {
                                    position: absolute;
                                    top: 8px;
                                    right: 8px;
                                    border: 1px solid #b7c0cc;
                                    background: #ffffff;
                                    color: #4b5563;
                                    border-radius: 7px;
                                    width: 28px;
                                    height: 28px;
                                    cursor: pointer;
                                    font-size: 15px;
                                    line-height: 1;
                                }
                                .copy-code-btn:hover {
                                    background: #f3f6fb;
                                    color: #1f2937;
                                }
                                .thinking { margin-top: 14px; color: #6a7585; display: flex; align-items: center; gap: 8px; }
                                .thinking-label { font-weight: 600; color: #b22020; }
                                .thinking-dots { display: inline-flex; gap: 4px; }
                                .thinking-dots span { width: 7px; height: 7px; border-radius: 50%; background: #8f99a8; display: inline-block; animation: thinking-bounce 1.2s infinite ease-in-out; }
                                .thinking-dots span:nth-child(2) { animation-delay: 0.18s; }
                                .thinking-dots span:nth-child(3) { animation-delay: 0.36s; }
                                @keyframes thinking-bounce {
                                    0%, 80%, 100% { transform: scale(0.6); opacity: 0.45; }
                                    40% { transform: scale(1); opacity: 1; }
                                }
                </style></head><body>
                """ + body + "</body></html>";
    }

        private String thinkingIndicatorHtml() {
                return """
                                <div class="thinking">
                                    <span class="thinking-label">SPQR</span>
                                    <span class="thinking-dots"><span></span><span></span><span></span></span>
                                </div>
                                """;
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
        alert.showAndWait();
    }

    private void error(String title, String text) {
        Alert alert = new Alert(Alert.AlertType.ERROR, text, ButtonType.OK);
        alert.setHeaderText(title);
        alert.showAndWait();
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

                    MenuItem openBrowser = new MenuItem("open in browser");
                    openBrowser.setOnAction(e -> hostServices.showDocument(url));

                    MenuItem openTab = new MenuItem("open in new tab");
                    openTab.setOnAction(e -> openInAppTab(url));

                    ContextMenu menu = new ContextMenu(openBrowser, openTab);
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

                    MenuItem openBrowser = new MenuItem("open in browser");
                    openBrowser.setOnAction(e -> hostServices.showDocument(url));

                    MenuItem openTab = new MenuItem("open in new tab");
                    openTab.setOnAction(e -> openInAppTab(url));

                    ContextMenu menu = new ContextMenu(openBrowser, openTab);
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
