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

当前扫描报告覆盖字段解析、必填项、数值范围和枚举合法性。唯一性、跨表引用一致性、时间边界及最终五维评分由后续作业补充，不能把当前报告误称为完整五维评价报告。
