#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_FILE="${1:-${SCRIPT_DIR}/../.env.terraform}"

if ! command -v terraform >/dev/null 2>&1; then
  echo "terraform executable is required" >&2
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq executable is required" >&2
  exit 1
fi

temp_file="$(mktemp)"
trap 'rm -f "$temp_file"' EXIT

terraform -chdir="${SCRIPT_DIR}" output -json >"${temp_file}"

jq -r '
  to_entries
  | map(select(.value.value != null))
  | .[]
  | select(.value.type == "string")
  | "\(.key)=\(.value.value)"
' "${temp_file}" >"${OUTPUT_FILE}"

echo "Wrote Terraform outputs to ${OUTPUT_FILE}"
