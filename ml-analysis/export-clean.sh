#!/usr/bin/env bash
set -euo pipefail

version="${1:-}"
if [[ ! "$version" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "Usage: $0 <registered-clean-version>" >&2
  exit 2
fi

destination="data/processed/$version"
if [[ -e "$destination" ]]; then
  echo "Refusing to overwrite $destination" >&2
  exit 2
fi
staging="data/processed/.${version}.staging.$$"
mkdir -p "$staging"
for dataset in ratings users movies; do
  source="/movielens/cleaned/$version/$dataset/clean/part-*"
  if ! hdfs dfs -test -e "/movielens/cleaned/$version/$dataset/clean/part-r-00000"; then
    echo "Missing HDFS clean dataset: $source" >&2
    exit 2
  fi
  hdfs dfs -get "$source" "$staging/$dataset.dat"
done
(
  cd "$staging"
  sha256sum ./*.dat > SHA256SUMS
)
mv "$staging" "$destination"
echo "Exported $version to $destination"
