# MovieLens Data Analysis Agent

大数据分析课程实验项目。系统基于 MovieLens 1M 数据集，分三次迭代实现由 Agent 驱动的数据治理、机器学习分析和知识图谱任务。

当前正在完成迭代一：使用 Hadoop 对原始数据进行清洗和五维质量评估，并通过 Agent 和简易前端展示实际任务状态、数据版本、清洗结果及评价依据。

## 环境基线

- 项目目标 Java 版本：17
- 项目 Maven 版本：3.9.14（后续通过 Maven Wrapper 固定）
- Hadoop：3.5.0，单节点伪分布式模式
- Ubuntu：24.04 LTS，运行 HDFS、YARN、MapReduce 和最终集成测试
- Windows：日常开发和单元测试
- 原始 MovieLens `.dat` 文件编码：ISO-8859-1
- 项目源码、配置和清洗输出：统一使用 UTF-8

完整的安装路径、Hadoop 配置、端口、启动停止命令、验收结果和 VirtualBox 快照说明见 [ENVIRONMENT.md](ENVIRONMENT.md)。

## 重要约束

- Hadoop NameNode 已完成首次格式化，日常启动时禁止再次执行 `hdfs namenode -format`。
- 清洗、评分和报告必须来自真实 Hadoop 作业，不使用占位结果。
- API Key 只能通过环境变量提供，不得写入源码、配置、日志或截图。
