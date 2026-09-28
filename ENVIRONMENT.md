# 开发与运行环境

本文记录 MovieLens Data Analysis Agent 的环境基线、Hadoop 单节点配置和验收结果。记录日期为 2026-09-28。

## 1. 环境分工

| 环境 | 用途 |
| --- | --- |
| Windows 11 | 编写代码、运行纯 Java 单元测试、管理 Git、通过浏览器查看页面 |
| VirtualBox Ubuntu | 运行 HDFS、YARN、MapReduce、Agent 后端及端到端集成测试 |

并非所有测试都必须在 Ubuntu 运行。Agent 编排、评分公式、JSON 协议和报告生成等单元测试可在 Windows 高频执行；涉及 Hadoop、HDFS、YARN、Linux Shell、文件权限和最终全链路的测试必须在 Ubuntu 验收。

## 2. 已确认的软件版本

### Windows 开发机

| 组件 | 版本或状态 |
| --- | --- |
| 操作系统 | Windows 11，amd64 |
| Java | Eclipse Temurin OpenJDK 21.0.6 LTS |
| Maven | 3.9.13 |
| Git | 2.46.0.windows.1 |
| 默认编码 | UTF-8 |
| Hadoop | 不在 Windows 安装 |
| Docker | 不在 Windows 使用 |

Windows 的系统 JDK 保留 21，不修改全局 `JAVA_HOME` 和 `Path`。本项目的编译目标固定为 Java 17，IDEA 的 Project SDK、Module SDK、Maven Runner JRE 和 Maven Importer JDK 均固定为 17，Maven Compiler Plugin 设置 `release=17`，避免生成只能由 Java 21 运行的字节码。

每次新建 Windows PowerShell 或 IDEA Terminal 会话后，先为当前会话临时切换到 Java 17：

```powershell
$env:JAVA_HOME="C:\Users\JHZ\.jdks\ms-17.0.18"
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

然后验证：

```powershell
java -version
javac -version
mvn -version
```

三处都应显示 Java 17。该设置只影响当前终端会话，关闭终端后不会改变 Windows 的全局 Java 21 环境。

### Ubuntu 运行机

| 组件 | 版本或状态 |
| --- | --- |
| 操作系统 | Ubuntu 24.04 LTS (Noble)，x86_64 |
| 内核 | Linux 6.8.0-124-generic |
| 时区 | Asia/Shanghai |
| Locale | en_US.UTF-8 |
| Java/Javac | OpenJDK 17.0.20.1 |
| 系统 Maven | 3.8.7，运行于 Java 17 |
| Hadoop | 3.5.0 |
| Docker | 29.6.1 |
| Docker Compose | 5.3.1 |
| Python | 3.12.3 |
| OpenSSH | 9.6p1 |

VirtualBox 已配置 4 个虚拟 CPU。建议内存为 6～8 GiB；若只能使用约 4 GiB，应停止无关容器，并保持本项目的低内存 Hadoop 参数。

## 3. Maven 版本策略

Ubuntu 自带 Maven 3.8.7，Windows 当前为 Maven 3.9.13。两者仅用于首次生成 Maven Wrapper；正式项目统一使用 Maven Wrapper 3.9.14。

项目建立 `pom.xml` 后，在项目根目录执行一次：

```powershell
mvn wrapper:wrapper "-Dmaven=3.9.14" "-Dtype=only-script"
```

Wrapper 文件需要提交到 Git：

```text
mvnw
mvnw.cmd
.mvn/wrapper/maven-wrapper.properties
```

此后统一使用：

```powershell
# Windows
.\mvnw.cmd clean test
```

```bash
# Ubuntu
chmod +x mvnw
./mvnw clean test
```

## 4. Hadoop 安装位置和环境变量

Hadoop 安装结构：

```text
/home/ubuntu/hadoop -> /home/ubuntu/opt/hadoop-3.5.0
/home/ubuntu/hadoop-data/tmp
/home/ubuntu/hadoop-data/namenode
/home/ubuntu/hadoop-data/datanode
```

Ubuntu `~/.bashrc` 中使用：

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

export HADOOP_HOME="$HOME/hadoop"
export HADOOP_CONF_DIR="$HADOOP_HOME/etc/hadoop"
export HADOOP_COMMON_HOME="$HADOOP_HOME"
export HADOOP_HDFS_HOME="$HADOOP_HOME"
export HADOOP_MAPRED_HOME="$HADOOP_HOME"
export HADOOP_YARN_HOME="$HADOOP_HOME"

export PDSH_RCMD_TYPE=ssh
export PATH="$JAVA_HOME/bin:$HADOOP_HOME/bin:$HADOOP_HOME/sbin:$PATH"
```

`$HADOOP_HOME/etc/hadoop/hadoop-env.sh` 中必须包含：

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
```

原始 Hadoop 配置备份位于：

```text
/home/ubuntu/hadoop-conf-original
```

## 5. Hadoop 关键配置

当前为单节点伪分布式模式：

| 配置 | 值 |
| --- | --- |
| `fs.defaultFS` | `hdfs://localhost:9000` |
| `dfs.replication` | `1` |
| NameNode 数据目录 | `/home/ubuntu/hadoop-data/namenode` |
| DataNode 数据目录 | `/home/ubuntu/hadoop-data/datanode` |
| MapReduce framework | `yarn` |
| NodeManager 可用内存 | `2048 MB` |
| Scheduler 最小分配 | `256 MB` |
| Scheduler 最大分配 | `1024 MB` |
| Map container | `512 MB`，JVM `-Xmx384m` |
| Reduce container | `512 MB`，JVM `-Xmx384m` |
| MapReduce ApplicationMaster | `512 MB`，JVM `-Xmx384m` |

ApplicationMaster 的默认申请量为 1536 MB，曾超过当前 Scheduler 的 1024 MB 上限。已在 `mapred-site.xml` 中加入以下配置：

```xml
<property>
    <name>yarn.app.mapreduce.am.resource.mb</name>
    <value>512</value>
</property>

<property>
    <name>yarn.app.mapreduce.am.command-opts</name>
    <value>-Xmx384m</value>
</property>
```

## 6. SSH 和 VirtualBox 网络

Hadoop 启动脚本使用 Ubuntu 本机免密 SSH：

```bash
ssh -o BatchMode=yes localhost 'echo SSH_OK'
```

VirtualBox 网络：

```text
类型：NAT 网络
名称：NatNetwork
IPv4 网段：10.0.2.0/24
Ubuntu 地址：10.0.2.4
```

已经存在且必须保留的 SSH 转发：

| 名称 | 主机端口 | 子系统地址 | 子系统端口 |
| --- | ---: | --- | ---: |
| SSH (`Rule 1`) | 6666 | 10.0.2.4 | 22 |

Windows Bitvise 连接参数：

```text
主机：127.0.0.1
端口：6666
用户名：ubuntu
```

Web 页面需要的转发规则如下；只有在 VirtualBox 中实际添加后才可从 Windows 访问：

| 服务 | Windows 地址 | Ubuntu 端口 |
| --- | --- | ---: |
| Agent Web | `http://localhost:18080` | 18080 |
| HDFS NameNode | `http://localhost:19870` | 9870 |
| YARN ResourceManager | `http://localhost:18088` | 8088 |
| MapReduce JobHistory | `http://localhost:19888` | 19888 |

新增规则时不得修改或删除现有的 `6666 -> 10.0.2.4:22` 规则。

## 7. Hadoop 启动、检查与停止

启动：

```bash
start-dfs.sh
start-yarn.sh
mapred --daemon start historyserver
jps
```

正常情况下 `jps` 应包含：

```text
NameNode
DataNode
SecondaryNameNode
ResourceManager
NodeManager
JobHistoryServer
Jps
```

检查：

```bash
hdfs dfsadmin -report
yarn node -list
```

预期至少包括：

```text
Live datanodes (1)
Total Nodes:1
```

停止：

```bash
mapred --daemon stop historyserver
stop-yarn.sh
stop-dfs.sh
jps
```

> **严禁在日常启动时再次执行 `hdfs namenode -format`。** NameNode 已完成首次格式化，重复格式化会重建元数据并导致原有 HDFS 内容不可用。

## 8. MapReduce 验收记录

2026-09-28 已使用 Hadoop 自带 `hadoop-mapreduce-examples-3.5.0.jar` 完成真实 YARN MapReduce 验收。

验收结果：

```text
退出码：0
YARN 节点：1
grep-search：FINISHED / SUCCEEDED
grep-sort：FINISHED / SUCCEEDED
```

成功的应用：

```text
application_1790579746270_0004
application_1790579746270_0005
```

HDFS 结果目录：

```text
/user/ubuntu/hadoop-smoke-20260928151830/output-retry-152350
```

结果目录包含 `_SUCCESS` 和 `part-r-00000`，并能读取到 `dfs.replication`、`dfs.namenode.name.dir` 等实际统计结果。这证明 Java、SSH、HDFS、YARN、MapReduce 和 JobHistory 链路均可用。

## 9. MovieLens 数据注意事项

- 原始 `ratings.dat`、`users.dat`、`movies.dat` 无表头，字段使用 `::` 分隔。
- 原始文件使用 ISO-8859-1 编码；读取时必须显式指定该编码。
- 清洗输出、JSON 报告、源码和文档统一使用 UTF-8。
- 邮编必须按字符串处理，不能转换成数值后丢失前导零。
- 原始数据、大型 HDFS 导出、日志、`target/` 和 API Key 不应提交到 Git。

## 10. VirtualBox 快照

创建快照前先正常停止 Hadoop：

```bash
mapred --daemon stop historyserver
stop-yarn.sh
stop-dfs.sh
jps
sudo poweroff
```

VirtualBox 显示虚拟机为“已关闭”后，在管理器中选择该虚拟机，进入“快照”，点击“创建”，使用：

```text
名称：hadoop-3.5-working
说明：Ubuntu 24.04 + Java 17 + Hadoop 3.5.0，HDFS/YARN/MapReduce 验收成功
```

恢复快照会丢弃快照之后的虚拟磁盘变化，因此恢复前应先将源码推送到 Git，并备份仍需保留的数据和报告。
