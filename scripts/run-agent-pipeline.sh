#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 4 ]]; then
  echo "Usage: $0 <task-id> <input-version> <output-version> <rules-version>" >&2
  exit 2
fi

task_id="$1"
input_version="$2"
output_version="$3"
rules_version="$4"
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

echo "[1/5] Scanning raw records"
bash "${repo_root}/scripts/run-raw-quality-scan.sh" "$task_id" "$input_version" "$rules_version"

echo "[2/5] Checking raw uniqueness and cross-table consistency"
bash "${repo_root}/scripts/run-relational-quality-scan.sh" "$task_id" "$input_version" "$rules_version"

echo "[3/5] Cleaning and quarantining records"
bash "${repo_root}/scripts/run-cleaning.sh" "$task_id" "$input_version" "$output_version" "$rules_version"

echo "[4/5] Re-scanning the cleaned version"
bash "${repo_root}/scripts/run-clean-quality-scan.sh" "$task_id" "$output_version" "$rules_version"

echo "[5/5] Calculating five-dimension scores"
bash "${repo_root}/scripts/run-quality-score.sh" "$task_id" "$input_version" "$output_version" "$rules_version"

echo "PIPELINE_SUCCEEDED task=${task_id} output=${output_version}"
