const dimensions = [
    ["Accurate", "准确性"],
    ["Complete", "完整性"],
    ["Unique", "唯一性"],
    ["Up-to-date", "时效性"],
    ["Consistent", "一致性"]
];

const defaultSteps = [
    ["AGENT_PLANNING", "理解请求"],
    ["RAW_QUALITY_SCAN", "质量扫描"],
    ["RELATIONAL_CHECK", "跨表检查"],
    ["CLEANING", "数据清洗"],
    ["CLEAN_VALIDATION", "复检验证"],
    ["QUALITY_SCORING", "五维评分"],
    ["AGENT_SUMMARIZING", "结果解释"],
    ["COMPLETED", "完成"]
].map(([code, label]) => ({code, label, status: "PENDING"}));

const mlDefaultSteps = [
    ["AGENT_PLANNING", "理解请求"],
    ["ML_PRECHECK", "核对版本"],
    ["ML_EXECUTION", "调用算法"],
    ["ML_REPORT", "整理结果"],
    ["COMPLETED", "完成"]
].map(([code, label]) => ({code, label, status: "PENDING"}));

const cleanVersion = "clean-agent-20260929-124232-bfc440f2";
const governancePrompt = "请使用默认规则清洗 MovieLens 1M，评估清洗前后的 Accurate、Complete、Unique、Up-to-date、Consistent 五个维度，并说明处理了哪些问题、还有哪些问题无法解决。";
const taskNotes = {classification: "", clustering: "", reduction: "", governance: governancePrompt};
let selectedTaskKind = "classification";

const elements = {
    prompt: document.querySelector("#promptInput"),
    taskHint: document.querySelector("#taskComposerHint"),
    charCount: document.querySelector("#charCount"),
    start: document.querySelector("#startButton"),
    badge: document.querySelector("#statusBadge"),
    runningStrip: document.querySelector("#runningStrip"),
    stage: document.querySelector("#stageText"),
    stepper: document.querySelector("#stepper"),
    progressBar: document.querySelector("#progressBar"),
    progressValue: document.querySelector("#progressValue"),
    selectedTool: document.querySelector("#selectedTool"),
    elapsedTime: document.querySelector("#elapsedTime"),
    lastUpdated: document.querySelector("#lastUpdated"),
    openResults: document.querySelector("#openResultsButton"),
    resultEmpty: document.querySelector("#resultEmpty"),
    resultFailure: document.querySelector("#resultFailure"),
    resultFailureReason: document.querySelector("#resultFailureReason"),
    resultContent: document.querySelector("#resultContent"),
    mlResultContent: document.querySelector("#mlResultContent"),
    mlResultSummary: document.querySelector("#mlResultSummary"),
    toggleMlSummary: document.querySelector("#toggleMlSummary"),
    mlProvenance: document.querySelector("#mlProvenance"),
    mlResultCards: document.querySelector("#mlResultCards"),
    resultSummary: document.querySelector("#resultSummary"),
    agentMessage: document.querySelector("#agentMessage .agent-copy"),
    conversation: document.querySelector("#conversation"),
    trace: document.querySelector("#executionTrace"),
    consoleBody: document.querySelector("#consoleBody"),
    questionInput: document.querySelector("#questionInput"),
    questionButton: document.querySelector("#questionForm button"),
    dialog: document.querySelector("#reportDialog"),
    reportJson: document.querySelector("#reportJson"),
    toast: document.querySelector("#toast")
};

let currentTask = null;
let pollTimer = null;
let elapsedTimer = null;

function formatNumber(value) {
    return Number(value || 0).toLocaleString("zh-CN");
}

function formatDate(iso) {
    return iso ? iso.slice(0, 10) : "—";
}

function formatClock(iso) {
    if (!iso) return "—";
    return new Date(iso).toLocaleTimeString("zh-CN", {hour12: false});
}

function formatDuration(start, end) {
    if (!start) return "—";
    const seconds = Math.max(0, Math.floor((new Date(end || Date.now()) - new Date(start)) / 1000));
    const minutes = Math.floor(seconds / 60);
    return minutes > 0 ? `${minutes}分${String(seconds % 60).padStart(2, "0")}秒` : `${seconds}秒`;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#39;");
}

function renderInlineMarkdown(value) {
    const codeFragments = [];
    let rendered = escapeHtml(value).replace(/`([^`\n]+)`/g, (_, code) => {
        const index = codeFragments.push(code) - 1;
        return `\uE000${index}\uE001`;
    });
    rendered = rendered
        .replace(/\*\*([^*\n]+)\*\*/g, "<strong>$1</strong>")
        .replace(/__([^_\n]+)__/g, "<strong>$1</strong>");
    return rendered.replace(/\uE000(\d+)\uE001/g,
        (_, index) => `<code>${codeFragments[Number(index)]}</code>`);
}

function renderMarkdown(value) {
    const lines = String(value ?? "").replaceAll("\r\n", "\n").split("\n");
    const output = [];
    let listType = "";
    let codeLines = null;

    const closeList = () => {
        if (listType) output.push(`</${listType}>`);
        listType = "";
    };

    lines.forEach(line => {
        if (line.trim().startsWith("```")) {
            closeList();
            if (codeLines === null) {
                codeLines = [];
            } else {
                output.push(`<pre><code>${escapeHtml(codeLines.join("\n"))}</code></pre>`);
                codeLines = null;
            }
            return;
        }
        if (codeLines !== null) {
            codeLines.push(line);
            return;
        }

        const heading = line.match(/^\s*(#{1,4})\s+(.+)$/);
        const unordered = line.match(/^\s*[-*]\s+(.+)$/);
        const ordered = line.match(/^\s*\d+[.)]\s+(.+)$/);
        const quote = line.match(/^\s*>\s?(.*)$/);

        if (heading) {
            closeList();
            const level = heading[1].length;
            output.push(`<h${level}>${renderInlineMarkdown(heading[2])}</h${level}>`);
        } else if (unordered || ordered) {
            const nextType = unordered ? "ul" : "ol";
            if (listType !== nextType) {
                closeList();
                listType = nextType;
                output.push(`<${listType}>`);
            }
            output.push(`<li>${renderInlineMarkdown((unordered || ordered)[1])}</li>`);
        } else if (quote) {
            closeList();
            output.push(`<blockquote>${renderInlineMarkdown(quote[1])}</blockquote>`);
        } else if (!line.trim()) {
            closeList();
        } else {
            closeList();
            output.push(`<p>${renderInlineMarkdown(line)}</p>`);
        }
    });

    closeList();
    if (codeLines !== null) {
        output.push(`<pre><code>${escapeHtml(codeLines.join("\n"))}</code></pre>`);
    }
    return output.join("");
}

function setAgentMessage(text, markdown = false) {
    if (markdown) {
        elements.agentMessage.classList.add("markdown-body");
        elements.agentMessage.innerHTML = renderMarkdown(text);
    } else {
        elements.agentMessage.classList.remove("markdown-body");
        elements.agentMessage.textContent = text;
    }
}

function updateCharacterCount() {
    elements.charCount.textContent = `${elements.prompt.value.length}/${elements.prompt.maxLength}`;
}

function selectTaskKind(kind) {
    taskNotes[selectedTaskKind] = elements.prompt.value;
    selectedTaskKind = kind;
    document.querySelectorAll(".task-kind").forEach(button => {
        const active = button.dataset.taskKind === kind;
        button.classList.toggle("active", active);
        button.setAttribute("aria-pressed", String(active));
    });
    document.querySelectorAll("[data-task-fields]").forEach(group => {
        group.classList.toggle("hidden", group.dataset.taskFields !== kind);
    });
    elements.prompt.value = taskNotes[kind];
    elements.prompt.placeholder = kind === "governance" ? "输入数据治理问题" : "补充你希望 Agent 解释的问题（可选）";
    updateTaskHint();
    updateCharacterCount();
    if (!currentTask) renderSteps(kind === "governance" ? defaultSteps : mlDefaultSteps);
}

function updateTaskHint() {
    if (selectedTaskKind === "governance") {
        elements.taskHint.textContent = "迭代一数据清洗与五维质量评估。";
        return;
    }
    const algorithm = document.querySelector(`#${selectedTaskKind}Algorithm`).value;
    const names = {
        classification: {logistic_regression: "逻辑回归", decision_tree: "决策树"},
        clustering: {kmeans: "K-means", gmm: "高斯混合模型"},
        reduction: {pca: "PCA", nmf: "NMF"}
    };
    const subject = {classification: "输入 UserID 和 MovieID", clustering: "输入一个或多个 UserID",
        reduction: "输入用户集合和压缩目标"}[selectedTaskKind];
    elements.taskHint.textContent = algorithm === "both"
        ? `${subject}；本次将对照两种算法。`
        : `${subject}；本次只调用 ${names[selectedTaskKind][algorithm]}。`;
}

function positiveInteger(id, label, maximum = Number.MAX_SAFE_INTEGER) {
    const raw = document.querySelector(`#${id}`).value.trim();
    const value = Number(raw);
    if (!/^\d+$/.test(raw) || !Number.isSafeInteger(value) || value < 1 || value > maximum) {
        throw new Error(maximum === Number.MAX_SAFE_INTEGER
            ? `${label}必须是正整数` : `${label}必须是 1 到 ${maximum} 的整数`);
    }
    return value;
}

function userIds(id) {
    const raw = document.querySelector(`#${id}`).value.trim();
    const values = raw.split(/[,，\s]+/).filter(Boolean);
    if (!values.length || values.length > 100 || values.some(value => !/^\d+$/.test(value)
            || !Number.isSafeInteger(Number(value)) || Number(value) < 1)) {
        throw new Error("请输入 1 到 100 个有效 UserID，多个 ID 用逗号分隔");
    }
    return [...new Set(values.map(Number))];
}

function clusterCount(id) {
    const count = positiveInteger(id, "群体数", 20);
    if (count < 2) throw new Error("群体数必须在 2 到 20 之间");
    return count;
}

function buildTaskPrompt() {
    const question = elements.prompt.value.trim();
    if (selectedTaskKind === "governance") {
        if (!question) throw new Error("请输入数据治理问题");
        return question;
    }
    const version = `使用已登记清洗版本 dataVersion=${cleanVersion}。`;
    let instruction;
    if (selectedTaskKind === "classification") {
        const user = positiveInteger("classificationUserId", "UserID");
        const movie = positiveInteger("classificationMovieId", "MovieID");
        const algorithm = document.querySelector("#classificationAlgorithm").value;
        const tools = algorithm === "both" ? "用户明确选择两种算法对照：分别调用 predict_high_rating_lr 和 predict_high_rating_tree 两个分类工具并比较"
            : `只调用 ${algorithm === "logistic_regression" ? "predict_high_rating_lr" : "predict_high_rating_tree"} 分类工具`;
        instruction = `对 UserID=${user} 与 MovieID=${movie} 预测评分是否不低于 4 分，${tools}。给出预测类别、正类概率、模型版本、实际参数、验证与测试指标，并标注训练期无历史的适用限制。`;
    } else if (selectedTaskKind === "clustering") {
        const ids = userIds("clusteringUserIds");
        const count = clusterCount("clusteringCount");
        const algorithm = document.querySelector("#clusteringAlgorithm").value;
        const tools = algorithm === "both" ? "用户明确选择两种算法对照：分别调用 cluster_users_kmeans 和 cluster_users_gmm 两个聚类工具并比较"
            : `只调用 ${algorithm === "kmeans" ? "cluster_users_kmeans" : "cluster_users_gmm"} 聚类工具`;
        instruction = `分析 UserID 集合 [${ids.join(",")}]，参数 userIds=[${ids.join(",")}]、clusters=${count}；${tools}。给出每位用户的群体归属、群体摘要、版本、参数和评价指标，并说明历史不足情况。`;
    } else {
        const ids = userIds("reductionUserIds");
        const count = clusterCount("reductionClusterCount");
        const algorithm = document.querySelector("#reductionAlgorithm").value;
        const tools = algorithm === "both" ? "用户明确选择两种算法对照：分别调用 reduce_user_profiles_pca 和 reduce_user_profiles_nmf 两个降维工具并比较"
            : `只调用 ${algorithm === "pca" ? "reduce_user_profiles_pca" : "reduce_user_profiles_nmf"} 降维工具`;
        const target = document.querySelector("#reductionTarget").value;
        let targetText;
        if (target === "dimension") {
            targetText = `dimension=${positiveInteger("reductionDimension", "目标维数", 8)}`;
        } else {
            const raw = document.querySelector("#reductionRetention").value.trim();
            const retention = Number(raw);
            if (!raw || !Number.isFinite(retention) || retention <= 0 || retention > 1) {
                throw new Error("信息保留阈值必须在 0 到 1 之间，且不能为 0");
            }
            targetText = `retention=${retention}`;
        }
        const clusterAlgorithm = document.querySelector("#reductionClusterAlgorithm").value;
        const comparison = clusterAlgorithm === "both" ? "使用 K-means 和 GMM 分别对照原始与压缩画像"
            : `使用 ${clusterAlgorithm === "kmeans" ? "K-means" : "GMM"} 对照原始与压缩画像，参数 clusterAlgorithm=${clusterAlgorithm}`;
        instruction = `分析 UserID 集合 [${ids.join(",")}]，参数 userIds=[${ids.join(",")}]、${targetText}、clusters=${count}；${tools}。${comparison}。给出坐标、信息保留、资源开销、聚类指标与二维图。`;
    }
    const prompt = `${version}${instruction}${question ? ` 补充问题：${question}` : ""}`;
    if (prompt.length > 2000) throw new Error("任务描述超过 2000 字，请减少用户 ID 或补充问题长度");
    return prompt;
}

function toolsForSelection() {
    if (selectedTaskKind === "governance") return ["run_full_governance"];
    const choices = {
        classification: {both: ["predict_high_rating_lr", "predict_high_rating_tree"],
            logistic_regression: ["predict_high_rating_lr"], decision_tree: ["predict_high_rating_tree"]},
        clustering: {both: ["cluster_users_kmeans", "cluster_users_gmm"],
            kmeans: ["cluster_users_kmeans"], gmm: ["cluster_users_gmm"]},
        reduction: {both: ["reduce_user_profiles_pca", "reduce_user_profiles_nmf"],
            pca: ["reduce_user_profiles_pca"], nmf: ["reduce_user_profiles_nmf"]}
    };
    const selected = document.querySelector(`#${selectedTaskKind}Algorithm`).value;
    return choices[selectedTaskKind][selected];
}

function switchView(view, updateHash = true) {
    const selected = view === "results" ? "results" : "agent";
    document.querySelectorAll("[data-view-panel]").forEach(panel => {
        panel.classList.toggle("hidden", panel.dataset.viewPanel !== selected);
    });
    document.querySelectorAll(".nav-item[data-view]").forEach(item => {
        item.classList.toggle("active", item.dataset.view === selected);
    });
    if (updateHash && location.hash !== `#${selected}`) history.replaceState(null, "", `#${selected}`);
    window.scrollTo({top: 0, behavior: "smooth"});
}

function selectConsoleTab(tab) {
    const trace = tab === "trace";
    document.querySelector("#dialoguePanel").classList.toggle("hidden", trace);
    document.querySelector("#tracePanel").classList.toggle("hidden", !trace);
    document.querySelectorAll(".console-tab").forEach(button => {
        button.classList.toggle("active", button.dataset.consoleTab === tab);
    });
    elements.consoleBody.scrollTop = elements.consoleBody.scrollHeight;
}

function setMetadata(task) {
    const result = task?.result?.toolResults?.[0]?.result || task?.result;
    const cleanVersion = result?.provenance?.dataVersion;
    const isMl = task?.selectedTool && task.selectedTool !== "run_full_governance";
    document.querySelector("#inputVersion").textContent = cleanVersion || (isMl ? "核对清洗版本中" : task?.inputVersion || "raw-v1");
    document.querySelector("#outputVersion").textContent = cleanVersion ? "分析报告" : (isMl ? "计算中" : task?.outputVersion || "—");
    document.querySelector("#rulesVersion").textContent = task?.rulesVersion || "quality-rules-v1";
    document.querySelector("#taskId").textContent = task?.taskId || "—";
}

function renderSteps(steps = defaultSteps) {
    elements.stepper.replaceChildren();
    elements.stepper.style.setProperty("--step-count", String(steps.length));
    steps.forEach((step, index) => {
        const item = document.createElement("div");
        item.className = `step ${String(step.status || "PENDING").toLowerCase()}`;
        const number = document.createElement("i");
        number.textContent = String(index + 1);
        const label = document.createElement("span");
        label.textContent = step.label;
        item.append(number, label);
        elements.stepper.append(item);
    });
}

function renderStatus(task) {
    const status = task?.status || "IDLE";
    const progress = Number(task?.progress || 0);
    elements.badge.className = "status-badge";
    elements.runningStrip.classList.toggle("hidden", status !== "QUEUED" && status !== "RUNNING");
    elements.openResults.classList.toggle("hidden", status !== "SUCCEEDED");
    elements.start.disabled = status === "QUEUED" || status === "RUNNING";

    if (status === "QUEUED" || status === "RUNNING") {
        elements.badge.classList.add("running");
        elements.badge.textContent = status === "QUEUED" ? "排队中" : "执行中";
    } else if (status === "SUCCEEDED") {
        elements.badge.classList.add("success");
        elements.badge.textContent = "已完成";
    } else if (status === "FAILED") {
        elements.badge.classList.add("failure");
        elements.badge.textContent = "执行失败";
    } else {
        elements.badge.classList.add("idle");
        elements.badge.textContent = "等待开始";
    }

    elements.stage.textContent = task?.stage || "等待任务";
    elements.progressBar.style.width = `${Math.max(0, Math.min(100, progress))}%`;
    elements.progressValue.textContent = `${progress}%`;
    elements.selectedTool.textContent = task?.selectedTool || "—";
    elements.lastUpdated.textContent = formatClock(task?.updatedAt);
    renderSteps(task?.steps?.length ? task.steps
        : selectedTaskKind === "governance" ? defaultSteps : mlDefaultSteps);
    updateElapsed();
}

function updateElapsed() {
    elements.elapsedTime.textContent = currentTask
        ? formatDuration(currentTask.startedAt, currentTask.finishedAt)
        : "—";
}

function renderTrace(events = []) {
    const nearBottom = elements.consoleBody.scrollHeight - elements.consoleBody.scrollTop
        - elements.consoleBody.clientHeight < 50;
    elements.trace.replaceChildren();
    if (!events.length) {
        const empty = document.createElement("p");
        empty.className = "console-empty";
        empty.textContent = "任务执行后显示规划、工具调用与观察结果。";
        elements.trace.append(empty);
        return;
    }
    events.forEach(event => {
        const item = document.createElement("article");
        item.className = `trace-entry ${String(event.type || "").toLowerCase()}`;
        const marker = document.createElement("i");
        marker.textContent = traceIcon(event.type);
        const content = document.createElement("div");
        const heading = document.createElement("strong");
        heading.textContent = traceTitle(event.type, event.toolName);
        const message = document.createElement("p");
        message.textContent = event.message || event.stageCode;
        const meta = document.createElement("small");
        meta.textContent = `${formatClock(event.timestamp)} · ${event.progress}%`;
        content.append(heading, message, meta);
        item.append(marker, content);
        elements.trace.append(item);
    });
    if (nearBottom) elements.consoleBody.scrollTop = elements.consoleBody.scrollHeight;
}

function traceIcon(type) {
    return ({PLAN: "P", ACTION: "A", PROGRESS: "↻", OBSERVATION: "O", FINAL: "✓", ERROR: "!"})[type] || "·";
}

function traceTitle(type, toolName) {
    const title = ({PLAN: "规划", ACTION: "调用工具", PROGRESS: "执行进度", OBSERVATION: "工具结果", FINAL: "完成", ERROR: "错误"})[type] || "事件";
    return toolName ? `${title} · ${toolName}` : title;
}

async function createTask() {
    let prompt;
    try {
        prompt = buildTaskPrompt();
    } catch (error) {
        return showToast(error.message);
    }
    clearTimeout(pollTimer);
    resetTaskView();
    renderStatus({status: "QUEUED", stage: "正在创建 Agent 任务", progress: 0});
    try {
        const response = await fetch("/api/tasks", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({prompt, requestedTools: toolsForSelection()})
        });
        if (!response.ok) throw new Error(await errorMessage(response));
        currentTask = await response.json();
        localStorage.setItem("movielens.currentTask", currentTask.taskId);
        renderTask(currentTask);
        schedulePoll();
    } catch (error) {
        renderStatus({status: "FAILED", stage: "任务创建失败", progress: 0});
        setAgentMessage(`任务创建失败：${error.message}`);
        showToast(error.message);
    }
}

async function pollTask() {
    if (!currentTask?.taskId) return;
    try {
        const response = await fetch(`/api/tasks/${encodeURIComponent(currentTask.taskId)}`);
        if (response.status === 404) {
            localStorage.removeItem("movielens.currentTask");
            currentTask = null;
            return;
        }
        if (!response.ok) throw new Error(await errorMessage(response));
        currentTask = await response.json();
        renderTask(currentTask);
        if (currentTask.status === "QUEUED" || currentTask.status === "RUNNING") schedulePoll();
    } catch (error) {
        showToast(`状态更新失败：${error.message}`);
        schedulePoll(7000);
    }
}

function schedulePoll(delay = 2500) {
    clearTimeout(pollTimer);
    pollTimer = setTimeout(pollTask, delay);
}

function renderTask(task) {
    setMetadata(task);
    renderStatus(task);
    renderTrace(task.events || []);
    if (task.status === "QUEUED" || task.status === "RUNNING") {
        setAgentMessage(task.stage || "Agent 正在处理任务。");
    } else if (task.status === "FAILED") {
        setAgentMessage(`任务失败：${task.error || "未知错误"}`);
        elements.resultFailureReason.textContent = `${task.stage || "执行阶段未知"}：${task.error || "未知错误"}`;
        elements.resultFailure.classList.remove("hidden");
        elements.resultEmpty.classList.add("hidden");
        elements.resultContent.classList.add("hidden");
        elements.mlResultContent.classList.add("hidden");
        enableQuestions(false);
    } else if (task.status === "SUCCEEDED") {
        setAgentMessage(task.summary || "任务已完成，结果来自实际工具报告。", true);
        renderCompleted(task);
        enableQuestions(true);
    }
}

function renderCompleted(task) {
    elements.resultFailure.classList.add("hidden");
    const result = task.result || {};
    if (result.kind || Array.isArray(result.toolResults)) {
        renderMlCompleted(task);
        return;
    }
    const quality = result.quality;
    const cleaning = result.cleaning;
    if (!quality || !cleaning) {
        elements.resultEmpty.classList.remove("hidden");
        elements.resultContent.classList.add("hidden");
        showToast("任务完成，但报告结构不完整");
        return;
    }
    renderScoreChart(quality);
    renderActions(cleaning.actions || {});
    renderBoundaries(quality.timeBoundaries || {});
    renderSamples(result.cleanRatingSamples || [], result.quarantineRatingSamples || []);
    renderLimitations(quality.limitations || []);
    elements.resultSummary.textContent = task.summary || "任务已完成。";
    document.querySelector("#reportPath").textContent = result.reportPath || "HDFS 报告已生成";
    elements.reportJson.textContent = JSON.stringify(result, null, 2);
    elements.resultEmpty.classList.add("hidden");
    elements.resultContent.classList.remove("hidden");
    elements.mlResultContent.classList.add("hidden");
}

function renderMlCompleted(task) {
    const results = task.result.toolResults || [{toolName: task.selectedTool, result: task.result}];
    const provenance = results[0]?.result?.provenance;
    if (!provenance || !results.length) {
        elements.resultEmpty.classList.remove("hidden");
        showToast("机器学习报告缺少数据来源");
        return;
    }
    elements.mlResultSummary.textContent = task.summary || "实际算法结果已生成。";
    const longSummary = elements.mlResultSummary.textContent.length > 280;
    elements.mlResultSummary.classList.toggle("collapsed", longSummary);
    elements.toggleMlSummary.classList.toggle("hidden", !longSummary);
    elements.toggleMlSummary.textContent = "展开完整解释";
    elements.toggleMlSummary.setAttribute("aria-expanded", "false");
    elements.mlProvenance.textContent = `清洗数据：${provenance.dataVersion} · 治理任务：${provenance.governanceTaskId} · T1：${formatDate(new Date(provenance.T1 * 1000).toISOString())} · T2：${formatDate(new Date(provenance.T2 * 1000).toISOString())}`;
    elements.mlResultCards.replaceChildren();
    results.forEach(({toolName, result}) => {
        const card = document.createElement("article");
        card.className = "ml-result-card";
        const title = document.createElement("h3");
        title.textContent = `${toolName || result.algorithm} · ${result.algorithm}`;
        card.append(title);
        const note = document.createElement("p");
        note.className = "result-kind-note";
        note.textContent = ({classification: "预测结果 · 验证与测试指标用于说明模型表现",
            clustering: "训练期用户画像的统计分组",
            reduction: "训练期画像的探索性压缩与分组对照"})[result.kind] || "实际分析结果";
        card.append(note);
        const line = (label, value) => {
            const paragraph = document.createElement("p");
            paragraph.textContent = `${label}：${value ?? "未提供"}`;
            card.append(paragraph);
        };
        const number = value => typeof value === "number" ? value.toFixed(4) : "未提供";
        line("证据路径", result.queryReportPath);
        if (result.kind === "classification") {
            line("模型版本", result.evaluation?.modelVersion);
            line("参数", result.evaluation?.selectedParameters);
            line("训练 / 验证 / 测试样本", `${result.splitCounts?.train ?? "—"} / ${result.splitCounts?.validation ?? "—"} / ${result.splitCounts?.test ?? "—"}`);
            line("验证集 F1 / ROC-AUC", `${number(result.evaluation?.validation?.F1)} / ${number(result.evaluation?.validation?.ROC_AUC)}`);
            line("测试集 F1 / ROC-AUC", `${number(result.evaluation?.test?.F1)} / ${number(result.evaluation?.test?.ROC_AUC)}`);
            line("阈值", number(result.evaluation?.validation?.threshold));
            if (result.prediction) {
                line("预测对象", `用户 ${result.prediction.userId} / 电影 ${result.prediction.movieId}`);
                line("高分概率估计", number(result.prediction.estimatedHighRatingProbability));
                line("预测类别", result.prediction.predictedHighRating ? "高分" : "非高分");
                const coldStart = [result.prediction.coldStartUser && "用户",
                    result.prediction.coldStartMovie && "电影"].filter(Boolean);
                if (coldStart.length) line("适用限制", `${coldStart.join("和")}在训练期没有评分历史，预测依赖默认历史特征`);
            }
        } else if (result.kind === "clustering") {
            line("结果版本", result.resultVersion);
            line("目标群数", result.parameters?.clusters);
            line("配置", JSON.stringify(result.results?.original?.parameters || {}));
            (result.userAssignments || []).forEach(user => {
                line(`用户 ${user.userId} 的群体`, `${user.group}${user.historyInsufficient ? " · 训练期历史不足" : ""}`);
                line(`用户 ${user.userId} 训练期评分数`, user.historyCount);
            });
            (result.groupSummaries || []).forEach(group =>
                line(`群体 ${group.group} · ${group.size} 人`, `偏好类型：${(group.topGenres || []).join("、")}；代表用户：${group.representativeUserId}`));
            Object.entries(result.results || {}).forEach(([space, metrics]) => {
                line(`${space} 轮廓系数`, number(metrics.silhouetteSample1000));
                if (space !== "original") line(`${space} 与原画像分组 ARI`, number(metrics.ARIvsOriginal));
                line(`${space} 群体规模`, (metrics.sizes || []).join(" / "));
            });
        } else if (result.kind === "reduction") {
            line("模型 / 结果版本", `${result.modelVersion} / ${result.resultVersion}`);
            line("原始画像维度", result.originalDimensions);
            line("压缩后维度", result.reduction?.dimensions);
            const retentionLabel = result.algorithm === "pca" ? "解释方差比例"
                : result.algorithm === "nmf" ? "按重建误差计算的信息保留" : "信息保留";
            line(retentionLabel, number(result.reduction?.informationRetention));
            line("训练模型拟合秒数", number(result.reduction?.fitSeconds));
            line("坐标存储字节数", result.reduction?.coordinateBytes);
            line("模型参数", JSON.stringify(result.reduction?.modelParameters || {}));
            if (result.reduction?.fitStrategy) {
                line("分量说明", `训练期模型拟合 ${result.reduction?.modelParameters?.components ?? "—"} 个分量；本次取前 ${result.reduction?.dimensions ?? "—"} 维`);
            }
            (result.componentDirections || []).forEach(direction =>
                line(`方向 ${direction.component}`, `${(direction.strongestGenres || []).join("、")}${direction.oppositeGenres ? ` ↔ ${direction.oppositeGenres.join("、")}` : ""}`));
            (result.coordinates || []).forEach(point =>
                line(`用户 ${point.userId} 坐标`, (point.values || []).map(number).join("，")));
            Object.entries(result.clusteringComparison || {}).forEach(([method, metrics]) => {
                line(`${method} 原始 / 压缩轮廓系数`, `${number(metrics.original?.silhouetteSample1000)} / ${number(metrics.reduced?.silhouetteSample1000)}`);
                line(`${method} 群体 ARI`, number(metrics.reduced?.ARIvsOriginal));
            });
            line("二维图说明", result.plotDefinition?.startsWith("First two fitted components")
                ? "展示前两个拟合分量；背景用户最多抽样 80 人，所选用户另行标出。"
                : result.plotDefinition);
            if (result.plotPoints?.length) card.append(renderMlScatter(result.plotPoints));
        }
        elements.mlResultCards.append(card);
    });
    if (results.length === 1 && results[0].result?.queryReportPath) {
        elements.mlProvenance.textContent += ` · 查询报告：${results[0].result.queryReportPath}`;
    }
    elements.reportJson.textContent = JSON.stringify(task.result, null, 2);
    elements.resultEmpty.classList.add("hidden");
    elements.resultContent.classList.add("hidden");
    elements.mlResultContent.classList.remove("hidden");
}

function renderMlScatter(points) {
    const svgNs = "http://www.w3.org/2000/svg";
    const svg = document.createElementNS(svgNs, "svg");
    svg.setAttribute("viewBox", "0 0 320 220");
    svg.setAttribute("role", "img");
    svg.setAttribute("aria-label", "用户画像降维二维散点图；颜色表示压缩后群体，描边表示目标用户");
    svg.style.width = "100%";
    svg.style.marginTop = "10px";
    const xs = points.map(point => point.x);
    const ys = points.map(point => point.y);
    const minX = Math.min(...xs), maxX = Math.max(...xs);
    const minY = Math.min(...ys), maxY = Math.max(...ys);
    const palette = ["#2864d9", "#e07b33", "#23a081", "#9c59c8", "#e04f6a", "#8c9b24"];
    points.forEach(point => {
        const circle = document.createElementNS(svgNs, "circle");
        circle.setAttribute("cx", String(16 + 288 * (point.x - minX) / Math.max(maxX - minX, 1e-9)));
        circle.setAttribute("cy", String(204 - 188 * (point.y - minY) / Math.max(maxY - minY, 1e-9)));
        circle.setAttribute("r", point.selected ? "5" : "2.6");
        circle.setAttribute("fill", palette[point.group % palette.length]);
        if (point.selected) circle.setAttribute("stroke", "#172944");
        const title = document.createElementNS(svgNs, "title");
        title.textContent = `用户 ${point.userId} · 群体 ${point.group}`;
        circle.append(title);
        svg.append(circle);
    });
    return svg;
}

function renderScoreChart(quality) {
    const chart = document.querySelector("#scoreChart");
    chart.replaceChildren();
    dimensions.forEach(([key, chinese]) => {
        const value = quality?.dimensions?.[key];
        const column = document.createElement("div");
        column.className = "score-column";
        const bars = document.createElement("div");
        bars.className = "bars";
        bars.append(createBar("before", value?.before?.score), createBar("after", value?.after?.score));
        const label = document.createElement("div");
        label.className = "score-label";
        label.textContent = key;
        const small = document.createElement("small");
        small.textContent = chinese;
        label.append(small);
        column.append(bars, label);
        chart.append(column);
    });
}

function createBar(type, rawScore) {
    const available = rawScore !== undefined && rawScore !== null;
    const score = Number(rawScore || 0);
    const bar = document.createElement("div");
    bar.className = `bar ${type}`;
    bar.style.height = `${available ? Math.max(4, score) : 4}%`;
    const value = document.createElement("em");
    value.textContent = available ? score.toFixed(2) : "—";
    bar.append(value);
    return bar;
}

function renderActions(actions) {
    document.querySelector("#cleanCount").textContent = formatNumber(sumAction(actions, "cleanWritten"));
    document.querySelector("#invalidCount").textContent = formatNumber(sumAction(actions, "invalidQuarantined"));
    document.querySelector("#duplicateCount").textContent = formatNumber(sumAction(actions, "duplicatesRemoved"));
    document.querySelector("#conflictCount").textContent = formatNumber(sumAction(actions, "conflictsQuarantined"));
}

function sumAction(actions, name) {
    return ["ratings", "users", "movies"].reduce((sum, dataset) => sum + Number(actions?.[dataset]?.[name] || 0), 0);
}

function renderBoundaries(boundaries) {
    document.querySelector("#t1Value").textContent = formatDate(boundaries.T1?.utc);
    document.querySelector("#t2Value").textContent = formatDate(boundaries.T2?.utc);
}

function renderSamples(clean, quarantine) {
    const cleanBody = document.querySelector("#cleanRows");
    const quarantineBody = document.querySelector("#quarantineRows");
    cleanBody.replaceChildren();
    quarantineBody.replaceChildren();
    clean.forEach(line => cleanBody.append(tableRow(String(line).split("::").slice(0, 4))));
    quarantine.forEach(line => {
        const [reason = "UNKNOWN", raw = ""] = String(line).split("\t", 2);
        quarantineBody.append(tableRow([reason, ...raw.split("::").slice(0, 4)]));
    });
    if (!clean.length) cleanBody.append(emptyRow(4));
    if (!quarantine.length) quarantineBody.append(emptyRow(5));
}

function tableRow(values) {
    const row = document.createElement("tr");
    values.forEach(value => {
        const cell = document.createElement("td");
        cell.textContent = value || "—";
        cell.title = value || "";
        row.append(cell);
    });
    return row;
}

function emptyRow(columns) {
    const row = document.createElement("tr");
    const cell = document.createElement("td");
    cell.className = "empty-row";
    cell.colSpan = columns;
    cell.textContent = "暂无样例";
    row.append(cell);
    return row;
}

function renderLimitations(limitations) {
    const list = document.querySelector("#limitationsList");
    list.replaceChildren();
    limitations.forEach(text => {
        const item = document.createElement("li");
        item.textContent = text;
        list.append(item);
    });
}

function resetTaskView() {
    elements.resultEmpty.classList.remove("hidden");
    elements.resultFailure.classList.add("hidden");
    elements.resultContent.classList.add("hidden");
    elements.mlResultContent.classList.add("hidden");
    elements.conversation.replaceChildren();
    renderTrace([]);
    enableQuestions(false);
}

function enableQuestions(enabled) {
    elements.questionInput.disabled = !enabled;
    elements.questionButton.disabled = !enabled;
    elements.questionInput.placeholder = enabled
        ? "围绕本次报告继续提问"
        : "任务完成后可围绕报告继续提问";
}

async function askQuestion(event) {
    event.preventDefault();
    const question = elements.questionInput.value.trim();
    if (!question || !currentTask?.taskId) return;
    appendConversation("user", question);
    elements.questionInput.value = "";
    elements.questionButton.disabled = true;
    try {
        const response = await fetch(`/api/tasks/${encodeURIComponent(currentTask.taskId)}/questions`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({question})
        });
        if (!response.ok) throw new Error(await errorMessage(response));
        const answer = await response.json();
        appendConversation("agent", answer.answer);
    } catch (error) {
        appendConversation("agent", `追问失败：${error.message}`);
    } finally {
        elements.questionButton.disabled = false;
    }
}

function appendConversation(role, text) {
    const message = document.createElement("article");
    message.className = `message ${role}`;
    if (role === "agent") {
        message.classList.add("markdown-body");
        message.innerHTML = renderMarkdown(text);
    } else {
        message.textContent = text;
    }
    elements.conversation.append(message);
    selectConsoleTab("dialogue");
    elements.consoleBody.scrollTop = elements.consoleBody.scrollHeight;
}

function downloadReport() {
    if (!currentTask?.result) return showToast("暂无报告");
    const blob = new Blob([JSON.stringify(currentTask.result, null, 2)], {type: "application/json"});
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = `${currentTask.taskId}-report.json`;
    link.click();
    URL.revokeObjectURL(link.href);
}

async function errorMessage(response) {
    try {
        const payload = await response.json();
        return payload.detail || payload.message || `HTTP ${response.status}`;
    } catch {
        return `HTTP ${response.status}`;
    }
}

function showToast(message) {
    elements.toast.textContent = message;
    elements.toast.classList.add("show");
    setTimeout(() => elements.toast.classList.remove("show"), 3500);
}

elements.prompt.addEventListener("input", updateCharacterCount);
elements.start.addEventListener("click", createTask);
document.querySelectorAll(".task-kind").forEach(button => button.addEventListener("click", () => {
    selectTaskKind(button.dataset.taskKind);
}));
for (const id of ["classificationAlgorithm", "clusteringAlgorithm", "reductionAlgorithm"]) {
    document.querySelector(`#${id}`).addEventListener("change", updateTaskHint);
}
document.querySelector("#reductionTarget").addEventListener("change", event => {
    const retention = event.target.value === "retention";
    document.querySelector("#dimensionField").classList.toggle("hidden", retention);
    document.querySelector("#retentionField").classList.toggle("hidden", !retention);
});
document.querySelector("#questionForm").addEventListener("submit", askQuestion);
document.querySelectorAll(".nav-item[data-view]").forEach(item => item.addEventListener("click", event => {
    event.preventDefault();
    switchView(item.dataset.view);
}));
document.querySelectorAll(".console-tab").forEach(button => button.addEventListener("click", () => {
    selectConsoleTab(button.dataset.consoleTab);
}));
elements.openResults.addEventListener("click", () => switchView("results"));
elements.toggleMlSummary.addEventListener("click", () => {
    const expanded = elements.toggleMlSummary.getAttribute("aria-expanded") !== "true";
    elements.toggleMlSummary.setAttribute("aria-expanded", String(expanded));
    elements.toggleMlSummary.textContent = expanded ? "收起解释" : "展开完整解释";
    elements.mlResultSummary.classList.toggle("collapsed", !expanded);
});
document.querySelector("#backToAgentButton").addEventListener("click", () => switchView("agent"));
document.querySelector("#failureBackButton").addEventListener("click", () => switchView("agent"));
document.querySelector("#viewReportButton").addEventListener("click", () => {
    if (!currentTask?.result) return showToast("暂无报告");
    elements.dialog.showModal();
});
document.querySelector("#downloadReportButton").addEventListener("click", downloadReport);
document.querySelector("#downloadMlReportButton").addEventListener("click", downloadReport);
document.querySelector("#viewMlReportButton").addEventListener("click", () => {
    if (!currentTask?.result) return showToast("暂无报告");
    elements.dialog.showModal();
});
document.querySelector("#closeDialog").addEventListener("click", () => elements.dialog.close());
window.addEventListener("hashchange", () => switchView(location.hash.slice(1), false));

updateCharacterCount();
updateTaskHint();
renderSteps(defaultSteps);
renderStatus(null);
switchView(location.hash.slice(1), false);
elapsedTimer = setInterval(updateElapsed, 1000);

const rememberedTask = localStorage.getItem("movielens.currentTask");
if (rememberedTask) {
    currentTask = {taskId: rememberedTask};
    pollTask();
}
