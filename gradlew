#!/usr/bin/env sh
set -eu

GRADLE_VERSION="9.4.1"
PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BOOTSTRAP_DIR="${GRADLE_USER_HOME:-$HOME/.gradle}/canasta-bootstrap/gradle-${GRADLE_VERSION}"
GRADLE_BIN="$BOOTSTRAP_DIR/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
  ARCHIVE="${TMPDIR:-/tmp}/gradle-${GRADLE_VERSION}-bin.zip"
  URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  echo "Downloading Gradle ${GRADLE_VERSION}..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL "$URL" -o "$ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ARCHIVE" "$URL"
  else
    echo "Install curl or wget, or open the project in Android Studio." >&2
    exit 1
  fi
  mkdir -p "$(dirname "$BOOTSTRAP_DIR")"
  rm -rf "$BOOTSTRAP_DIR"
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$ARCHIVE" -d "$(dirname "$BOOTSTRAP_DIR")"
  else
    echo "Install unzip, or open the project in Android Studio." >&2
    exit 1
  fi
fi

exec "$GRADLE_BIN" -p "$PROJECT_DIR" "$@"
