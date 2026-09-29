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

const elements = {
    prompt: document.querySelector("#promptInput"),
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
    resultContent: document.querySelector("#resultContent"),
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
    elements.charCount.textContent = `${elements.prompt.value.length}/2000`;
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
    document.querySelector("#inputVersion").textContent = task?.inputVersion || "raw-v1";
    document.querySelector("#outputVersion").textContent = task?.outputVersion || "—";
    document.querySelector("#rulesVersion").textContent = task?.rulesVersion || "quality-rules-v1";
    document.querySelector("#taskId").textContent = task?.taskId || "—";
}

function renderSteps(steps = defaultSteps) {
    elements.stepper.replaceChildren();
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
    renderSteps(task?.steps?.length ? task.steps : defaultSteps);
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
    const prompt = elements.prompt.value.trim();
    if (!prompt) return showToast("请输入任务需求");
    clearTimeout(pollTimer);
    resetTaskView();
    renderStatus({status: "QUEUED", stage: "正在创建 Agent 任务", progress: 0, steps: defaultSteps});
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
        renderStatus({status: "FAILED", stage: "任务创建失败", progress: 0, steps: defaultSteps});
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
        enableQuestions(false);
    } else if (task.status === "SUCCEEDED") {
        setAgentMessage(task.summary || "任务已完成，结果来自实际工具报告。", true);
        renderCompleted(task);
        enableQuestions(true);
    }
}

function renderCompleted(task) {
    const result = task.result || {};
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
    elements.resultContent.classList.add("hidden");
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
document.querySelectorAll(".nav-item[data-view]").forEach(item => item.addEventListener("click", event => {
    event.preventDefault();
    switchView(item.dataset.view);
}));
document.querySelectorAll(".console-tab").forEach(button => button.addEventListener("click", () => {
    selectConsoleTab(button.dataset.consoleTab);
}));
elements.openResults.addEventListener("click", () => switchView("results"));
document.querySelector("#backToAgentButton").addEventListener("click", () => switchView("agent"));
document.querySelector("#viewReportButton").addEventListener("click", () => {
    if (!currentTask?.result) return showToast("暂无报告");
    elements.dialog.showModal();
});
document.querySelector("#downloadReportButton").addEventListener("click", downloadReport);
document.querySelector("#closeDialog").addEventListener("click", () => elements.dialog.close());
window.addEventListener("hashchange", () => switchView(location.hash.slice(1), false));

updateCharacterCount();
renderSteps(defaultSteps);
renderStatus(null);
switchView(location.hash.slice(1), false);
elapsedTimer = setInterval(updateElapsed, 1000);

const rememberedTask = localStorage.getItem("movielens.currentTask");
if (rememberedTask) {
    currentTask = {taskId: rememberedTask};
    pollTask();
}
