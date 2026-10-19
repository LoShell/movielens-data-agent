"""Reproducible iteration-2 experiments on a verified iteration-1 clean export."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import time
import uuid
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
from sklearn.cluster import KMeans
from sklearn.decomposition import NMF, PCA
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    adjusted_rand_score,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
    silhouette_score,
)
from sklearn.mixture import GaussianMixture
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.tree import DecisionTreeClassifier


T1 = 1009843200
T2 = 1041379200
FEATURE_VERSION = "event-history-v1"
PROFILE_VERSION = "user-genre-v1"
SEED = 42
EXPECTED_VERSION = "clean-agent-20260929-124232-bfc440f2"


def _rows(path: Path, fields: int):
    if not path.is_file():
        raise ValueError(f"Missing clean input: {path}")
    with path.open("r", encoding="utf-8") as source:
        for number, line in enumerate(source, 1):
            row = line.rstrip("\r\n").split("::")
            if len(row) != fields:
                raise ValueError(f"Malformed clean input: {path}:{number}")
            yield row


def _digest(path: Path) -> str:
    checksum = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1 << 20), b""):
            checksum.update(block)
    return checksum.hexdigest()


def load_clean(data_dir: Path, report_path: Path):
    report = json.loads(report_path.read_text(encoding="utf-8"))
    if report.get("quality", {}).get("status") != "SUCCEEDED":
        raise ValueError("Iteration-1 report is not successful")
    version = report.get("outputVersion")
    if version != EXPECTED_VERSION or data_dir.name != version:
        raise ValueError("Clean data version does not match the registered iteration-1 version")
    boundaries = report["quality"]["timeBoundaries"]
    if boundaries["T1"]["epochSeconds"] != T1 or boundaries["T2"]["epochSeconds"] != T2:
        raise ValueError("Iteration-1 time boundaries do not match")

    paths = {name: data_dir / f"{name}.dat" for name in ("ratings", "users", "movies")}
    users = {}
    for row in _rows(paths["users"], 5):
        user_id = int(row[0])
        if user_id in users or row[1] not in {"F", "M"} or not 0 <= int(row[3]) <= 20:
            raise ValueError("Invalid or duplicate clean user")
        users[user_id] = (row[1], int(row[3]))

    movies = {}
    for row in _rows(paths["movies"], 3):
        movie_id = int(row[0])
        if movie_id in movies or not row[2]:
            raise ValueError("Invalid or duplicate clean movie")
        movies[movie_id] = tuple(row[2].split("|"))

    ratings = []
    keys = set()
    for row in _rows(paths["ratings"], 4):
        user_id, movie_id, rating, timestamp = map(int, row)
        key = (user_id, movie_id, timestamp)
        if (key in keys or user_id not in users or movie_id not in movies
                or not 1 <= rating <= 5):
            raise ValueError("Invalid, duplicate or unreferenced clean rating")
        keys.add(key)
        ratings.append((timestamp, user_id, movie_id, rating))

    counts = report["cleaning"]["actions"]
    for name, actual in (("users", len(users)), ("movies", len(movies)),
                         ("ratings", len(ratings))):
        if actual != counts[name]["cleanWritten"]:
            raise ValueError(f"{name} row count does not match iteration-1 report")
    ratings.sort()
    checksums = {name: _digest(path) for name, path in paths.items()}
    manifest_path = data_dir / "SHA256SUMS"
    if not manifest_path.is_file():
        raise ValueError("Missing HDFS export SHA256SUMS manifest")
    manifest = {}
    for line in manifest_path.read_text(encoding="utf-8").splitlines():
        digest, filename = line.split(maxsplit=1)
        manifest[Path(filename.lstrip("*")).name] = digest.lower()
    if manifest != {f"{name}.dat": digest for name, digest in checksums.items()}:
        raise ValueError("Clean export SHA-256 manifest does not match input files")
    provenance = {
        "dataVersion": version,
        "governanceTaskId": report["taskId"],
        "rulesVersion": report["rulesVersion"],
        "T1": T1,
        "T2": T2,
        "rowCounts": {"users": len(users), "movies": len(movies), "ratings": len(ratings)},
        "sha256": checksums,
    }
    return users, movies, ratings, provenance


def _feature_row(user_id, movie_id, users, movies, genres, history):
    gender, occupation = users[user_id]
    row = np.zeros(2 + 21 + len(genres) + 3, dtype=np.float32)
    row[0 if gender == "F" else 1] = 1
    row[2 + occupation] = 1
    genre_start = 23
    for genre in movies[movie_id]:
        row[genre_start + genres[genre]] = 1
    user_sum, user_count = history["users"].get(user_id, (0, 0))
    _, movie_count = history["movies"].get(movie_id, (0, 0))
    row[-3] = user_sum / user_count if user_count else 3.5
    row[-2] = math.log1p(user_count)
    row[-1] = math.log1p(movie_count)
    return row


def build_classification(users, movies, ratings):
    genres = {name: index for index, name in enumerate(
        sorted({genre for movie_genres in movies.values() for genre in movie_genres}))}
    history = {"users": defaultdict(lambda: [0, 0]),
               "movies": defaultdict(lambda: [0, 0])}
    features = {split: [] for split in ("train", "validation", "test")}
    labels = {split: [] for split in features}
    index = 0
    while index < len(ratings):
        timestamp = ratings[index][0]
        end = index + 1
        while end < len(ratings) and ratings[end][0] == timestamp:
            end += 1
        split = "train" if timestamp <= T1 else "validation" if timestamp <= T2 else "test"
        for _, user_id, movie_id, rating in ratings[index:end]:
            features[split].append(_feature_row(user_id, movie_id, users, movies, genres, history))
            labels[split].append(int(rating >= 4))
        # Holdout labels are never used to form features. Equal-timestamp
        # training events also cannot see each other.
        if split == "train":
            for _, user_id, movie_id, rating in ratings[index:end]:
                history["users"][user_id][0] += rating
                history["users"][user_id][1] += 1
                history["movies"][movie_id][0] += rating
                history["movies"][movie_id][1] += 1
        index = end
    arrays = {split: (np.asarray(features[split], dtype=np.float32),
                      np.asarray(labels[split], dtype=np.int8)) for split in features}
    if any(len(y) == 0 or len(np.unique(y)) != 2 for _, y in arrays.values()):
        raise ValueError("Every time split must contain both classes")
    return arrays, genres, history


def _metrics(labels, probabilities, threshold):
    predictions = probabilities >= threshold
    return {
        "count": int(len(labels)),
        "positiveRate": float(np.mean(labels)),
        "threshold": float(threshold),
        "F1": float(f1_score(labels, predictions, zero_division=0)),
        "ROC_AUC": float(roc_auc_score(labels, probabilities)),
        "precision": float(precision_score(labels, predictions, zero_division=0)),
        "recall": float(recall_score(labels, predictions, zero_division=0)),
        "confusionMatrix": confusion_matrix(labels, predictions, labels=[0, 1]).tolist(),
    }


def _best_threshold(labels, probabilities):
    candidates = np.arange(0.2, 0.801, 0.025)
    return float(max(candidates, key=lambda threshold: (
        f1_score(labels, probabilities >= threshold, zero_division=0), threshold)))


def run_classification(arrays, output_dir: Path, provenance, genres, history, users, movies):
    x_train, y_train = arrays["train"]
    x_val, y_val = arrays["validation"]
    x_test, y_test = arrays["test"]
    if any(not np.isfinite(x).all() for x, _ in arrays.values()):
        raise ValueError("Non-finite classification features")
    candidates = {
        "logistic_regression": [
            ("C=0.1", make_pipeline(StandardScaler(), LogisticRegression(
                C=0.1, max_iter=500, random_state=SEED))),
            ("C=1.0", make_pipeline(StandardScaler(), LogisticRegression(
                C=1.0, max_iter=500, random_state=SEED))),
        ],
        "decision_tree": [
            ("depth=8,leaf=100", DecisionTreeClassifier(
                max_depth=8, min_samples_leaf=100, random_state=SEED)),
            ("depth=12,leaf=100", DecisionTreeClassifier(
                max_depth=12, min_samples_leaf=100, random_state=SEED)),
        ],
    }
    result = {"featureVersion": FEATURE_VERSION, "historyPolicy":
              "train strictly earlier; validation/test frozen at T1", "models": {}}
    for name, configurations in candidates.items():
        trials = []
        best = None
        for setting, model in configurations:
            start = time.perf_counter()
            model.fit(x_train, y_train)
            duration = time.perf_counter() - start
            probabilities = model.predict_proba(x_val)[:, 1]
            threshold = _best_threshold(y_val, probabilities)
            metrics = _metrics(y_val, probabilities, threshold)
            trial = {"parameters": setting, "trainingSeconds": duration,
                     "validation": metrics}
            trials.append(trial)
            rank = (metrics["F1"], metrics["ROC_AUC"])
            if best is None or rank > best[0]:
                best = (rank, setting, model, threshold, metrics)
        _, setting, model, threshold, validation = best
        test = _metrics(y_test, model.predict_proba(x_test)[:, 1], threshold)
        inputs = "".join(provenance["sha256"][key] for key in ("ratings", "users", "movies"))
        model_version = hashlib.sha256((inputs + FEATURE_VERSION + name + setting).encode()).hexdigest()[:16]
        joblib.dump({"model": model, "modelVersion": model_version,
                     "dataVersion": provenance["dataVersion"], "featureVersion": FEATURE_VERSION,
                     "genres": genres, "users": users, "movies": movies,
                     "history": {kind: dict(values) for kind, values in history.items()},
                     "threshold": threshold}, output_dir / f"{name}.joblib")
        result["models"][name] = {"modelVersion": model_version,
                                   "selectedParameters": setting,
                                   "validation": validation, "test": test,
                                   "trials": trials}
    return result


def build_profiles(movies, ratings):
    genres = {name: index for index, name in enumerate(
        sorted({genre for movie_genres in movies.values() for genre in movie_genres}))}
    user_ids = sorted({user_id for timestamp, user_id, _, _ in ratings if timestamp <= T1})
    positions = {user_id: index for index, user_id in enumerate(user_ids)}
    count = np.zeros((len(user_ids), len(genres)), dtype=np.float32)
    high = np.zeros_like(count)
    total = np.zeros(len(user_ids), dtype=np.int32)
    for timestamp, user_id, movie_id, rating in ratings:
        if timestamp > T1:
            break
        row = positions[user_id]
        total[row] += 1
        for genre in movies[movie_id]:
            column = genres[genre]
            count[row, column] += 1
            high[row, column] += int(rating >= 4)
    # Beta(1,1) smoothing gives a defined value for unseen genres.
    profiles = (high + 1) / (count + 2)
    return user_ids, profiles, total, genres


def _cluster(name, matrix, clusters):
    if name == "kmeans":
        estimator = KMeans(n_clusters=clusters, n_init=10, random_state=SEED)
        parameters = {"requestedClusters": clusters, "nInit": 10, "randomSeed": SEED}
    else:
        estimator = GaussianMixture(n_components=clusters, covariance_type="diag",
                                    reg_covar=1e-5, random_state=SEED)
        parameters = {"requestedClusters": clusters, "covarianceType": "diag",
                      "regularization": 1e-5, "randomSeed": SEED}
    start = time.perf_counter()
    labels = estimator.fit_predict(matrix)
    seconds = time.perf_counter() - start
    unique = len(np.unique(labels))
    score = float(silhouette_score(matrix, labels, sample_size=min(1000, len(matrix)),
                                   random_state=SEED)) if 1 < unique < len(matrix) else None
    return estimator, labels, {"algorithm": name, "clusterCount": unique,
                    "parameters": parameters,
                    "sizes": np.bincount(labels, minlength=clusters).tolist(),
                    "silhouetteSample1000": score, "fitSeconds": seconds}


def run_profiles(movies, ratings, output_dir: Path, provenance):
    user_ids, profiles, counts, genres = build_profiles(movies, ratings)
    if len(user_ids) < 10 or len(genres) < 3:
        raise ValueError("Insufficient training-period users or genres")
    dimensions = min(8, profiles.shape[1] - 1)
    projections = {"original": profiles}
    reduction = {}
    source_hash = hashlib.sha256((provenance["sha256"]["ratings"]
                                  + provenance["sha256"]["movies"]
                                  + PROFILE_VERSION).encode()).hexdigest()
    for name, estimator in (
        ("pca", PCA(n_components=dimensions, random_state=SEED)),
        ("nmf", NMF(n_components=dimensions, init="nndsvda", random_state=SEED,
                    max_iter=5000)),
    ):
        start = time.perf_counter()
        coordinates = estimator.fit_transform(profiles)
        seconds = time.perf_counter() - start
        if name == "nmf" and estimator.n_iter_ >= estimator.max_iter:
            raise ValueError(f"NMF did not converge within {estimator.max_iter} iterations")
        reconstructed = estimator.inverse_transform(coordinates)
        retention = (float(np.sum(estimator.explained_variance_ratio_)) if name == "pca"
                     else float(1 - np.linalg.norm(profiles - reconstructed) ** 2
                                / np.linalg.norm(profiles) ** 2))
        projections[name] = coordinates
        model_version = hashlib.sha256((source_hash + name + str(dimensions)
                                        + (f"max_iter={estimator.max_iter}" if name == "nmf" else "")
                                        ).encode()).hexdigest()[:16]
        reduction[name] = {"modelVersion": model_version,
                           "dimensions": dimensions, "informationRetention": retention,
                           "parameters": ({"components": dimensions, "randomSeed": SEED}
                                          if name == "pca" else {"components": dimensions,
                                          "initialization": "nndsvda", "maxIterations": 5000,
                                          "actualIterations": int(estimator.n_iter_),
                                          "randomSeed": SEED}),
                           "retentionDefinition": "explained variance" if name == "pca"
                           else "1 - squared relative reconstruction error",
                           "fitSeconds": seconds,
                           "coordinateBytes": int(coordinates.nbytes)}
        joblib.dump({"model": estimator, "modelVersion": model_version,
                     "profileVersion": PROFILE_VERSION,
                     "dataVersion": provenance["dataVersion"]}, output_dir / f"{name}.joblib")
    comparison = {}
    labels_by_method = {}
    for method in ("kmeans", "gmm"):
        baseline_labels = None
        comparison[method] = {}
        for name, matrix in projections.items():
            estimator, labels, metrics = _cluster(method, matrix, clusters=5)
            projection_version = reduction[name]["modelVersion"] if name != "original" else ""
            cluster_version = hashlib.sha256((source_hash + method + name + "5"
                                              + projection_version).encode()).hexdigest()[:16]
            metrics["modelVersion"] = cluster_version
            if name == "original":
                baseline_labels = labels
            else:
                metrics["ARIvsOriginal"] = float(adjusted_rand_score(baseline_labels, labels))
            comparison[method][name] = metrics
            labels_by_method[f"{method}_{name}"] = labels
            joblib.dump({"algorithm": method, "projection": name, "model": estimator,
                         "modelVersion": cluster_version, "labels": labels,
                         "profileVersion": PROFILE_VERSION,
                         "dataVersion": provenance["dataVersion"],
                         "userIds": user_ids}, output_dir / f"{method}_{name}.joblib")
    np.savez_compressed(output_dir / "user_profiles.npz", user_ids=np.array(user_ids),
                        profiles=profiles, rating_counts=counts,
                        pca=projections["pca"], nmf=projections["nmf"],
                        **labels_by_method)
    return {"profileVersion": PROFILE_VERSION, "userCount": len(user_ids),
            "genreCount": len(genres), "genres": list(genres),
            "profileDefinition": "per-genre training high-rating share with Beta(1,1) smoothing",
            "clusterCountRequested": 5, "dimensionalityReduction": reduction,
            "clustering": comparison}


def run(args):
    data_dir = Path(args.data_dir).resolve()
    output_dir = Path(args.output_dir).resolve()
    if output_dir.exists():
        raise ValueError(f"Refusing to overwrite existing output: {output_dir}")
    users, movies, ratings, provenance = load_clean(data_dir, Path(args.report))
    output_dir.parent.mkdir(parents=True, exist_ok=True)
    staging = output_dir.parent / f".{output_dir.name}.staging-{uuid.uuid4().hex[:8]}"
    staging.mkdir()
    arrays, genres, history = build_classification(users, movies, ratings)
    report = {"status": "SUCCEEDED", "experimentVersion": "iteration2-v1",
              "generatedAt": datetime.now(timezone.utc).isoformat(),
              "provenance": provenance,
              "splitCounts": {split: len(y) for split, (_, y) in arrays.items()},
              "classification": run_classification(arrays, staging, provenance, genres, history,
                                                    users, movies),
              "profiles": run_profiles(movies, ratings, staging, provenance)}
    (staging / "experiment-report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    staging.rename(output_dir)
    print(json.dumps({"status": "SUCCEEDED", "report": str(output_dir / "experiment-report.json"),
                      "splitCounts": report["splitCounts"]}))


def _selection(args, profile_ids):
    requested = []
    if args.user_id is not None:
        requested.append(args.user_id)
    if args.user_ids:
        requested.extend(int(value) for value in args.user_ids.split(","))
    requested = list(dict.fromkeys(requested))
    if len(requested) > 100:
        raise ValueError("At most 100 users may be requested at once")
    positions = {int(user_id): index for index, user_id in enumerate(profile_ids)}
    missing = [user_id for user_id in requested if user_id not in positions]
    if missing:
        raise ValueError(f"Users have no training-period profile: {missing[:10]}")
    return requested, positions


def _summaries(profiles, labels, user_ids, counts, genres):
    result = []
    for group in sorted(set(map(int, labels))):
        members = np.flatnonzero(labels == group)
        center = profiles[members].mean(axis=0)
        top = np.argsort(center)[-3:][::-1]
        representative = members[np.argmin(np.linalg.norm(profiles[members] - center, axis=1))]
        result.append({"group": group, "size": int(len(members)),
                       "topGenres": [genres[int(index)] for index in top],
                       "representativeUserId": int(user_ids[representative]),
                       "meanHistoryCount": float(counts[members].mean()),
                       "description": "Group summary describes training-period genre preferences only."})
    return result


def _retention(model, coordinates, profiles, dimensions, algorithm):
    if algorithm == "pca":
        return float(np.sum(model.explained_variance_ratio_[:dimensions]))
    reconstructed = coordinates[:, :dimensions] @ model.components_[:dimensions]
    return float(1 - np.linalg.norm(profiles - reconstructed) ** 2
                 / np.linalg.norm(profiles) ** 2)


def _plot_points(coordinates, labels, user_ids, requested):
    if coordinates.shape[1] < 2:
        raise ValueError("A two-dimensional visualization requires at least two fitted components")
    sample = np.linspace(0, len(user_ids) - 1, min(80, len(user_ids)), dtype=int)
    positions = {int(user_id): index for index, user_id in enumerate(user_ids)}
    sample = sorted(set(map(int, sample)) | {positions[user_id] for user_id in requested})
    return [{"userId": int(user_ids[index]), "x": float(coordinates[index, 0]),
             "y": float(coordinates[index, 1]), "group": int(labels[index]),
             "selected": int(user_ids[index]) in requested} for index in sample]


def inspect(args):
    artifact = Path(args.output_dir).resolve()
    report = json.loads((artifact / "experiment-report.json").read_text(encoding="utf-8"))
    if report.get("status") != "SUCCEEDED" or report["provenance"]["dataVersion"] != EXPECTED_VERSION:
        raise ValueError("Missing or incompatible successful experiment report")
    if args.kind == "classification":
        selected = report["classification"]["models"][args.algorithm]
        payload = {"status": "SUCCEEDED", "kind": args.kind, "algorithm": args.algorithm,
                   "provenance": report["provenance"], "splitCounts": report["splitCounts"],
                   "evaluation": selected}
        if args.user_id is not None or args.movie_id is not None or args.prediction_time is not None:
            if args.user_id is None or args.movie_id is None:
                raise ValueError("user-id and movie-id must be provided together")
            prediction_time = args.prediction_time or T1 + 1
            if prediction_time <= T1:
                raise ValueError("This model serves predictions after the frozen T1 history only")
            bundle = joblib.load(artifact / f"{args.algorithm}.joblib")
            if bundle["dataVersion"] != EXPECTED_VERSION:
                raise ValueError("Model and data versions disagree")
            if args.user_id not in bundle["users"] or args.movie_id not in bundle["movies"]:
                raise ValueError("Unknown user or movie; no supported cold-start prediction")
            features = _feature_row(args.user_id, args.movie_id, bundle["users"],
                                    bundle["movies"], bundle["genres"], bundle["history"])
            probability = float(bundle["model"].predict_proba(features.reshape(1, -1))[0, 1])
            user_count = bundle["history"]["users"].get(args.user_id, [0, 0])[1]
            movie_count = bundle["history"]["movies"].get(args.movie_id, [0, 0])[1]
            payload["prediction"] = {"userId": args.user_id, "movieId": args.movie_id,
                                     "predictionTime": prediction_time,
                                     "estimatedHighRatingProbability": probability,
                                     "predictedHighRating": probability >= bundle["threshold"],
                                     "threshold": bundle["threshold"],
                                     "modelVersion": bundle["modelVersion"],
                                     "historyPolicy": "frozen at T1",
                                     "userHistoryCount": user_count,
                                     "movieHistoryCount": movie_count,
                                     "coldStartUser": user_count == 0,
                                     "coldStartMovie": movie_count == 0,
                                     "predictionTimeDefaulted": args.prediction_time is None}
    elif args.kind == "clustering":
        profiles_file = np.load(artifact / "user_profiles.npz")
        profile_ids = profiles_file["user_ids"]
        requested, positions = _selection(args, profile_ids)
        profiles = profiles_file["profiles"]
        counts = profiles_file["rating_counts"]
        cluster_count = args.clusters or 5
        if not 2 <= cluster_count <= min(20, len(profile_ids) - 1):
            raise ValueError("clusters must be between 2 and 20 and below the profile count")
        results = {}
        assignments = None
        summaries = None
        for space in ("original", "pca", "nmf"):
            matrix = profiles if space == "original" else profiles_file[space]
            estimator, labels, metrics = _cluster(args.algorithm, matrix, cluster_count)
            results[space] = metrics
            if space == "original":
                assignments = labels
                summaries = _summaries(profiles, labels, profile_ids, counts,
                                       report["profiles"]["genres"])
                for user_id in requested:
                    index = positions[user_id]
                    membership = {"userId": user_id, "group": int(labels[index]),
                                  "historyCount": int(counts[index]),
                                  "historyInsufficient": bool(counts[index] < 10),
                                  "noisePoint": False}
                    if args.algorithm == "gmm":
                        membership["membershipProbability"] = float(
                            estimator.predict_proba(matrix[index:index + 1]).max())
                    results.setdefault("users", []).append(membership)
            else:
                metrics["ARIvsOriginal"] = float(adjusted_rand_score(assignments, labels))
        payload = {"status": "SUCCEEDED", "kind": args.kind, "algorithm": args.algorithm,
                   "provenance": report["provenance"],
                   "profileVersion": report["profiles"]["profileVersion"],
                   "parameters": {"clusters": cluster_count, "randomSeed": SEED},
                   "groupSummaries": summaries,
                   "userAssignments": results.pop("users", []),
                   "results": results}
    else:
        profiles_file = np.load(artifact / "user_profiles.npz")
        profile_ids = profiles_file["user_ids"]
        requested, positions = _selection(args, profile_ids)
        profiles = profiles_file["profiles"]
        full_coordinates = profiles_file[args.algorithm]
        model = joblib.load(artifact / f"{args.algorithm}.joblib")["model"]
        max_dimensions = full_coordinates.shape[1]
        if args.dimension is not None and args.retention is not None:
            raise ValueError("Choose either target dimension or retention threshold")
        if args.retention is not None:
            if not 0 < args.retention <= 1:
                raise ValueError("retention threshold must be in (0, 1]")
            eligible = [dimension for dimension in range(1, max_dimensions + 1)
                        if _retention(model, full_coordinates, profiles, dimension,
                                      args.algorithm) >= args.retention]
            if not eligible:
                raise ValueError("Retention threshold is unattainable within the fitted dimensions")
            dimensions = eligible[0]
        else:
            dimensions = args.dimension or max_dimensions
        if not 1 <= dimensions <= max_dimensions:
            raise ValueError(f"dimension must be between 1 and {max_dimensions}")
        cluster_count = args.clusters or 5
        if not 2 <= cluster_count <= min(20, len(profile_ids) - 1):
            raise ValueError("Invalid clustering group count")
        coordinate_matrix = full_coordinates[:, :dimensions]
        genre_names = report["profiles"]["genres"]
        directions = []
        for axis in range(min(3, dimensions)):
            weights = model.components_[axis]
            positive = np.argsort(weights)[-3:][::-1]
            direction = {"component": axis + 1,
                         "strongestGenres": [genre_names[int(index)] for index in positive]}
            if args.algorithm == "pca":
                negative = np.argsort(weights)[:3]
                direction["oppositeGenres"] = [genre_names[int(index)] for index in negative]
            directions.append(direction)
        methods = [args.cluster_algorithm] if args.cluster_algorithm else ["kmeans", "gmm"]
        comparison = {}
        plot_labels = None
        for method in methods:
            _, baseline, baseline_metrics = _cluster(method, profiles, cluster_count)
            _, compressed, compressed_metrics = _cluster(method, coordinate_matrix, cluster_count)
            compressed_metrics["ARIvsOriginal"] = float(adjusted_rand_score(baseline, compressed))
            comparison[method] = {"original": baseline_metrics, "reduced": compressed_metrics}
            if plot_labels is None:
                plot_labels = compressed
        coords = [{"userId": user_id,
                   "values": [float(value) for value in coordinate_matrix[positions[user_id]]],
                   "historyCount": int(profiles_file["rating_counts"][positions[user_id]])}
                  for user_id in requested]
        payload = {"status": "SUCCEEDED", "kind": args.kind, "algorithm": args.algorithm,
                   "provenance": report["provenance"],
                   "profileVersion": report["profiles"]["profileVersion"],
                   "modelVersion": report["profiles"]["dimensionalityReduction"][args.algorithm]["modelVersion"],
                   "originalDimensions": int(profiles.shape[1]),
                   "reduction": {"dimensions": dimensions,
                                 "informationRetention": _retention(model, full_coordinates,
                                                                    profiles, dimensions, args.algorithm),
                                 "modelParameters": report["profiles"]["dimensionalityReduction"]
                                     [args.algorithm]["parameters"],
                                 "retentionDefinition": report["profiles"]["dimensionalityReduction"]
                                     [args.algorithm]["retentionDefinition"],
                                 "fitSeconds": report["profiles"]["dimensionalityReduction"]
                                     [args.algorithm]["fitSeconds"],
                                 "coordinateBytes": int(coordinate_matrix.nbytes),
                                 "fitStrategy": "Prefix of a training-only fitted eight-component model"},
                   "componentDirections": directions,
                   "parameters": {"clusters": cluster_count, "clusterAlgorithms": methods,
                                  "randomSeed": SEED},
                   "coordinates": coords,
                   "clusteringComparison": comparison,
                   "plotPoints": _plot_points(full_coordinates, plot_labels, profile_ids, requested),
                   "plotDefinition": "First two fitted components; plot sampled to at most 80 background users."}
    if args.kind != "classification":
        profiles_file.close()
    if args.kind != "classification":
        identity = {"dataVersion": report["provenance"]["dataVersion"],
                    "profileVersion": report["profiles"]["profileVersion"],
                    "kind": args.kind, "algorithm": args.algorithm,
                    "parameters": payload["parameters"]}
        if args.kind == "reduction":
            identity["dimensions"] = payload["reduction"]["dimensions"]
        payload["resultVersion"] = hashlib.sha256(json.dumps(
            identity, sort_keys=True).encode()).hexdigest()[:16]
    query_bytes = json.dumps(payload, ensure_ascii=False, sort_keys=True).encode("utf-8")
    query_version = hashlib.sha256(query_bytes).hexdigest()[:16]
    payload["queryVersion"] = query_version
    query_dir = artifact / "queries"
    query_dir.mkdir(exist_ok=True)
    query_path = query_dir / f"{query_version}.json"
    saved = json.dumps(payload, ensure_ascii=False, sort_keys=True, indent=2)
    if query_path.exists():
        if query_path.read_text(encoding="utf-8") != saved:
            raise ValueError("Existing query result differs from the computed result")
    else:
        query_path.write_text(saved, encoding="utf-8")
    print(json.dumps(payload, ensure_ascii=False))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    training = commands.add_parser("run")
    training.add_argument("--data-dir", required=True)
    training.add_argument("--report", required=True)
    training.add_argument("--output-dir", required=True)
    inspection = commands.add_parser("inspect")
    inspection.add_argument("--output-dir", required=True)
    inspection.add_argument("--kind", choices=("classification", "clustering", "reduction"), required=True)
    inspection.add_argument("--algorithm", required=True)
    inspection.add_argument("--user-id", type=int)
    inspection.add_argument("--movie-id", type=int)
    inspection.add_argument("--prediction-time", type=int)
    inspection.add_argument("--user-ids")
    inspection.add_argument("--clusters", type=int)
    inspection.add_argument("--dimension", type=int)
    inspection.add_argument("--retention", type=float)
    inspection.add_argument("--cluster-algorithm", choices=("kmeans", "gmm"))
    args = parser.parse_args()
    if args.command == "run":
        run(args)
    else:
        allowed = {"classification": {"logistic_regression", "decision_tree"},
                   "clustering": {"kmeans", "gmm"}, "reduction": {"pca", "nmf"}}
        if args.algorithm not in allowed[args.kind]:
            parser.error("Algorithm does not match the requested kind")
        if args.kind == "classification" and any(value is not None for value in
            (args.user_ids, args.clusters, args.dimension, args.retention, args.cluster_algorithm)):
            parser.error("Analysis parameters are not valid for classification")
        if args.kind == "clustering" and any(value is not None for value in
            (args.movie_id, args.prediction_time, args.dimension, args.retention, args.cluster_algorithm)):
            parser.error("Invalid clustering arguments")
        if args.kind == "reduction" and any(value is not None for value in
            (args.movie_id, args.prediction_time)):
            parser.error("Prediction arguments are not valid for reduction")
        if args.user_id is not None and args.user_ids is not None:
            parser.error("Choose user-id or user-ids")
        inspect(args)


if __name__ == "__main__":
    main()
