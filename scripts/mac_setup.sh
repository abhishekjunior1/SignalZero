#!/usr/bin/env bash
# Build and run Lodestar on macOS (Apple Silicon), from a clean machine.
#
# The original toolchain targeted Windows + WSL2 with a Qualcomm QNN SDK and a
# physical Snapdragon device. None of that exists on a Mac, so this script sets
# up the parts that do work and disables the parts that cannot:
#
#   - QNN/ExecuTorch needs a Hexagon NPU. There is none in an emulator, so the
#     build sets ENABLE_QNN_BACKEND=false. AiServiceFactory already falls back
#     to StubAiService, so everything except on-device generation still runs.
#   - The overrides are passed with -P rather than edited into
#     android/gradle.properties, so the Windows configuration stays intact.
#
# Usage:  bash scripts/mac_setup.sh [install|build|run|all]
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

AVD=signalzero
IMAGE="system-images;android-35;google_apis;arm64-v8a"
GRADLE_OVERRIDES=(-PenableQnnBackend=false -PuseLocalAar=false -PqnnVersion=)

install_toolchain() {
  # openjdk@17 is the FORMULA, not the temurin cask: the cask ships a .pkg that
  # requires sudo and fails in a non-interactive shell.
  brew list openjdk@17 >/dev/null 2>&1 || brew install openjdk@17
  brew list --cask android-commandlinetools >/dev/null 2>&1 || brew install --cask android-commandlinetools

  yes | sdkmanager --licenses >/dev/null 2>&1 || true
  sdkmanager --install "platform-tools" "platforms;android-35" "build-tools;35.0.0" "emulator" "$IMAGE"

  echo "sdk.dir=$ANDROID_HOME" > "$REPO/local.properties"
  echo "sdk.dir=$ANDROID_HOME" > "$REPO/android/local.properties"
}

build() {
  chmod +x "$REPO/android/gradlew" "$REPO/gradlew"
  # Shared multiplatform module: unit tests for the domain logic.
  (cd "$REPO" && ./gradlew :shared:testDebugUnitTest)
  # The app itself.
  (cd "$REPO/android" && ./gradlew assembleDebug \
      -Dorg.gradle.java.home="$JAVA_HOME" "${GRADLE_OVERRIDES[@]}")
}

evaluate() {
  cd "$REPO"
  for system in naive safetytree; do
    ./gradlew -q :shared:evalCli --args="eval/cases/triage.json $system" \
      | sed -n '/^\[/,/^\]/p' > "eval/results/raw_$system.json"
    python3 eval/score.py "eval/results/raw_$system.json" "$system"
  done
}

run() {
  emulator -list-avds | grep -qx "$AVD" || \
    echo "no" | avdmanager create avd -n "$AVD" -k "$IMAGE" --force

  if ! adb devices | grep -q emulator; then
    nohup emulator -avd "$AVD" -no-snapshot-load -no-boot-anim \
      -gpu swiftshader_indirect >/tmp/emulator.log 2>&1 &
    adb wait-for-device
    until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
      sleep 3
    done
  fi

  adb install -r "$REPO/android/app/build/outputs/apk/debug/app-debug.apk"
  for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION RECORD_AUDIO CAMERA \
           READ_MEDIA_IMAGES POST_NOTIFICATIONS; do
    adb shell pm grant com.medic.app "android.permission.$p" 2>/dev/null || true
  done
  adb shell am start -n com.medic.app/.MainActivity
}

case "${1:-all}" in
  install)  install_toolchain ;;
  build)    build ;;
  eval)     evaluate ;;
  run)      run ;;
  all)      install_toolchain; build; evaluate; run ;;
  *) echo "usage: $0 [install|build|eval|run|all]"; exit 2 ;;
esac
