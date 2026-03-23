# SPQR Latin Chat (Java Desktop)

A JavaFX desktop chat application with a ChatGPT-like layout:
- Left panel for conversation history
- Center panel for rich Markdown-rendered responses
- Bottom composer for user prompts

The assistant is configured to always reply in Latin.

## Current Implementation Scope

Implemented:
- JavaFX desktop shell with full-screen startup
- Conversation create/delete/select
- SQLite persistence for conversations and messages
- File menu actions for OpenAI key set/clear
- OpenAI API integration via LangChain4j OpenAI chat model
- Latin-first system prompt + response validation/retry
- Markdown rendering with HTML styling
- SPQR icon generation (red background, yellow foreground text)
- run.sh and run.bat launch scripts for built jar

Planned next increments:
- Replace lightweight retry graph with explicit LangGraph4j orchestration
- Add streaming token UX
- Add stronger Latin language validation and test suite
- Package native bundles per OS

## Requirements

- Java 17+
- Maven 3.9+
- OpenAI API key in OPENAI_KEY or configured in-app

## Build

```bash
mvn -DskipTests clean package
```

## Test

```bash
mvn test
```

## Run (from built jar)

macOS/Linux:
```bash
bash run.sh
```

- Runtime dependencies are copied to target/lib for launcher scripts.
Windows:
```bat
run.bat
```

## API Key Behavior

Resolution order:
1. OPENAI_KEY environment variable
2. Local app config at ~/.spqr-latin-chat/config.properties

File -> Set OpenAI Key does:
- Stores key in local app config for reliable app startup
- Attempts OS-level persistence:
  - Windows: setx OPENAI_KEY <key> (user scope)
  - macOS/Linux: appends export OPENAI_KEY=... to shell profile (.zshrc or .bash_profile)

Notes:
- Windows setx is effective for future terminals/sessions.
- macOS/Linux GUI apps may not inherit shell env vars depending on launch method/desktop session; local app config fallback is used for reliability.

## Project Structure

- src/main/java/com/passivlingo/latinchat/MainApp.java: App bootstrap
- src/main/java/com/passivlingo/latinchat/ui/: JavaFX UI and theme
- src/main/java/com/passivlingo/latinchat/data/: SQLite persistence layer
- src/main/java/com/passivlingo/latinchat/agent/: OpenAI client + agent graph + Latin enforcement
- src/main/java/com/passivlingo/latinchat/config/: app paths and key store
- src/main/java/com/passivlingo/latinchat/platform/: environment variable installer

## Important

The reference folder used for initial styling inspiration has been removed from this repository.