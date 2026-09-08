#!/usr/bin/env bash
set -euo pipefail
backend_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
checkout_key="$(printf '%s' "$backend_dir" | sha256sum | cut -c1-16)"
output_dir="${LOLFM_TEST_BUILD_DIR:-/tmp/lolfm-backend-${UID}-${checkout_key}/build}"
if [[ "$output_dir" != /* ]]; then
    echo 'LOLFM_TEST_BUILD_DIR must be an absolute path' >&2
    exit 2
fi
mkdir -p -- "$output_dir"
# Serialize runs using this output; never race Gradle test/report writers.
exec 9>"$output_dir/.test-run.lock"
flock -n 9 || { echo "Test output is already in use: $output_dir" >&2; exit 2; }
cd -- "$backend_dir"
printf 'Live sources: %s\nBuild output: %s\n' "$backend_dir" "$output_dir"
# Only test is forced to execute. Compilation/resources may reuse valid incremental outputs.
exec ./gradlew --project-cache-dir "$output_dir/.gradle-project-cache" test --rerun --no-build-cache --console=plain --no-daemon "-PbackendBuildDir=$output_dir" "$@"
