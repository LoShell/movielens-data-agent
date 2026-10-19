# 迭代二分析模块

本模块只使用**迭代一实际生成的 HDFS 清洗版本**。指定清洗版本缺失时，不会以原始 MovieLens 文件代替。运行环境需要 Python 3.9 或更高版本，以及 `requirements.txt` 中固定版本的依赖。

在运行 Hadoop 的 Ubuntu 项目根目录执行：

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

导出脚本只复制 HDFS 中 `users`、`movies`、`ratings` 三个表的 `clean/part-*` 文件，并记录 SHA-256。训练前，`experiment.py` 核对清洗版本、迭代一成功报告中的准确行数及文件内容。缺失、重复、格式错误或引用不存在的记录都会导致明确失败。生成的模型与报告位于被 Git 忽略的 `artifacts/` 目录。

## 时间边界与特征

分类任务按 UTC 时间切分：训练期截至 2002-01-01，验证期截至 2003-01-01，之后为测试期。评分不低于 4 分为正类。训练事件的特征只能使用严格早于该事件的历史；相同时间戳的事件先统一计算特征，再更新历史。验证集和测试集使用 T1 冻结的历史，模拟离线预测，其标签不会更新特征。两种分类算法使用相同业务特征，超参数和 F1 阈值由验证集决定，测试集只在方案确定后用于最终评价。

用户画像只由训练期评分构建。聚类使用 K-means 和高斯混合模型（GMM）；降维使用 PCA 和非负矩阵分解（NMF）。真实运行结果、指标定义与局限见[迭代二实验报告](../迭代二实验报告.md)及[验收记录](../reports/iteration2/validation.md)。

## Agent 工具接入

Spring 应用注册了项目主 README 中列出的六个迭代二工具。调用时必须提供与已登记清洗版本一致的 `dataVersion`。

- 分类工具可接收 `userId`、`movieId`，还可选传 `predictionTime`（T1 之后的 Unix 秒数）。未传时间时明确使用 T1 冻结的历史。未知 ID 会失败；训练期无历史的已知用户仍可预测，但结果标记冷启动限制，并没有单独训练冷启动模型。
- 聚类工具接收 `userId` 或 `userIds`，以及 `clusters`（2～20）。
- 降维工具还接收 `dimension`（1～8）或 `retention`（大于 0 且不超过 1），以及可选的 `clusterAlgorithm`（`kmeans` 或 `gmm`）。训练期没有画像的用户会得到明确失败状态。

若本地实验报告不存在，首次工具调用会在需要时从 HDFS 导出清洗版本并执行完整实验；后续调用读取已生成的版本化产物。Python 解释器由 `MOVIELENS_ML_PYTHON` 配置，默认使用项目中的 `.venv-ml/bin/python`。

`experiment-report.json` 保存输入 SHA-256、数据切分数、两种分类算法的验证与测试指标、画像定义、聚类结果、两种降维算法的信息保留、运行耗时和跨空间调整兰德指数。模型文件与用户坐标存于同一产物目录，前端只展示实际产物。发布脚本将完整版本写入 HDFS `/movielens/analysis/iteration2/<dataVersion>`，不会覆盖已有的不同版本。带参数的查询结果另存于本地及 HDFS 的 `queries/<queryVersion>.json`；Agent 工具会在成功后发布基础实验和对应查询报告。

手动查看某个算法的报告示例：

```bash
.venv-ml/bin/python ml-analysis/experiment.py inspect \
  --output-dir artifacts/iteration2/clean-agent-20260929-124232-bfc440f2 \
  --kind classification --algorithm logistic_regression
```
