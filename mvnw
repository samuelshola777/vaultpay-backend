#!/bin/sh
set -e

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
MAVEN_VERSION=3.9.11
MAVEN_HOME="${HOME}/.m2/wrapper/dists/apache-maven-${MAVEN_VERSION}"

if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

if [ ! -x "${MAVEN_HOME}/bin/mvn" ]; then
  ARCHIVE="${MAVEN_HOME}.zip"
  mkdir -p "$(dirname "${MAVEN_HOME}")"
  URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.zip"
  if command -v curl >/dev/null 2>&1; then
    curl --fail --location --silent --show-error "$URL" --output "$ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget --quiet "$URL" --output-document="$ARCHIVE"
  else
    echo "Maven is not installed and neither curl nor wget is available." >&2
    exit 1
  fi
  mkdir -p "$MAVEN_HOME"
  unzip -q "$ARCHIVE" -d "$MAVEN_HOME"
  mv "$MAVEN_HOME/apache-maven-${MAVEN_VERSION}"/* "$MAVEN_HOME/"
  rmdir "$MAVEN_HOME/apache-maven-${MAVEN_VERSION}"
  rm -f "$ARCHIVE"
fi

cd "$BASE_DIR"
exec "${MAVEN_HOME}/bin/mvn" "$@"
