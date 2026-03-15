#!/bin/bash

# 获取脚本所在目录
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR/shower"

# === 1. 编译 Shower (release) ===
echo "[1/3] Building Shower (assembleRelease)..."
chmod +x gradlew
./gradlew assembleRelease

if [ $? -ne 0 ]; then
    echo "[ERROR] Gradle build failed."
    exit 1
fi

# === 2. 查找生成的 release APK ===
APK_DIR="app/build/outputs/apk/release"
APK_PATH=$(find "$APK_DIR" -name "*.apk" | head -n 1)

if [ -z "$APK_PATH" ]; then
    echo "[ERROR] 未在 $APK_DIR 下找到 APK 文件。"
    exit 1
fi

echo "[INFO] 使用 APK: $APK_PATH"

# === 3. 复制到 assets 目录作为 shower-server.jar ===
TARGET_ASSETS_DIR="$SCRIPT_DIR/../showerclient/src/main/assets"
mkdir -p "$TARGET_ASSETS_DIR"

TARGET_JAR="$TARGET_ASSETS_DIR/shower-server.jar"
echo "[2/3] Copying APK to $TARGET_JAR ..."
cp -f "$APK_PATH" "$TARGET_JAR"

if [ $? -ne 0 ]; then
    echo "[ERROR] 无法复制 APK 到 $TARGET_JAR。"
    exit 1
fi

echo "[3/3] Done. Generated $TARGET_JAR"