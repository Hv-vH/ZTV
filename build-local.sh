#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
export ANDROID_USER_HOME="$ROOT_DIR/.toolchain/android-user-home"
export ANDROID_SDK_ROOT="$ROOT_DIR/.toolchain/android-sdk"
export GRADLE_USER_HOME="$ROOT_DIR/.toolchain/gradle-user-home"
export JAVA_HOME="$ROOT_DIR/.toolchain/jdk/jdk-17.0.20.1+1/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

exec "$ROOT_DIR/.toolchain/gradle/gradle-8.13/bin/gradle" "$@"
