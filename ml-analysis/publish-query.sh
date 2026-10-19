#!/usr/bin/env bash
set -euo pipefail

version="${1:-}"
query="${2:-}"
if [[ ! "$version" =~ ^[A-Za-z0-9._-]+$ || ! "$query" =~ ^[a-f0-9]{16}$ ]]; then
  echo "Usage: $0 <registered-clean-version> <query-version>" >&2
  exit 2
fi
source="artifacts/iteration2/$version/queries/$query.json"
destination="/movielens/analysis/iteration2/$version/queries/$query.json"
if [[ ! -f "$source" ]]; then
  echo "Missing local query report: $source" >&2
  exit 2
fi
hdfs dfs -mkdir -p "/movielens/analysis/iteration2/$version/queries"
if hdfs dfs -test -e "$destination"; then
  local_digest="$(sha256sum "$source" | awk '{print $1}')"
  remote_digest="$(hdfs dfs -cat "$destination" | sha256sum | awk '{print $1}')"
  if [[ "$local_digest" != "$remote_digest" ]]; then
    echo "HDFS query version already exists with a different report" >&2
    exit 2
  fi
  exit 0
fi
hdfs dfs -put "$source" "$destination"
echo "Published $destination"
