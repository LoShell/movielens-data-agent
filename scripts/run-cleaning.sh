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
min_timestamp="${MOVIELENS_MIN_TIMESTAMP:-956703932}"
max_timestamp="${MOVIELENS_MAX_TIMESTAMP:-1046454590}"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
job_jar="${HADOOP_JOB_JAR:-${repo_root}/hadoop-jobs/target/hadoop-jobs-0.1.0-SNAPSHOT.jar}"
raw_root="/movielens/raw/${input_version}"
output_root="/movielens/cleaned/${output_version}"
report_path="/movielens/reports/${task_id}/cleaning.json"

if [[ ! -f "$job_jar" ]]; then
  echo "Hadoop job JAR not found: ${job_jar}" >&2
  exit 2
fi
for filename in ratings.dat users.dat movies.dat; do
  if ! hdfs dfs -test -e "${raw_root}/${filename}"; then
    echo "Missing HDFS input: ${raw_root}/${filename}" >&2
    exit 2
  fi
done
if hdfs dfs -test -e "$output_root"; then
  echo "Refusing to overwrite cleaned data version: ${output_root}" >&2
  exit 3
fi
if hdfs dfs -test -e "$report_path"; then
  echo "Refusing to overwrite cleaning report: ${report_path}" >&2
  exit 3
fi

hadoop jar "$job_jar" \
  com.jhz.movielens.hadoop.clean.MovieLensCleaningJob \
  --ratings "${raw_root}/ratings.dat" \
  --users "${raw_root}/users.dat" \
  --movies "${raw_root}/movies.dat" \
  --output-root "$output_root" \
  --report "$report_path" \
  --task-id "$task_id" \
  --input-version "$input_version" \
  --output-version "$output_version" \
  --rules-version "$rules_version" \
  --min-timestamp "$min_timestamp" \
  --max-timestamp "$max_timestamp"

echo "Cleaning report: ${report_path}"
hdfs dfs -cat "$report_path"
