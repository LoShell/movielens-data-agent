# MovieLens 数据分析 Agent

大数据分析课程实验项目。系统以 MovieLens 1M 为数据源，分三次迭代建设由 Agent 驱动的数据治理、机器学习分析和知识图谱系统。

当前版本为 **迭代一：Agent 驱动的 Hadoop 数据清洗与五维质量评估**。用户只需在网页中输入一次自然语言请求，Agent 会规划任务、选择已注册工具并调用真实 Hadoop 作业，最终展示执行进度、清洗前后质量得分、异常处置、数据样例、报告与评价局限。

> 本项目不使用占位数据或模拟分数。任务未完成、工具失败或报告缺失时，页面会明确显示未完成或失败状态。

迭代一的实验过程、结果分析和截图位置见 [迭代一实验报告.md](迭代一实验报告.md)。

## 1. 当前实现

- 使用真实 LLM 完成 `Plan → Action → Observation → Reflection` Agent 循环。
- 兼容 DeepSeek 和其他 OpenAI-compatible Chat Completions API。
- Spring 自动注册所有 `Tool` 实现，后续迭代增加工具时无需修改 Agent 主循环。
- 使用 Hadoop MapReduce 完成原始数据扫描、跨表检查、清洗隔离、清洗后复检和五维评分。
- 使用 HDFS 保存不可覆盖的原始数据版本、清洗版本和 JSON 报告。
- 网页支持自然语言输入、八阶段实时进度、Agent 对话、执行轨迹、结果展示、报告下载和结果追问。
- 固定并输出数据版本、规则版本、任务标识以及供后续迭代使用的 `T1/T2`。

迭代一当前注册的高级工具：

| 工具 | 作用 |
| --- | --- |
| `run_full_governance` | 一次执行原始质量扫描、跨表检查、数据清洗、清洗后复检和五维评分，并返回 HDFS 中的真实报告与样例。 |

## 2. 系统结构

```text
用户自然语言请求
       │
       ▼
Web 页面 / REST API
       │
       ▼
AgentLoop ── LLM 规划、工具选择、结果反思与解释
       │
       ▼
ToolRegistry ── 自动登记 Spring Tool
       │
       ▼
run_full_governance
       │
       ▼
Hadoop MapReduce ── HDFS 数据与报告
```

Maven 模块：

| 模块 | 职责 |
| --- | --- |
| `agent-core` | Agent 协议、上下文、循环、LLM Client、工具注册及通用进度事件。 |
| `hadoop-jobs` | MovieLens 解析、质量扫描、跨表检查、清洗隔离和五维评分。 |
| `web-app` | Spring Boot API、异步任务管理、Hadoop 工具适配、报告问答和前端页面。 |
| `scripts` | 数据上传、单阶段运行和 Agent 全流程入口脚本。 |

## 3. 运行环境

| 组件 | 版本或要求 |
| --- | --- |
| Java | 17 |
| Maven | Wrapper 3.9.14 |
| Hadoop | 3.5.0，单节点伪分布式模式 |
| Ubuntu | 24.04 LTS，运行 HDFS、YARN、MapReduce 和 Agent 后端 |
| Windows | 日常开发、单元测试和 Git 管理 |
| Web 端口 | `18080` |

Windows 只负责开发和纯 Java 测试；涉及 HDFS、YARN、Linux Shell 和最终端到端流程的验收必须在 Ubuntu 进行。完整的软件版本、Hadoop 参数、VirtualBox 网络、端口转发和快照说明见 [ENVIRONMENT.md](ENVIRONMENT.md)。

## 4. 首次准备

### 4.1 构建项目

Windows PowerShell 使用临时 Java 17，不修改系统默认 Java 21：

```powershell
$env:JAVA_HOME="C:\Users\JHZ\.jdks\ms-17.0.18"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd clean test
```

Ubuntu：

```bash
chmod +x mvnw scripts/*.sh
./mvnw clean package
```

### 4.2 启动并检查 Hadoop

```bash
start-dfs.sh
start-yarn.sh
mapred --daemon start historyserver
jps
hdfs dfsadmin -report
yarn node -list
```

`jps` 应至少包含 `NameNode`、`DataNode`、`SecondaryNameNode`、`ResourceManager`、`NodeManager` 和 `JobHistoryServer`。

> NameNode 已完成首次格式化。日常启动时禁止再次执行 `hdfs namenode -format`。

### 4.3 上传原始数据版本

项目不会把 MovieLens 原始数据提交到 Git。将 `ratings.dat`、`users.dat` 和 `movies.dat` 放在同一目录后执行：

```bash
./scripts/upload-movielens-raw.sh ~/data/ml-1m raw-v1
```

脚本将数据上传至 `/movielens/raw/raw-v1` 并保存 SHA-256 清单。已存在的数据版本不会被覆盖。

## 5. 配置 LLM

API Key 只能通过环境变量提供，不得写入源码、配置、日志、README 或截图。

DeepSeek 默认配置：

```bash
export LLM_PROVIDER="deepseek"
export LLM_API_KEY="你的 API Key"
export LLM_BASE_URL="https://api.deepseek.com"
export LLM_MODEL="deepseek-v4-flash"
```

系统也兼容 `DEEPSEEK_API_KEY`、`DEEPSEEK_BASE_URL` 和 `DEEPSEEK_MODEL`。切换其他 OpenAI-compatible 服务时，设置对应的地址、模型和密钥，并使用：

```bash
export LLM_PROVIDER="openai"
```

未配置 API Key 时应用仍可启动以预览页面，但 Agent 任务会明确失败，不会退化为固定工具调用或生成模拟结果。

## 6. 启动与使用

在 Ubuntu 项目根目录执行：

```bash
./mvnw clean package
java -jar web-app/target/web-app-0.1.0-SNAPSHOT.jar
```

浏览器访问：

```text
http://localhost:18080
```

VirtualBox 中需保留 `Windows 18080 → Ubuntu 18080` 的端口转发。推荐提示词：

```text
请使用默认规则清洗 MovieLens 1M，评估清洗前后的 Accurate、Complete、Unique、
Up-to-date、Consistent 五个维度，并说明处理了哪些问题、还有哪些问题无法解决。
```

执行阶段：

1. Agent 理解请求并选择工具。
2. 扫描原始数据质量。
3. 检查重复、冲突和跨表关系。
4. 清洗数据并隔离异常记录。
5. 使用相同口径复检清洗版本。
6. 计算五维质量得分。
7. Agent 根据真实工具结果生成解释。
8. 展示结果并支持围绕报告继续追问。

长任务运行时，页面会显示当前阶段、Hadoop Job ID、Map/Reduce 进度、总进度和最近更新时间。也可在 Ubuntu 中检查：

```bash
yarn application -list -appStates RUNNING,ACCEPTED
jps
```

## 7. 数据与报告路径

每次任务使用独立的 `taskId`，输出版本固定为 `clean-<taskId>`，避免覆盖或混用不同任务产物。

```text
/movielens/raw/<inputVersion>
/movielens/cleaned/<outputVersion>
/movielens/reports/<taskId>/raw-quality.json
/movielens/reports/<taskId>/relational-quality.json
/movielens/reports/<taskId>/cleaning.json
/movielens/reports/<taskId>/clean-raw-quality.json
/movielens/reports/<taskId>/clean-relational-quality.json
/movielens/reports/<taskId>/five-dimension-quality.json
```

查看某次任务的最终报告：

```bash
hdfs dfs -cat /movielens/reports/<taskId>/five-dimension-quality.json
```

## 8. 清洗和评分口径

默认规则版本为 `quality-rules-v1`：

- 字段数量、必填值、数据类型、取值范围和已知类别不合法的记录进入隔离区。
- 引用不存在用户或电影的评分记录进入隔离区。
- 超出 MovieLens 文档历史覆盖范围的评分记录进入隔离区。
- 精确重复记录只保留一条。
- 同一业务键存在相互矛盾值时，相关记录整体隔离，不猜测哪个值正确。
- 原始版本和清洗版本使用完全相同的规则、时间范围与计数方法复检。

五维得分统一使用：

```text
score = max(0, 100 × (1 - defectCount / denominator))
```

| 维度 | 当前评价口径 |
| --- | --- |
| Accurate | 类型、数值范围和已知类别等约束符合程度，不代表外部事实真实性。 |
| Complete | 必填字段缺失和空行缺陷率。 |
| Unique | 精确重复及相同业务键冲突率。 |
| Up-to-date | 时间戳相对 MovieLens 历史覆盖范围的有效性，不表示相对今天的新鲜度。 |
| Consistent | 结构、类别、跨表引用及业务键冲突的一致性。 |

供后续迭代统一使用的时间边界：

| 边界 | UTC 时间 | 用途 |
| --- | --- | --- |
| `T1` | `2002-01-01T00:00:00Z` | 训练集：`timestamp <= T1` |
| `T2` | `2003-01-01T00:00:00Z` | 验证集：`T1 < timestamp <= T2`；测试集：`timestamp > T2` |

## 9. 已验证结果

任务 `agent-20260929-124232-bfc440f2` 已由 Agent 在 Ubuntu 的真实 HDFS/YARN/MapReduce 环境完成全流程验证，总耗时约 11 分 55 秒：

```text
输入版本：raw-v1
输出版本：clean-agent-20260929-124232-bfc440f2
规则版本：quality-rules-v1
最终报告：/movielens/reports/agent-20260929-124232-bfc440f2/five-dimension-quality.json
```

| 维度 | 清洗前 | 清洗后 | 变化 |
| --- | ---: | ---: | ---: |
| Accurate | 95.96 | 100.00 | +4.04 |
| Complete | 98.82 | 100.00 | +1.18 |
| Unique | 94.97 | 100.00 | +5.03 |
| Up-to-date | 97.78 | 100.00 | +2.22 |
| Consistent | 94.12 | 100.00 | +5.88 |

清洗处置统计：

| 数据表 | 写入 clean | 非法记录隔离 | 精确重复移除 | 冲突记录隔离 |
| --- | ---: | ---: | ---: | ---: |
| users | 5,895 | 499 | 118 | 434 |
| movies | 3,662 | 128 | 117 | 558 |
| ratings | 894,993 | 219,004 | 0 | 36,244 |

以上结果只证明当前规则下的约束缺陷在清洗版本中已被排除，不能理解为所有数据内容都已被证明真实。

## 10. REST API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/tasks` | 提交自然语言任务，返回任务标识和初始状态。 |
| `GET` | `/api/tasks/{taskId}` | 查询状态、阶段、进度、执行事件和实际报告结果。 |
| `POST` | `/api/tasks/{taskId}/questions` | 围绕已完成任务的报告继续提问。 |

创建任务示例：

```bash
curl -X POST http://localhost:18080/api/tasks \
  -H 'Content-Type: application/json' \
  -d '{"prompt":"请使用默认规则清洗 MovieLens 1M，并比较清洗前后的五维质量。"}'
```

## 11. 测试

```bash
# Windows
.\mvnw.cmd clean test

# Ubuntu
./mvnw clean test
```

自动化测试覆盖 Agent 循环、LLM 协议、动态工具注册、MovieLens 解析、清洗决策、评分计算、任务进度和 Web JSON 序列化。最终验收还应在 Ubuntu 发起一次完整 Agent 任务，并核对：

- YARN 应用最终为 `FINISHED / SUCCEEDED`。
- HDFS 存在清洗数据和六份任务报告。
- 页面图表、处置统计、样例和下载报告来自同一次任务。
- Agent 解释与报告中的数字一致。

各 Hadoop 脚本的单独运行方法见 [scripts/README.md](scripts/README.md)。

## 12. 已知限制

- MovieLens 用户属性由用户自愿填写，格式正确不能证明内容真实。
- MovieLens 1M 是历史数据；时效性衡量历史覆盖范围有效性，而不是相对当前日期的新鲜度。
- 无法可靠修复的记录采用隔离方式处理，不能表述为已经修复。
- 当前为单节点教学环境，尚未验证多节点性能、容错和并发任务能力。
- 任务状态暂存在应用内存中；应用重启后旧任务仍保留在 HDFS，但不会继续出现在当前进程的任务列表中。
- 迭代一只登记了数据治理工具；分类、聚类、降维和图分析工具将在后续迭代加入同一个工具注册机制。

## 13. 提交与安全

提交源码时应包含三个 Maven 模块、前端、配置、Shell 脚本、Maven Wrapper、README 和环境文档。以下内容不得提交：

- 真实 API Key 或 `.env`。
- MovieLens 原始数据和大型 HDFS 导出。
- `target/`、本地日志和 IDE 临时文件。

汇报或文档中的所有指标必须来自真实运行结果。若任务失败，应展示失败阶段和原因，不得使用模拟结果替代。
