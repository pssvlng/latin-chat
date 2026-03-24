#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_PATH="$SCRIPT_DIR/target/latin-chat-1.0.0.jar"
LIB_DIR="$SCRIPT_DIR/target/lib"

if ! command -v java >/dev/null 2>&1; then
  echo "Java is not installed. Install Java 17+ and try again."
  exit 1
fi

if [ ! -f "$JAR_PATH" ]; then
  echo "Jar not found: $JAR_PATH"
  echo "Run: mvn -DskipTests clean package"
  exit 1
fi

if [ ! -d "$LIB_DIR" ]; then
  echo "Dependency directory not found: $LIB_DIR"
  echo "Run: mvn -DskipTests clean package"
  exit 1
fi

java -cp "$JAR_PATH:$LIB_DIR/*" com.passivlingo.latinchat.AppLauncher
