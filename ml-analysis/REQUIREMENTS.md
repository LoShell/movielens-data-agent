# 迭代二正式需求对照

依据：`lab3-迭代2/迭代二_分类聚类降维与分析Agent.md`（2026-10-18 核对）。

| 正式需求 | 当前实现位置 | 状态 |
| --- | --- | --- |
| 只使用迭代一清洗版本、报告和 T1/T2 | `export-clean.sh`、`experiment.py:load_clean` | 本地逻辑已验证；待 Ubuntu/HDFS 真实验收 |
| 分类两种算法、独立工具、用户电影预测、概率与指标 | `experiment.py`、`IterationTwoConfiguration.java` | 逻辑回归/决策树已接入；待真实运行 |
| 聚类两种算法、用户/集合查询、群体归属与摘要、历史不足提示 | `experiment.py:inspect`、`IterationTwoTool.java` | K-means/GMM 已接入；待真实运行与课程 PPT 确认 |
| 降维两种算法、维数或保留阈值、坐标、变化方向、聚类对照与二维图 | `experiment.py:inspect`、`static/js/app.js` | PCA/NMF 已接入；待真实运行与课程 PPT 确认 |
| 算法参数、版本、模型、画像、群体、坐标、评价报告 | `artifacts/iteration2/<dataVersion>`、HDFS `/movielens/analysis/iteration2/<dataVersion>` | 本地生成与发布脚本已写；待 HDFS 验收 |
| 前端自然语言、状态、证据、追问、报告下载 | 原 Web 应用及迭代二结果卡片 | 编译与脚本检查通过；待 Ubuntu 浏览器全链路验收 |

**尚未完成的正式验收：**

1. 在 Ubuntu 中读取指定 HDFS 清洗版本，实际生成分类、聚类、降维指标、模型、坐标和版本化报告。
2. 验证 Agent 对未指定算法的请求确实调用两种工具，以及指定算法、未知 ID、缺失模型、历史不足、无效维数等分支。
3. 用真实结果检查前端二维图、群体解释、报告下载与追问内容，并制作迭代二实验报告和汇报材料。
4. 对照课程 PPT 确认 GMM/NMF 属于可选算法；若不在 PPT，改用 PPT 中的算法并重跑评价。

当前没有真实迭代二数值。任何本地合成数据测试结果都不应放入提交报告或汇报。
