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
