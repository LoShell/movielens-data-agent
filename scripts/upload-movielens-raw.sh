#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <local-ml-1m-directory> <data-version>" >&2
  exit 2
fi

source_dir="$(realpath "$1")"
data_version="$2"
target_dir="/movielens/raw/${data_version}"

for filename in ratings.dat users.dat movies.dat; do
  if [[ ! -f "${source_dir}/${filename}" ]]; then
    echo "Missing required file: ${source_dir}/${filename}" >&2
    exit 2
  fi
done

if hdfs dfs -test -e "$target_dir"; then
  echo "Refusing to overwrite registered data version: ${target_dir}" >&2
  exit 3
fi

checksum_file="$(mktemp)"
trap 'rm -f "$checksum_file"' EXIT

(
  cd "$source_dir"
  sha256sum ratings.dat users.dat movies.dat > "$checksum_file"
)

hdfs dfs -mkdir -p "$target_dir"
hdfs dfs -put "${source_dir}/ratings.dat" "$target_dir/ratings.dat"
hdfs dfs -put "${source_dir}/users.dat" "$target_dir/users.dat"
hdfs dfs -put "${source_dir}/movies.dat" "$target_dir/movies.dat"
hdfs dfs -put "$checksum_file" "$target_dir/_checksums.sha256"

echo "Uploaded immutable raw data version: ${target_dir}"
hdfs dfs -ls "$target_dir"
