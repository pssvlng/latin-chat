package com.passivlingo.latinchat;

import com.passivlingo.latinchat.agent.GrammarAgentGraph;
import com.passivlingo.latinchat.agent.LatinAgentGraph;
import com.passivlingo.latinchat.agent.OpenAiClient;
import com.passivlingo.latinchat.config.ApiKeyStore;
import com.passivlingo.latinchat.config.AppPaths;
import com.passivlingo.latinchat.data.ChatRepository;
import com.passivlingo.latinchat.data.ChatService;
import com.passivlingo.latinchat.data.DatabaseManager;
import com.passivlingo.latinchat.ui.AppIconFactory;
import com.passivlingo.latinchat.ui.ChatTheme;
import com.passivlingo.latinchat.ui.MainWindow;
import com.passivlingo.latinchat.util.MarkdownRenderer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Shell32;

import java.awt.Taskbar;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainApp extends Application {
    private static final AtomicBoolean CRASH_DIALOG_SHOWN = new AtomicBoolean(false);

    private DatabaseManager db;

    @Override
    public void start(Stage stage) throws Exception {
        Path home = AppPaths.appHome();
        db = new DatabaseManager(home);
        db.initialize();

        ChatRepository repository = new ChatRepository(db);
        ChatService service = new ChatService(repository);
        ApiKeyStore keyStore = new ApiKeyStore(home);
        OpenAiClient openAiClient = new OpenAiClient();
        LatinAgentGraph graph = new LatinAgentGraph(openAiClient);
        GrammarAgentGraph grammarGraph = new GrammarAgentGraph(openAiClient);
        String key = System.getenv("OPENAI_KEY");
        if (key == null || key.isBlank()) {
            key = keyStore.read().orElse(null);
        }

        var icon16 = AppIconFactory.createSpqrIcon(16);
        var icon32 = AppIconFactory.createSpqrIcon(32);
        var icon64 = AppIconFactory.createSpqrIcon(64);
        var icon128 = AppIconFactory.createSpqrIcon(128);
        stage.getIcons().addAll(icon16, icon32, icon64, icon128);
        installTaskbarIcon(icon128);

        MainWindow window = new MainWindow(stage, service, graph, grammarGraph, keyStore, new MarkdownRenderer(), key, getHostServices());
        window.show();
    }

    @Override
    public void stop() {
        if (db != null) {
            db.close();
        }
    }

    public static void main(String[] args) {
        configureWindowsAppIdentity();
        installGlobalExceptionHandler();
        launch(args);
    }

    private static void configureWindowsAppIdentity() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!osName.contains("win")) {
            return;
        }

        try {
            Shell32.INSTANCE.SetCurrentProcessExplicitAppUserModelID(new WString("com.passivlingo.latinchat"));
        } catch (Throwable ignored) {
            // Keep startup resilient even if Windows shell APIs are unavailable.
        }
    }

    private static void installGlobalExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            if (throwable == null || !CRASH_DIALOG_SHOWN.compareAndSet(false, true)) {
                return;
            }

            Runnable showCrashDialog = () -> {
                try {
                    Alert alert = new Alert(Alert.AlertType.ERROR, "The application encountered an unrecoverable error and must close.", ButtonType.OK);
                    alert.setHeaderText("Unexpected Crash");
                    alert.setTitle("SPQR Latin Chat");
                    alert.getDialogPane().getStylesheets().add(ChatTheme.stylesheetUri());
                    alert.getDialogPane().getStyleClass().add("app-dialog");

                    String threadName = thread == null ? "unknown" : thread.getName();
                    String rootMessage = throwable.getMessage();
                    if (rootMessage == null || rootMessage.isBlank()) {
                        rootMessage = throwable.getClass().getSimpleName();
                    }

                    TextArea details = new TextArea(buildCrashDetails(threadName, throwable));
                    details.setEditable(false);
                    details.setWrapText(false);
                    details.setPrefRowCount(12);
                    VBox.setVgrow(details, Priority.ALWAYS);

                    VBox content = new VBox(8);
                    content.getChildren().addAll(
                            new javafx.scene.control.Label("Thread: " + threadName),
                            new javafx.scene.control.Label("Reason: " + rootMessage),
                            details
                    );

                    alert.getDialogPane().setExpandableContent(content);
                    alert.getDialogPane().setExpanded(false);
                    alert.showAndWait();
                } catch (Exception popupError) {
                    popupError.printStackTrace(System.err);
                } finally {
                    Platform.exit();
                    System.exit(1);
                }
            };

            if (Platform.isFxApplicationThread()) {
                showCrashDialog.run();
            } else {
                try {
                    Platform.runLater(showCrashDialog);
                } catch (IllegalStateException ex) {
                    throwable.printStackTrace(System.err);
                    System.exit(1);
                }
            }
        });
    }

    private static String buildCrashDetails(String threadName, Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("Thread: " + threadName);
        throwable.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static void installTaskbarIcon(javafx.scene.image.Image icon) {
        if (!Taskbar.isTaskbarSupported()) {
            return;
        }

        try {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (!taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                return;
            }
            taskbar.setIconImage(SwingFXUtils.fromFXImage(icon, null));
        } catch (UnsupportedOperationException ignored) {
            // Some platforms/desktop environments expose Taskbar but do not support icon updates.
        }
    }
}