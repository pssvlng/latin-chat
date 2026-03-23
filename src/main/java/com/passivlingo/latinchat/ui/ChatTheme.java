package com.passivlingo.latinchat.ui;

public final class ChatTheme {
    private ChatTheme() {
    }

    public static final String APP_CSS = """
            * {
                -fx-font-family: "Inter", "SF Pro Text", "Segoe UI", "Helvetica Neue", "Roboto", "Arial", sans-serif;
            }

            .root {
                -fx-background-color: #f6f7f9;
            }

            .sidebar {
                -fx-background-color: #eef1f6;
                -fx-border-color: #c4ccd6;
                -fx-border-width: 0 1 0 0;
            }

            .chat-input {
                -fx-background-color: white;
                -fx-border-color: #c4ccd6;
                -fx-border-width: 1;
                -fx-border-radius: 8;
                -fx-background-radius: 8;
                -fx-padding: 8;
            }
            """;
}
