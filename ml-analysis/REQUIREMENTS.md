# 迭代二正式需求对照

依据：仓库外的 `lab3-迭代2/迭代二_分类聚类降维与分析Agent.md`（2026-10-18 核对）。状态更新于 2026-10-19；实测数据见[迭代二实验报告](../迭代二实验报告.md)。

| 正式需求 | 当前实现位置 | 状态 |
| --- | --- | --- |
| 只使用迭代一清洗版本、报告和 T1/T2 | `export-clean.sh`、`experiment.py:load_clean` | Ubuntu/HDFS 实际导出、核对并完成实验 |
| 分类两种算法、独立工具、用户电影预测、概率与指标 | `experiment.py`、`IterationTwoConfiguration.java` | 逻辑回归/决策树实际训练及 Web 查询成功 |
| 聚类两种算法、用户/集合查询、群体归属与摘要、历史不足提示 | `experiment.py:inspect`、`IterationTwoTool.java` | K-means/GMM 实际查询与 Web 对照成功；课程 PPT 选型仍需核对 |
| 降维两种算法、维数或保留阈值、坐标、变化方向、聚类对照与二维图 | `experiment.py:inspect`、`static/js/app.js` | PCA/NMF 二维查询、聚类对照与页面散点图成功；课程 PPT 选型仍需核对 |
| 算法参数、版本、模型、画像、群体、坐标、评价报告 | `artifacts/iteration2/<dataVersion>`、HDFS `/movielens/analysis/iteration2/<dataVersion>` | 已发布 HDFS，实验报告本地与 HDFS 的 SHA-256 一致 |
| 前端自然语言、状态、证据、追问、报告下载 | Web 应用及迭代二结果卡片 | 三类任务已完成页面联调；追问、下载及异常分支仍需单独核对 |

**后续核对项：**

1. 单算法、未知 ID、缺失模型、历史不足、无效维数等异常分支可继续做页面级验收；现有自动化测试不等于全部浏览器分支已覆盖。
2. 单独核对页面的报告下载与追问内容。
3. 对照课程 PPT 确认 GMM/NMF 属于可选算法；若不在 PPT，改用 PPT 中的算法并重跑评价。

实验报告中的指标只引用真实运行产物；本地合成数据测试结果不作为课程结论。
