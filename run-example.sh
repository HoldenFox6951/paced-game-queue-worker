#!/usr/bin/env sh
set -eu
BUILD_DIR="${TMPDIR:-/tmp}/game-queue-worker-classes"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" $(find src/main/java -name '*.java')
java -cp "$BUILD_DIR" learn.gameworker.GameQueueLesson
