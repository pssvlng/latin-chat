package com.passivlingo.latinchat;

import com.passivlingo.latinchat.agent.LatinAgentGraph;
import com.passivlingo.latinchat.agent.OpenAiClient;
import com.passivlingo.latinchat.config.ApiKeyStore;
import com.passivlingo.latinchat.config.AppPaths;
import com.passivlingo.latinchat.data.ChatRepository;
import com.passivlingo.latinchat.data.ChatService;
import com.passivlingo.latinchat.data.DatabaseManager;
import com.passivlingo.latinchat.ui.AppIconFactory;
import com.passivlingo.latinchat.ui.MainWindow;
import com.passivlingo.latinchat.util.MarkdownRenderer;
import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.stage.Stage;

import java.awt.Taskbar;
import java.nio.file.Path;

public final class MainApp extends Application {
    private DatabaseManager db;

    @Override
    public void start(Stage stage) throws Exception {
        Path home = AppPaths.appHome();
        db = new DatabaseManager(home);
        db.initialize();

        ChatRepository repository = new ChatRepository(db);
        ChatService service = new ChatService(repository);
        ApiKeyStore keyStore = new ApiKeyStore(home);
        LatinAgentGraph graph = new LatinAgentGraph(new OpenAiClient());
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

        MainWindow window = new MainWindow(stage, service, graph, keyStore, new MarkdownRenderer(), key, getHostServices());
        window.show();
    }

    @Override
    public void stop() {
        if (db != null) {
            db.close();
        }
    }

    public static void main(String[] args) {
        launch(args);
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