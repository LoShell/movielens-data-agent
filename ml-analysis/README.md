# Iteration 2 analysis

This module consumes the **actual iteration-1 cleaned HDFS version**. It never
substitutes raw MovieLens files for a missing cleaned version. Python 3.9+ and
the pinned `requirements.txt` are required.

On the Ubuntu machine running Hadoop, from the repository root:

```bash
python3 -m venv .venv-ml
.venv-ml/bin/pip install -r ml-analysis/requirements.txt
bash ml-analysis/export-clean.sh clean-agent-20260929-124232-bfc440f2
.venv-ml/bin/python ml-analysis/experiment.py run \
  --data-dir data/processed/clean-agent-20260929-124232-bfc440f2 \
  --report reports/iteration1/agent-20260929-124232-bfc440f2-quality-report.json \
  --output-dir artifacts/iteration2/clean-agent-20260929-124232-bfc440f2
bash ml-analysis/publish-analysis.sh clean-agent-20260929-124232-bfc440f2
```

The export script copies only the three `clean/part-*` datasets from HDFS and
records their SHA-256 checksums. `experiment.py` checks the data version and
the exact row counts in the successful iteration-1 report before training.
Missing, duplicate, malformed, or unreferenced records fail closed. Reports
and models are written under the ignored `artifacts/` directory.

Classification uses the established UTC split: train through 2002-01-01,
validation through 2003-01-01, and test afterward. The label is rating >= 4.
Training event features use only strictly earlier events. Events sharing a
timestamp are featurized together before the history is updated. Validation
and test use history frozen at T1, matching an offline deployment assumption;
their labels never update features. Both classifiers use the same business
features. Hyperparameters and the F1 threshold are selected on validation;
the test set is evaluated once after selection.

The user profile uses only training-period ratings. Iteration 2 implements
K-means and Gaussian mixtures for clustering, and PCA and NMF for
dimensionality reduction. The actual run and its validation evidence are
recorded in [the iteration-2 validation note](../reports/iteration2/validation.md).

## Agent integration

The Spring application registers six iteration-2 tools listed in the project
README. The tool input must contain `dataVersion` equal to the registered
clean version. A classification tool may additionally receive `userId` and
`movieId`, plus optional `predictionTime` (Unix seconds after T1).
With no time supplied, the tool explicitly uses T1-frozen history. Unknown IDs
fail explicitly; a cold-start model has not been trained. Clustering tools
accept `userId` or `userIds` and `clusters` (2–20). Reduction tools also accept
`dimension` (1–8) or `retention` (0–1], plus `clusterAlgorithm` (`kmeans` or
`gmm`). A missing training-period user profile fails explicitly. When the artifact
report is absent, the first tool call exports the clean version from HDFS (if
needed) and executes the full experiment. Later calls inspect the immutable
artifact. The Python executable is configured through `MOVIELENS_ML_PYTHON`;
the default is `.venv-ml/bin/python` inside the project.

The generated `experiment-report.json` contains the input SHA-256 values,
split counts, both classifiers' validation and test metrics, user profile
definition, both clustering results, both reductions' information retention,
runtime, and cross-space Adjusted Rand Index. Model bundles and user
coordinates are stored beside it. The UI reads only those actual outputs.
The publish script writes the complete versioned artifact to
`/movielens/analysis/iteration2/<dataVersion>` in HDFS without replacing an
existing version. Parameterized query results are saved separately under
`queries/<queryVersion>.json` locally and in HDFS. Agent tools publish both
the base experiment and each query report after a successful run.

For a manual read of an algorithm's report:

```bash
.venv-ml/bin/python ml-analysis/experiment.py inspect \
  --output-dir artifacts/iteration2/clean-agent-20260929-124232-bfc440f2 \
  --kind classification --algorithm logistic_regression
```
