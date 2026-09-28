#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "Usage: $0 <task-id> <clean-version> <rules-version>" >&2
  exit 2
fi

task_id="$1"
clean_version="$2"
rules_version="$3"
min_timestamp="${MOVIELENS_MIN_TIMESTAMP:-956703932}"
max_timestamp="${MOVIELENS_MAX_TIMESTAMP:-1046454590}"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
job_jar="${HADOOP_JOB_JAR:-${repo_root}/hadoop-jobs/target/hadoop-jobs-0.1.0-SNAPSHOT.jar}"
clean_root="/movielens/cleaned/${clean_version}"
raw_report="/movielens/reports/${task_id}/clean-raw-quality.json"
relational_report="/movielens/reports/${task_id}/clean-relational-quality.json"

if [[ ! -f "$job_jar" ]]; then
  echo "Hadoop job JAR not found: ${job_jar}" >&2
  exit 2
fi
for dataset in ratings users movies; do
  if ! hdfs dfs -test -e "${clean_root}/${dataset}/clean"; then
    echo "Missing cleaned HDFS input: ${clean_root}/${dataset}/clean" >&2
    exit 2
  fi
done
for report in "$raw_report" "$relational_report"; do
  if hdfs dfs -test -e "$report"; then
    echo "Refusing to overwrite existing report: ${report}" >&2
    exit 3
  fi
done

hadoop jar "$job_jar" \
  com.jhz.movielens.hadoop.scan.RawQualityScanJob \
  -Dmovielens.input.encoding=UTF-8 \
  --ratings "${clean_root}/ratings/clean" \
  --users "${clean_root}/users/clean" \
  --movies "${clean_root}/movies/clean" \
  --report "$raw_report" \
  --task-id "$task_id" \
  --data-version "$clean_version" \
  --rules-version "$rules_version"

hadoop jar "$job_jar" \
  com.jhz.movielens.hadoop.scan.RelationalQualityScanJob \
  -Dmovielens.input.encoding=UTF-8 \
  --ratings "${clean_root}/ratings/clean" \
  --users "${clean_root}/users/clean" \
  --movies "${clean_root}/movies/clean" \
  --report "$relational_report" \
  --task-id "$task_id" \
  --data-version "$clean_version" \
  --rules-version "$rules_version" \
  --min-timestamp "$min_timestamp" \
  --max-timestamp "$max_timestamp"

echo "Clean raw quality report: ${raw_report}"
hdfs dfs -cat "$raw_report"
echo "Clean relational quality report: ${relational_report}"
hdfs dfs -cat "$relational_report"
