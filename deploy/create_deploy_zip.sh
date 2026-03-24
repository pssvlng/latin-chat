#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST_ROOT="$SCRIPT_DIR/dist"
APP_DIR="$DIST_ROOT/spqr-latin-chat"

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven is required but was not found in PATH."
  exit 1
fi

if ! command -v zip >/dev/null 2>&1; then
  echo "zip command is required but was not found in PATH."
  exit 1
fi

echo "Building application..."
cd "$ROOT_DIR"
mvn -DskipTests clean package

JAR_PATH="$(ls "$ROOT_DIR"/target/latin-chat-*.jar 2>/dev/null | grep -vE '(sources|javadoc|tests)' | head -n 1 || true)"
if [[ -z "$JAR_PATH" || ! -f "$JAR_PATH" ]]; then
  echo "Could not find built application jar in target/."
  exit 1
fi

if [[ ! -d "$ROOT_DIR/target/lib" ]]; then
  echo "Dependency folder target/lib was not found after build."
  exit 1
fi

APP_VERSION="$(basename "$JAR_PATH" | sed -E 's/^latin-chat-([0-9][^.]*(\.[0-9A-Za-z_-]+)*)\.jar$/\1/' )"
if [[ -z "$APP_VERSION" || "$APP_VERSION" == "$(basename "$JAR_PATH")" ]]; then
  APP_VERSION="1.0.0"
fi

ZIP_NAME="spqr-latin-chat-${APP_VERSION}.zip"
ZIP_PATH="$SCRIPT_DIR/$ZIP_NAME"

rm -rf "$APP_DIR"
mkdir -p "$APP_DIR/target/lib"

cp "$ROOT_DIR/run.sh" "$APP_DIR/run.sh"
cp "$ROOT_DIR/run.bat" "$APP_DIR/run.bat"
cp "$JAR_PATH" "$APP_DIR/target/"
cp -R "$ROOT_DIR/target/lib/." "$APP_DIR/target/lib/"

chmod +x "$APP_DIR/run.sh"

rm -f "$ZIP_PATH"
(
  cd "$DIST_ROOT"
  zip -r "$ZIP_PATH" "$(basename "$APP_DIR")" >/dev/null
)

echo "Created deploy archive: $ZIP_PATH"
echo "Unzip and run using run.sh (macOS/Linux) or run.bat (Windows)."
