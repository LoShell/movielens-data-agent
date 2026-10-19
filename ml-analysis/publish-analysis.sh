#!/usr/bin/env bash
set -euo pipefail

version="${1:-}"
if [[ ! "$version" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "Usage: $0 <registered-clean-version>" >&2
  exit 2
fi
source="artifacts/iteration2/$version"
destination="/movielens/analysis/iteration2/$version"
if [[ ! -f "$source/experiment-report.json" ]]; then
  echo "Missing successful local experiment report" >&2
  exit 2
fi
if hdfs dfs -test -e "$destination"; then
  if ! hdfs dfs -test -e "$destination/experiment-report.json"; then
    echo "HDFS analysis destination is incomplete: $destination" >&2
    exit 2
  fi
  local_digest="$(sha256sum "$source/experiment-report.json" | awk '{print $1}')"
  remote_digest="$(hdfs dfs -cat "$destination/experiment-report.json" | sha256sum | awk '{print $1}')"
  if [[ "$local_digest" != "$remote_digest" ]]; then
    echo "HDFS analysis version already exists with a different report" >&2
    exit 2
  fi
  echo "Existing HDFS analysis: $destination"
  exit 0
fi

parent="/movielens/analysis/iteration2"
staging="$parent/.${version}.staging.$$"
hdfs dfs -mkdir -p "$parent"
hdfs dfs -put "$source" "$staging"
hdfs dfs -mv "$staging" "$destination"
echo "Published $destination"
