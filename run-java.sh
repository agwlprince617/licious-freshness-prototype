#!/usr/bin/env bash
set -euo pipefail
prototype_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
classes_dir="$(mktemp -d "${TMPDIR:-/tmp}/freshness-java.XXXXXX")"
trap 'rm -rf -- "$classes_dir"' EXIT
javac --release 21 --add-modules jdk.httpserver -d "$classes_dir" "$prototype_dir"/java/src/demo/*.java
java --add-modules jdk.httpserver -cp "$classes_dir" demo.Server "$prototype_dir/dist" "${1:-8088}"
