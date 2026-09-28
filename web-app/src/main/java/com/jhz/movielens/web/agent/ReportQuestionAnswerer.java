package com.jhz.movielens.web.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.jhz.movielens.web.task.TaskSnapshot;
import com.jhz.movielens.web.task.TaskStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class ReportQuestionAnswerer {
    private static final List<String> DIMENSIONS =
            List.of("Accurate", "Complete", "Unique", "Up-to-date", "Consistent");

    public String answer(TaskSnapshot task, String question) {
        if (task.status() != TaskStatus.SUCCEEDED || task.result() == null) {
            return "任务尚未成功完成，目前没有可用于回答的实际评估报告。";
        }
        String normalized = question.toLowerCase(Locale.ROOT);
        JsonNode quality = task.result().path("quality");
        JsonNode cleaning = task.result().path("cleaning");

        if (containsAny(normalized, "分数", "评分", "提升", "score")) {
            return scoreAnswer(quality);
        }
        if (containsAny(normalized, "隔离", "删除", "去重", "冲突", "保留")) {
            return cleaningAnswer(cleaning);
        }
        if (containsAny(normalized, "t1", "t2", "时间", "训练", "验证", "测试")) {
            JsonNode boundaries = quality.path("timeBoundaries");
            return "本任务固定使用 T1=" + boundaries.path("T1").path("utc").asText()
                    + "、T2=" + boundaries.path("T2").path("utc").asText()
                    + "。训练集为 timestamp≤T1，验证集为 T1<timestamp≤T2，测试集为 timestamp>T2。";
        }
        if (containsAny(normalized, "局限", "真实", "准确", "为什么100")) {
            return "100 分表示清洗后的记录全部符合当前可计算规则，不代表现实真实性已经得到外部核验。"
                    + "用户自填属性和评分内容无法仅凭格式证明真实；时效性也只针对 MovieLens 历史覆盖区间。";
        }
        return "本任务已生成真实的清洗与五维评估结果。你可以继续询问“各维度提升多少”、"
                + "“隔离了多少记录”、“T1/T2 如何使用”或“评分有哪些局限”。";
    }

    private static String scoreAnswer(JsonNode quality) {
        StringBuilder answer = new StringBuilder("清洗前后五维得分为：");
        JsonNode dimensions = quality.path("dimensions");
        for (int index = 0; index < DIMENSIONS.size(); index++) {
            String dimension = DIMENSIONS.get(index);
            JsonNode value = dimensions.path(dimension);
            answer.append(dimension).append(' ')
                    .append(value.path("before").path("score").asText()).append("→")
                    .append(value.path("after").path("score").asText())
                    .append("（+").append(value.path("delta").asText()).append("）");
            answer.append(index < DIMENSIONS.size() - 1 ? "，" : "。");
        }
        return answer.toString();
    }

    private static String cleaningAnswer(JsonNode cleaning) {
        long clean = 0;
        long invalid = 0;
        long duplicates = 0;
        long conflicts = 0;
        JsonNode actions = cleaning.path("actions");
        for (String dataset : List.of("ratings", "users", "movies")) {
            JsonNode action = actions.path(dataset);
            clean += action.path("cleanWritten").asLong();
            invalid += action.path("invalidQuarantined").asLong();
            duplicates += action.path("duplicatesRemoved").asLong();
            conflicts += action.path("conflictsQuarantined").asLong();
        }
        return "清洗版本保留 " + clean + " 条；规则或格式异常隔离 " + invalid
                + " 条；精确重复隔离 " + duplicates + " 条；冲突记录隔离 " + conflicts
                + " 条。隔离表示从 clean 版本排除并保留在 quarantine 中，不等同于自动修复。";
    }

    private static boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
