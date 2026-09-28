# Ubuntu Hadoop 脚本

以下命令均在 Ubuntu 项目根目录执行。首次运行前确认 HDFS、YARN 和 JobHistoryServer 已启动。

## 构建 Hadoop 作业

```bash
chmod +x mvnw scripts/*.sh
./mvnw -pl hadoop-jobs -am clean package
```

## 上传不可覆盖的原始数据版本

```bash
./scripts/upload-movielens-raw.sh /path/to/ml-1m raw-v1
```

脚本会上传三个 `.dat` 文件并保存 SHA-256 清单。若 `/movielens/raw/raw-v1` 已存在，脚本会拒绝覆盖。

## 运行清洗前基础质量扫描

```bash
./scripts/run-raw-quality-scan.sh scan-001 raw-v1 quality-rules-v1
```

报告写入：

```text
/movielens/reports/scan-001/raw-quality.json
```

当前扫描报告覆盖字段解析、必填项、数值范围和枚举合法性。

## 运行全表与跨表质量扫描

使用与基础扫描相同的任务标识：

```bash
./scripts/run-relational-quality-scan.sh scan-001 raw-v1 quality-rules-v1
```

报告写入 `/movielens/reports/scan-001/relational-quality.json`，覆盖精确重复、同一业务键冲突、
评分引用不存在的用户或电影，以及评分时间戳是否超出 MovieLens 1M 文档覆盖范围。
默认闭区间为 `956703932..1046454590`，其他数据版本可通过环境变量
`MOVIELENS_MIN_TIMESTAMP` 和 `MOVIELENS_MAX_TIMESTAMP` 显式覆盖。

这两个扫描报告仍不是最终五维评价报告；最终得分由后续评分作业统一计算。

## 运行清洗

```bash
./scripts/run-cleaning.sh scan-001 raw-v1 clean-v1 quality-rules-v1
```

清洗按 `users -> movies -> ratings` 的顺序执行三个 Hadoop 作业。格式或取值非法、断链、
时间越界的记录进入 `quarantine`；精确重复只保留一条；同一业务键下存在矛盾值时，
所有相关记录均隔离，避免系统自行猜测正确值。结果写入 `/movielens/cleaned/clean-v1`，
处置统计写入 `/movielens/reports/scan-001/cleaning.json`。

## 使用相同口径复检清洗结果

```bash
./scripts/run-clean-quality-scan.sh scan-001 clean-v1 quality-rules-v1
```

脚本使用 UTF-8 读取清洗产物，依次执行基础质量扫描和全表/跨表扫描，报告分别写入
`clean-raw-quality.json` 与 `clean-relational-quality.json`。原始数据与清洗数据使用相同规则版本、
相同时间范围和相同计数逻辑，结果可直接用于五维评分对比。

## 生成五维评分与时间边界

```bash
./scripts/run-quality-score.sh scan-001 raw-v1 clean-v1 quality-rules-v1
```

评分工具读取四份 Hadoop 扫描报告，使用固定公式生成清洗前后 Accurate、Complete、Unique、
Up-to-date、Consistent 得分和变化。默认 `T1=2002-01-01T00:00:00Z`、
`T2=2003-01-01T00:00:00Z`，后续迭代统一采用：训练集 `timestamp <= T1`，
验证集 `T1 < timestamp <= T2`，测试集 `timestamp > T2`。
