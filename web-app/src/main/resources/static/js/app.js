const dimensions = [
    ["Accurate", "准确性"],
    ["Complete", "完整性"],
    ["Unique", "唯一性"],
    ["Up-to-date", "时效性"],
    ["Consistent", "一致性"]
];

const elements = {
    prompt: document.querySelector("#promptInput"),
    charCount: document.querySelector("#charCount"),
    start: document.querySelector("#startButton"),
    badge: document.querySelector("#statusBadge"),
    runningStrip: document.querySelector("#runningStrip"),
    stage: document.querySelector("#stageText"),
    resultArea: document.querySelector("#resultArea"),
    questionArea: document.querySelector("#questionArea"),
    agentMessage: document.querySelector("#agentMessage p"),
    dialog: document.querySelector("#reportDialog"),
    reportJson: document.querySelector("#reportJson"),
    toast: document.querySelector("#toast")
};

let currentTask = null;
let pollTimer = null;

function formatNumber(value) {
    return Number(value || 0).toLocaleString("zh-CN");
}

function formatDate(iso) {
    if (!iso) return "—";
    return iso.slice(0, 10);
}

function updateCharacterCount() {
    elements.charCount.textContent = `${elements.prompt.value.length}/2000`;
}

function renderScoreChart(quality) {
    const chart = document.querySelector("#scoreChart");
    chart.replaceChildren();
    dimensions.forEach(([key, chinese]) => {
        const value = quality?.dimensions?.[key];
        const before = Number(value?.before?.score || 0);
        const after = Number(value?.after?.score || 0);

        const column = document.createElement("div");
        column.className = "score-column";
        const bars = document.createElement("div");
        bars.className = "bars";
        bars.append(createBar("before", before, Boolean(value)), createBar("after", after, Boolean(value)));
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

function createBar(type, score, available) {
    const bar = document.createElement("div");
    bar.className = `bar ${type}`;
    bar.style.height = `${available ? Math.max(4, score) : 4}%`;
    const value = document.createElement("em");
    value.textContent = available ? score.toFixed(2) : "—";
    bar.append(value);
    return bar;
}

function setMetadata(task) {
    document.querySelector("#inputVersion").textContent = task?.inputVersion || "raw-v1";
    document.querySelector("#outputVersion").textContent = task?.outputVersion || "—";
    document.querySelector("#rulesVersion").textContent = task?.rulesVersion || "quality-rules-v1";
    document.querySelector("#taskId").textContent = task?.taskId || "—";
}

function setStatus(status, stage = "") {
    const steps = [...document.querySelectorAll(".step")];
    steps.forEach(step => step.className = "step");
    elements.badge.className = "status-badge";
    elements.runningStrip.classList.add("hidden");

    if (status === "QUEUED" || status === "RUNNING") {
        elements.badge.classList.add("running");
        elements.badge.textContent = status === "QUEUED" ? "排队中" : "执行中";
        steps[0].classList.add("done");
        for (let index = 1; index < 6; index++) steps[index].classList.add("running");
        elements.runningStrip.classList.remove("hidden");
        elements.stage.textContent = stage || "Agent 正在调用 Hadoop 完整治理流程";
        elements.start.disabled = true;
        return;
    }
    if (status === "SUCCEEDED") {
        elements.badge.classList.add("success");
        elements.badge.textContent = "已完成";
        steps.forEach(step => step.classList.add("done"));
        elements.start.disabled = false;
        return;
    }
    if (status === "FAILED") {
        elements.badge.classList.add("failure");
        elements.badge.textContent = "执行失败";
        steps[0].classList.add("done");
        steps[1].classList.add("failed");
        elements.start.disabled = false;
        return;
    }
    elements.badge.classList.add("idle");
    elements.badge.textContent = "等待开始";
    steps[0].classList.add("current");
    elements.start.disabled = false;
}

async function createTask() {
    const prompt = elements.prompt.value.trim();
    if (!prompt) {
        showToast("请输入数据治理需求");
        return;
    }
    clearTimeout(pollTimer);
    resetResults();
    setStatus("QUEUED", "正在创建 Agent 任务");
    try {
        const response = await fetch("/api/tasks", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({prompt})
        });
        if (!response.ok) throw new Error(await errorMessage(response));
        currentTask = await response.json();
        localStorage.setItem("movielens.currentTask", currentTask.taskId);
        renderTask(currentTask);
        schedulePoll();
    } catch (error) {
        setStatus("FAILED");
        elements.agentMessage.textContent = `任务创建失败：${error.message}`;
        showToast(error.message);
    }
}

async function pollTask() {
    if (!currentTask?.taskId) return;
    try {
        const response = await fetch(`/api/tasks/${encodeURIComponent(currentTask.taskId)}`);
        if (!response.ok) throw new Error(await errorMessage(response));
        currentTask = await response.json();
        renderTask(currentTask);
        if (currentTask.status === "QUEUED" || currentTask.status === "RUNNING") schedulePoll();
    } catch (error) {
        showToast(`状态更新失败：${error.message}`);
        schedulePoll(7000);
    }
}

function schedulePoll(delay = 3500) {
    clearTimeout(pollTimer);
    pollTimer = setTimeout(pollTask, delay);
}

function renderTask(task) {
    setMetadata(task);
    setStatus(task.status, task.stage);
    if (task.status === "QUEUED" || task.status === "RUNNING") {
        elements.agentMessage.textContent = "Agent 已接受请求，正在调用 Hadoop 执行扫描、清洗、复检和评分。任务完成前不会生成占位分数。";
    } else if (task.status === "FAILED") {
        elements.agentMessage.textContent = `任务失败：${task.error || "未知错误"}`;
    } else if (task.status === "SUCCEEDED") {
        renderCompleted(task);
    }
}

function renderCompleted(task) {
    const result = task.result || {};
    const quality = result.quality || {};
    const cleaning = result.cleaning || {};
    renderScoreChart(quality);
    renderActions(cleaning.actions || {});
    renderBoundaries(quality.timeBoundaries || {});
    renderSamples(result.cleanRatingSamples || [], result.quarantineRatingSamples || []);
    renderLimitations(quality.limitations || []);

    const clean = sumAction(cleaning.actions, "cleanWritten");
    const quarantined = sumAction(cleaning.actions, "invalidQuarantined")
        + sumAction(cleaning.actions, "duplicatesRemoved")
        + sumAction(cleaning.actions, "conflictsQuarantined");
    elements.agentMessage.textContent = `Hadoop 治理任务已完成：清洗版本保留 ${formatNumber(clean)} 条记录，`
        + `另有 ${formatNumber(quarantined)} 条因规则异常、重复或冲突进入隔离区。`
        + "五维得分均来自清洗前后同口径扫描；100 分仅表示通过当前规则，不代表现实真实性已被外部核验。";
    elements.resultArea.classList.remove("hidden");
    elements.questionArea.classList.remove("hidden");
    document.querySelector("#reportPath").textContent = result.reportPath || "HDFS 报告已生成";
    elements.reportJson.textContent = JSON.stringify({quality, cleaning}, null, 2);
}

function renderActions(actions) {
    document.querySelector("#cleanCount").textContent = formatNumber(sumAction(actions, "cleanWritten"));
    document.querySelector("#invalidCount").textContent = formatNumber(sumAction(actions, "invalidQuarantined"));
    document.querySelector("#duplicateCount").textContent = formatNumber(sumAction(actions, "duplicatesRemoved"));
    document.querySelector("#conflictCount").textContent = formatNumber(sumAction(actions, "conflictsQuarantined"));
}

function sumAction(actions, name) {
    if (!actions) return 0;
    return ["ratings", "users", "movies"].reduce((sum, dataset) => sum + Number(actions[dataset]?.[name] || 0), 0);
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

    clean.forEach(line => {
        const fields = line.split("::");
        cleanBody.append(tableRow(fields.slice(0, 4)));
    });
    quarantine.forEach(line => {
        const [reason = "UNKNOWN", raw = ""] = line.split("\t", 2);
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
    cell.textContent = "暂无可展示样例";
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

function resetResults() {
    renderScoreChart(null);
    ["cleanCount", "invalidCount", "duplicateCount", "conflictCount", "t1Value", "t2Value"]
        .forEach(id => document.querySelector(`#${id}`).textContent = "—");
    elements.resultArea.classList.add("hidden");
    elements.questionArea.classList.add("hidden");
    document.querySelector("#conversation").replaceChildren();
}

async function askQuestion(event) {
    event.preventDefault();
    const input = document.querySelector("#questionInput");
    const question = input.value.trim();
    if (!question || !currentTask?.taskId) return;
    appendConversation("user", question);
    input.value = "";
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
    }
}

function appendConversation(role, text) {
    const paragraph = document.createElement("p");
    paragraph.className = role;
    paragraph.textContent = text;
    const conversation = document.querySelector("#conversation");
    conversation.append(paragraph);
    conversation.scrollTop = conversation.scrollHeight;
}

function downloadReport() {
    if (!currentTask?.result) return showToast("暂无可下载报告");
    const blob = new Blob([JSON.stringify(currentTask.result, null, 2)], {type: "application/json"});
    const link = document.createElement("a");
    link.href = URL.createObjectURL(blob);
    link.download = `${currentTask.taskId}-quality-report.json`;
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
document.querySelector("#questionForm").addEventListener("submit", askQuestion);
document.querySelector("#viewReportButton").addEventListener("click", () => {
    if (!currentTask?.result) return showToast("暂无报告");
    elements.dialog.showModal();
});
document.querySelector("#downloadReportButton").addEventListener("click", downloadReport);
document.querySelector("#closeDialog").addEventListener("click", () => elements.dialog.close());

updateCharacterCount();
renderScoreChart(null);
setStatus("IDLE");

const rememberedTask = localStorage.getItem("movielens.currentTask");
if (rememberedTask) {
    currentTask = {taskId: rememberedTask};
    pollTask();
}
