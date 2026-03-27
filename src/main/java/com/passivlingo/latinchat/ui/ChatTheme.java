package com.passivlingo.latinchat.ui;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class ChatTheme {
    private ChatTheme() {
    }

    public static String stylesheetUri() {
        return "data:text/css," + URLEncoder.encode(APP_CSS, StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    public static final String APP_CSS = """
            * {
                -fx-font-family: "Inter", "SF Pro Text", "Segoe UI", "Helvetica Neue", "Roboto", "Arial", sans-serif;
            }

            .root {
                -fx-background-color: #0f172a;
                -fx-text-fill: #e2e8f0;
            }

            .sidebar {
                -fx-background-color: #111c34;
                -fx-border-color: #273449;
                -fx-border-width: 0 1 0 0;
            }

            .chat-input {
                -fx-background-color: #0b1326;
                -fx-text-fill: #e2e8f0;
                -fx-prompt-text-fill: #8795ad;
                -fx-font-family: "SF Pro Text", "Segoe UI", "Noto Sans", "Ubuntu", "Cantarell", "Helvetica Neue", "Arial", sans-serif;
                -fx-font-size: 16px;
                -fx-highlight-fill: #2b4470;
                -fx-highlight-text-fill: #e2e8f0;
                -fx-border-color: #2b3a54;
                -fx-border-width: 1;
                -fx-border-radius: 8;
                -fx-background-radius: 8;
                -fx-padding: 8;
            }

            .chat-input .content {
                -fx-background-color: #0b1326;
                -fx-font-family: "SF Pro Text", "Segoe UI", "Noto Sans", "Ubuntu", "Cantarell", "Helvetica Neue", "Arial", sans-serif;
                -fx-font-size: 16px;
            }

            .label {
                -fx-text-fill: #d5deea;
            }

            .button {
                -fx-background-color: #1d4ed8;
                -fx-text-fill: #f8fafc;
                -fx-background-radius: 8;
                -fx-border-radius: 8;
                -fx-font-weight: 600;
                -fx-padding: 8 12;
            }

            .button:hover {
                -fx-background-color: #2563eb;
            }

            .button:pressed {
                -fx-background-color: #1e40af;
            }

            .text-area,
            .text-field {
                -fx-background-color: #0b1326;
                -fx-control-inner-background: #0b1326;
                -fx-text-fill: #e2e8f0;
                -fx-prompt-text-fill: #8795ad;
                -fx-highlight-fill: #2b4470;
                -fx-highlight-text-fill: #e2e8f0;
                -fx-border-color: #2b3a54;
                -fx-border-radius: 8;
                -fx-background-radius: 8;
            }

            .list-view {
                -fx-background-color: #0f172a;
                -fx-control-inner-background: #0f172a;
                -fx-border-color: #2b3a54;
            }

            .conversation-list .list-cell,
            .conversation-list .list-cell .label {
                -fx-font-family: "SF Pro Text", "Segoe UI", "Noto Sans", "Ubuntu", "Cantarell", "Helvetica Neue", "Arial", sans-serif;
                -fx-font-size: 16px;
            }

            .list-cell {
                -fx-background-color: transparent;
                -fx-text-fill: #d5deea;
            }

            .list-cell:filled:hover {
                -fx-background-color: #1a2740;
            }

            .list-cell:filled:selected,
            .list-cell:filled:selected:hover {
                -fx-background-color: #243759;
                -fx-text-fill: #f8fafc;
            }

            .menu-bar {
                -fx-background-color: #0b1326;
                -fx-border-color: #273449;
                -fx-border-width: 0 0 1 0;
            }

            .menu-bar .label {
                -fx-text-fill: #d5deea;
            }

            .context-menu,
            .menu-item,
            .menu-item .label {
                -fx-background-color: #0f172a;
                -fx-text-fill: #d5deea;
            }

            .menu-item:focused {
                -fx-background-color: #1d4ed8;
            }

            .menu-item:focused .label {
                -fx-text-fill: #f8fafc;
            }

            .tab-pane {
                -fx-background-color: #0f172a;
            }

            .tab-pane .tab-header-area .tab-header-background {
                -fx-background-color: #111c34;
            }

            .tab-pane .tab {
                -fx-background-color: #1a2740;
            }

            .tab-pane .tab:selected {
                -fx-background-color: #243759;
            }

            .tab-pane .tab .tab-label {
                -fx-text-fill: #d5deea;
            }

            .separator .line {
                -fx-border-color: #273449;
            }

            .tooltip {
                -fx-background-color: #0b1326;
                -fx-text-fill: #d5deea;
                -fx-border-color: #273449;
                -fx-border-radius: 6;
                -fx-background-radius: 6;
            }

            .app-dialog {
                -fx-background-color: #0f172a;
            }

            .app-dialog .header-panel {
                -fx-background-color: #111c34;
            }

            .app-dialog .header-panel .label {
                -fx-text-fill: #e2e8f0;
                -fx-font-weight: 700;
            }

            .app-dialog .content.label {
                -fx-text-fill: #d5deea;
            }

            .app-dialog .button-bar {
                -fx-background-color: #111c34;
            }
            """;
}
