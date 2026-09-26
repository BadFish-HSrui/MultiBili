#!/bin/sh

set -eu

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
OUTPUT="${SCRIPT_OUTPUT_FILE_0:-}"

if [ -z "${OUTPUT}" ]; then
    echo "error: Missing output path for the iOS version header." >&2
    exit 1
fi

# 避免 Xcode 的 Swift 调试环境变量污染 Git 输出。
unset SWIFT_DEBUG_INFORMATION_FORMAT SWIFT_DEBUG_INFORMATION_VERSION

if [ "$(git -C "${REPO_ROOT}" rev-parse --is-shallow-repository)" = "true" ]; then
    echo "error: iOS version metadata requires a complete Git history." >&2
    exit 1
fi

APP_BUILD_NUMBER="$(git -C "${REPO_ROOT}" rev-list --count HEAD)"
APP_COMMIT_SHA="$(git -C "${REPO_ROOT}" rev-parse HEAD)"

case "${APP_BUILD_NUMBER}" in
    ''|0|*[!0-9]*)
        echo "error: Git did not return a valid iOS build number." >&2
        exit 1
        ;;
esac

case "${APP_COMMIT_SHA}" in
    ''|*[!0-9a-f]*)
        echo "error: Git did not return a valid iOS commit SHA." >&2
        exit 1
        ;;
esac

mkdir -p "$(dirname -- "${OUTPUT}")"
TEMP_OUTPUT="$(mktemp "${OUTPUT}.tmp.XXXXXX")"
trap 'rm -f "${TEMP_OUTPUT}"' EXIT

{
    printf '// Git commit: %s\n' "${APP_COMMIT_SHA}"
    printf '#define BOLO_APP_BUILD_NUMBER %s\n' "${APP_BUILD_NUMBER}"
} > "${TEMP_OUTPUT}"

if [ -f "${OUTPUT}" ] && cmp -s "${TEMP_OUTPUT}" "${OUTPUT}"; then
    rm -f "${TEMP_OUTPUT}"
else
    mv "${TEMP_OUTPUT}" "${OUTPUT}"
fi
trap - EXIT
