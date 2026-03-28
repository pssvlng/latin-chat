package com.passivlingo.latinchat.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManager {
    private final Path dbPath;
    private Connection connection;

    public DatabaseManager(Path appHome) {
        this.dbPath = appHome.resolve("latin_chat.db");
    }

    public synchronized Connection connection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }
        }
        return connection;
    }

    public synchronized void initialize() throws SQLException {
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (Exception ex) {
            throw new SQLException("Failed to prepare database directory", ex);
        }

        try (Statement statement = connection().createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS conversations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        title TEXT NOT NULL,
                        include_web_search INTEGER NOT NULL DEFAULT 0,
                        language_code TEXT NOT NULL DEFAULT 'la',
                        created_at TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);

            if (!columnExists("conversations", "include_web_search")) {
                statement.executeUpdate("ALTER TABLE conversations ADD COLUMN include_web_search INTEGER NOT NULL DEFAULT 0");
            }
            if (!columnExists("conversations", "language_code")) {
                statement.executeUpdate("ALTER TABLE conversations ADD COLUMN language_code TEXT NOT NULL DEFAULT 'la'");
            }

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        conversation_id INTEGER NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS web_tabs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        conversation_id INTEGER NOT NULL,
                        url TEXT NOT NULL,
                        created_at TEXT NOT NULL,
                        FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
                    )
                    """);
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        String escapedTable = tableName.replace("'", "''");
        try (Statement statement = connection().createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA table_info('" + escapedTable + "')")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // No-op
        }
    }
}