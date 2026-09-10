#!/bin/sh

set -eu

REPO_ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)"
VERSION_PROPERTIES="${REPO_ROOT}/version.properties"
OUTPUT="${REPO_ROOT}/iosApp/Configuration/GeneratedVersion.xcconfig"

if [ ! -f "${VERSION_PROPERTIES}" ]; then
    echo "error: Missing version.properties at ${VERSION_PROPERTIES}." >&2
    exit 1
fi

version_value() {
    key="$1"
    awk -F= -v key="${key}" '
        $1 == key {
            count++
            value=$2
        }
        END {
            if (count != 1 || value == "") exit 1
            print value
        }' "${VERSION_PROPERTIES}"
}

APP_VERSION_CORE="$(version_value APP_VERSION_CORE)"

if [ "$(git -C "${REPO_ROOT}" rev-parse --is-shallow-repository)" = "true" ]; then
    echo "error: iOS version metadata requires a complete Git history." >&2
    exit 1
fi

APP_BUILD_NUMBER="$(git -C "${REPO_ROOT}" rev-list --count HEAD)"
APP_COMMIT_SHA="$(git -C "${REPO_ROOT}" rev-parse HEAD)"

case "${APP_BUILD_NUMBER}" in
    ''|*[!0-9]*)
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

TEMP_OUTPUT="$(mktemp "${OUTPUT}.tmp.XXXXXX")"
trap 'rm -f "${TEMP_OUTPUT}"' EXIT

{
    printf 'APP_VERSION_CORE = %s\n' "${APP_VERSION_CORE}"
    printf 'APP_BUILD_NUMBER = %s\n' "${APP_BUILD_NUMBER}"
    printf 'APP_COMMIT_SHA = %s\n' "${APP_COMMIT_SHA}"
} > "${TEMP_OUTPUT}"

mv "${TEMP_OUTPUT}" "${OUTPUT}"
trap - EXIT
