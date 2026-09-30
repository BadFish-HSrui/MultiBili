#!/bin/sh

set -eu

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"

case "${PLATFORM_NAME:-}" in
    iphoneos) NATIVE_TARGET=ios-arm64 ;;
    iphonesimulator) NATIVE_TARGET=iossim-arm64 ;;
    *) echo "error: Unsupported iOS platform: ${PLATFORM_NAME:-unset}" >&2; exit 1 ;;
esac
if [ "${ARCHS:-}" != "arm64" ] || [ "${BOLO_IOS_NATIVE_TARGET:-}" != "${NATIVE_TARGET}" ]; then
    echo "error: Expected arm64 and native target ${NATIVE_TARGET}." >&2
    exit 1
fi

# IDE 已构建 Kotlin 时仍需把当前目标的许可证放入最终 App。
NATIVE_OUTPUT="${REPO_ROOT}/nativePlayer/build/${NATIVE_TARGET}"
LICENSES="${NATIVE_OUTPUT}/licenses"
if [ ! -f "${NATIVE_OUTPUT}/BoloNativePlayer.framework/BoloNativePlayer" ] ||
   [ ! -f "${LICENSES}/bolo__LICENSE" ] || [ ! -d "${LICENSES}/bolo__player" ]; then
    echo "error: Missing prepared native player for ${NATIVE_TARGET}." >&2
    exit 1
fi
APP_RESOURCES="${TARGET_BUILD_DIR:?}/${UNLOCALIZED_RESOURCES_FOLDER_PATH:?}"
mkdir -p "${APP_RESOURCES}"
# 仅同步 bolo__ 前缀的资源，不能对 App 根目录执行无过滤的删除。
rsync -a --delete --include='/bolo__*' --include='/bolo__*/***' --exclude='*' \
    "${LICENSES}/" "${APP_RESOURCES}/"
