import importlib.util
import io
import json
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path


spec = importlib.util.spec_from_file_location("experiment", Path(__file__).with_name("experiment.py"))
experiment = importlib.util.module_from_spec(spec)
spec.loader.exec_module(experiment)


class TemporalFeatureTest(unittest.TestCase):
    def test_known_ids_without_training_history_use_default_features(self):
        row = experiment._feature_row(
            1, 1, {1: ("F", 1)}, {1: ("Drama",)}, {"Drama": 0},
            {"users": {}, "movies": {}})
        self.assertEqual(3.5, row[-3])
        self.assertEqual(0, row[-2])
        self.assertEqual(0, row[-1])

    def test_equal_timestamp_events_do_not_see_each_other_and_holdouts_are_frozen(self):
        users = {1: ("F", 1), 2: ("M", 2)}
        movies = {1: ("Drama",), 2: ("Comedy",)}
        ratings = [
            (experiment.T1 - 1, 1, 1, 5),
            (experiment.T1, 1, 2, 1),
            (experiment.T1, 2, 1, 4),
            (experiment.T1 + 1, 1, 1, 2),
            (experiment.T1 + 2, 1, 2, 5),
            (experiment.T2 + 1, 1, 1, 4),
            (experiment.T2 + 2, 2, 2, 1),
        ]
        arrays, _, history = experiment.build_classification(users, movies, ratings)
        train, _ = arrays["train"]
        validation, _ = arrays["validation"]
        test, _ = arrays["test"]
        self.assertEqual(3.5, train[0, -3])
        self.assertEqual(5.0, train[1, -3])
        self.assertEqual(3.5, train[2, -3])
        self.assertEqual(3.0, validation[0, -3])
        self.assertEqual(3.0, validation[1, -3])
        self.assertEqual(3.0, test[0, -3])
        self.assertEqual([6, 2], history["users"][1])

    def test_profiles_use_training_period_only(self):
        movies = {1: ("Drama",), 2: ("Comedy",)}
        ratings = [(experiment.T1, 1, 1, 5),
                   (experiment.T1 + 1, 1, 1, 1),
                   (experiment.T2 + 1, 2, 2, 5)]
        users, profiles, counts, genres = experiment.build_profiles(movies, ratings)
        self.assertEqual([1], users)
        self.assertEqual([1], counts.tolist())
        self.assertAlmostEqual(2 / 3, profiles[0, genres["Drama"]])


class ExperimentSmokeTest(unittest.TestCase):
    def test_complete_experiment_and_versioned_inspection(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            data = root / experiment.EXPECTED_VERSION
            data.mkdir()
            users = [f"{user}::F::25::{user % 21}::00000" for user in range(1, 31)]
            movies = ["1::Drama (2000)::Drama", "2::Comedy (2000)::Comedy",
                      "3::Action (2000)::Action"]
            ratings = []
            for user in range(1, 31):
                for index in range(10):
                    ratings.append(f"{user}::{index % 3 + 1}::{(user + index) % 5 + 1}::"
                                   f"{experiment.T1 - 1000 + user * 10 + index}")
                ratings.append(f"{user}::1::{user % 5 + 1}::{experiment.T1 + user}")
                ratings.append(f"{user}::2::{(user + 2) % 5 + 1}::{experiment.T2 + user}")
            for name, rows in (("users", users), ("movies", movies), ("ratings", ratings)):
                (data / f"{name}.dat").write_text("\n".join(rows) + "\n", encoding="utf-8")
            (data / "SHA256SUMS").write_text("".join(
                f"{experiment._digest(data / (name + '.dat'))}  ./{name}.dat\n"
                for name in ("users", "movies", "ratings")), encoding="utf-8")
            report = {"taskId": "synthetic-test-only", "outputVersion": experiment.EXPECTED_VERSION,
                      "rulesVersion": "quality-rules-v1",
                      "quality": {"status": "SUCCEEDED", "timeBoundaries": {
                          "T1": {"epochSeconds": experiment.T1},
                          "T2": {"epochSeconds": experiment.T2}}},
                      "cleaning": {"actions": {name: {"cleanWritten": len(rows)} for name, rows in
                                                (("users", users), ("movies", movies), ("ratings", ratings))}}}
            report_path = root / "report.json"
            report_path.write_text(json.dumps(report), encoding="utf-8")
            output = root / "artifacts"
            experiment.run(type("Args", (), {"data_dir": str(data), "report": str(report_path),
                                             "output_dir": str(output)})())
            result = json.loads((output / "experiment-report.json").read_text(encoding="utf-8"))
            self.assertEqual({"train": 300, "validation": 30, "test": 30}, result["splitCounts"])
            self.assertEqual({"logistic_regression", "decision_tree"},
                             set(result["classification"]["models"]))
            self.assertEqual({"kmeans", "gmm"}, set(result["profiles"]["clustering"]))
            self.assertEqual({"pca", "nmf"}, set(result["profiles"]["dimensionalityReduction"]))
            captured = io.StringIO()
            with redirect_stdout(captured):
                experiment.inspect(type("Args", (), {"output_dir": str(output),
                    "kind": "classification", "algorithm": "logistic_regression",
                    "user_id": 1, "movie_id": 1,
                    "prediction_time": experiment.T1 + 1, "user_ids": None,
                    "clusters": None, "dimension": None, "retention": None,
                    "cluster_algorithm": None})())
            prediction = json.loads(captured.getvalue())
            self.assertEqual(experiment.EXPECTED_VERSION, prediction["provenance"]["dataVersion"])
            self.assertTrue(0 <= prediction["prediction"]["estimatedHighRatingProbability"] <= 1)
            self.assertTrue((output / "queries" / f"{prediction['queryVersion']}.json").is_file())
            captured = io.StringIO()
            with redirect_stdout(captured):
                experiment.inspect(type("Args", (), {"output_dir": str(output),
                    "kind": "clustering", "algorithm": "kmeans", "user_id": 1,
                    "user_ids": None, "movie_id": None, "prediction_time": None,
                    "clusters": 3, "dimension": None, "retention": None,
                    "cluster_algorithm": None})())
            groups = json.loads(captured.getvalue())
            self.assertEqual(1, groups["userAssignments"][0]["userId"])
            self.assertEqual(3, groups["parameters"]["clusters"])
            self.assertGreater(len(groups["groupSummaries"]), 0)
            captured = io.StringIO()
            with redirect_stdout(captured):
                experiment.inspect(type("Args", (), {"output_dir": str(output),
                    "kind": "reduction", "algorithm": "pca", "user_id": 1,
                    "user_ids": None, "movie_id": None, "prediction_time": None,
                    "clusters": 3, "dimension": 2, "retention": None,
                    "cluster_algorithm": "kmeans"})())
            reduction = json.loads(captured.getvalue())
            self.assertEqual(2, len(reduction["coordinates"][0]["values"]))
            self.assertGreater(len(reduction["plotPoints"]), 0)


if __name__ == "__main__":
    unittest.main()
