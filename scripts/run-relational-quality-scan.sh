#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "Usage: $0 <task-id> <data-version> <rules-version>" >&2
  exit 2
fi

task_id="$1"
data_version="$2"
rules_version="$3"
min_timestamp="${MOVIELENS_MIN_TIMESTAMP:-956703932}"
max_timestamp="${MOVIELENS_MAX_TIMESTAMP:-1046454590}"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
job_jar="${HADOOP_JOB_JAR:-${repo_root}/hadoop-jobs/target/hadoop-jobs-0.1.0-SNAPSHOT.jar}"
raw_root="/movielens/raw/${data_version}"
report_path="/movielens/reports/${task_id}/relational-quality.json"

if [[ ! -f "$job_jar" ]]; then
  echo "Hadoop job JAR not found: ${job_jar}" >&2
  echo "Build it first with: ./mvnw -pl hadoop-jobs -am clean package" >&2
  exit 2
fi

for filename in ratings.dat users.dat movies.dat; do
  if ! hdfs dfs -test -e "${raw_root}/${filename}"; then
    echo "Missing HDFS input: ${raw_root}/${filename}" >&2
    exit 2
  fi
done

if hdfs dfs -test -e "$report_path"; then
  echo "Refusing to overwrite existing task report: ${report_path}" >&2
  exit 3
fi

hadoop jar "$job_jar" \
  com.jhz.movielens.hadoop.scan.RelationalQualityScanJob \
  --ratings "${raw_root}/ratings.dat" \
  --users "${raw_root}/users.dat" \
  --movies "${raw_root}/movies.dat" \
  --report "$report_path" \
  --task-id "$task_id" \
  --data-version "$data_version" \
  --rules-version "$rules_version" \
  --min-timestamp "$min_timestamp" \
  --max-timestamp "$max_timestamp"

echo "Relational quality report: ${report_path}"
hdfs dfs -cat "$report_path"
