#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BUILD_DIR="/tmp/ui-similarity-tests"
mkdir -p "$BUILD_DIR"

SRC_FILES=$(find src/main/kotlin src/test/kotlin -name '*.kt' | tr '\n' ' ')

kotlinc $SRC_FILES -d "$BUILD_DIR/tests.jar"
kotlin -classpath "$BUILD_DIR/tests.jar" RunAllTestsKt
