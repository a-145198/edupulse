#!/usr/bin/env bash
set -e

MODEL_PATH="models/gemma-4-E2B-it-gpu.litertlm"
PACKAGE="com.edupulse.app"
DEST_DIR="/sdcard/Android/data/${PACKAGE}/files"

ADB_BIN="adb"
if ! command -v adb &> /dev/null; then
    if [ -f "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
        ADB_BIN="$HOME/Library/Android/sdk/platform-tools/adb"
    else
        echo "❌ 'adb' command not found."
        echo "   Ensure Android Platform Tools are installed and 'adb' is on your PATH."
        exit 1
    fi
fi

if [ ! -f "$MODEL_PATH" ]; then
    echo "❌ Model file not found at: $MODEL_PATH"
    exit 1
fi

echo "📱 Checking connected Android devices..."
"$ADB_BIN" devices

echo "📂 Creating destination directory on device: $DEST_DIR..."
"$ADB_BIN" shell "mkdir -p $DEST_DIR"

echo "🚀 Pushing $MODEL_PATH to $DEST_DIR..."
"$ADB_BIN" push "$MODEL_PATH" "$DEST_DIR/"

echo "✅ Model successfully pushed to device! The app will detect it on launch."
