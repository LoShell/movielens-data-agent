package com.jhz.movielens.web.agent;

import com.jhz.movielens.agent.llm.TextLlmClient;
import com.jhz.movielens.web.config.JsonTreeConverter;
import com.jhz.movielens.web.task.TaskSnapshot;
import com.jhz.movielens.web.task.TaskStatus;
import org.springframework.stereotype.Component;

@Component
public class ReportQuestionAnswerer {
    private final TextLlmClient llmClient;
    private final JsonTreeConverter jsonTreeConverter;

    public ReportQuestionAnswerer(TextLlmClient llmClient, JsonTreeConverter jsonTreeConverter) {
        this.llmClient = llmClient;
        this.jsonTreeConverter = jsonTreeConverter;
    }

    public String answer(TaskSnapshot task, String question) {
        if (task.status() != TaskStatus.SUCCEEDED || task.result() == null) {
            return "任务尚未成功完成，目前没有可用于回答的实际评估报告。";
        }
        String systemPrompt = """
                你是 MovieLens 数据治理 Agent 的报告解释模块。
                只能依据提供的真实任务元数据和报告 JSON 回答。
                不得编造数字、执行状态、规则或结论；报告没有提供的信息必须明确说无法判断。
                必须区分修复、去重和隔离。100 分只表示通过当前评价规则，不代表现实真实性得到外部核验。
                回答应简洁、使用中文，并在涉及数字时引用报告中的具体值。
                """;
        String userPrompt = """
                任务元数据：
                taskId=%s
                inputVersion=%s
                outputVersion=%s
                rulesVersion=%s

                实际任务报告 JSON：
                %s

                用户追问：
                %s
                """.formatted(task.taskId(), task.inputVersion(), task.outputVersion(),
                task.rulesVersion(), jsonTreeConverter.toJson(task.result()), question);
        return llmClient.complete(systemPrompt, userPrompt);
    }
}
