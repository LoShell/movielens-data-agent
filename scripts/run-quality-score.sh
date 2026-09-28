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

# Fixed, reproducible calendar boundaries for every later iteration.
# T1 = 2002-01-01T00:00:00Z; T2 = 2003-01-01T00:00:00Z.
t1="${MOVIELENS_T1:-1009843200}"
t2="${MOVIELENS_T2:-1041379200}"

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
job_jar="${HADOOP_JOB_JAR:-${repo_root}/hadoop-jobs/target/hadoop-jobs-0.1.0-SNAPSHOT.jar}"
report_root="/movielens/reports/${task_id}"
score_report="${report_root}/five-dimension-quality.json"

if [[ ! -f "$job_jar" ]]; then
  echo "Hadoop job JAR not found: ${job_jar}" >&2
  exit 2
fi
for report in raw-quality.json relational-quality.json clean-raw-quality.json clean-relational-quality.json; do
  if ! hdfs dfs -test -e "${report_root}/${report}"; then
    echo "Missing quality input report: ${report_root}/${report}" >&2
    exit 2
  fi
done
if hdfs dfs -test -e "$score_report"; then
  echo "Refusing to overwrite score report: ${score_report}" >&2
  exit 3
fi

hadoop jar "$job_jar" \
  com.jhz.movielens.hadoop.score.QualityScoreTool \
  --raw-basic "${report_root}/raw-quality.json" \
  --raw-relational "${report_root}/relational-quality.json" \
  --clean-basic "${report_root}/clean-raw-quality.json" \
  --clean-relational "${report_root}/clean-relational-quality.json" \
  --report "$score_report" \
  --task-id "$task_id" \
  --input-version "$input_version" \
  --output-version "$output_version" \
  --rules-version "$rules_version" \
  --t1 "$t1" \
  --t2 "$t2"

echo "Five-dimension quality report: ${score_report}"
hdfs dfs -cat "$score_report"
