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
            return "任务尚未成功完成，目前没有可用于回答的实际报告。";
        }
        String systemPrompt = """
                你是 MovieLens 数据分析 Agent 的报告解释模块。
                只能依据提供的真实任务元数据和报告 JSON 回答。
                不得编造数字、执行状态、规则或结论；报告没有提供的信息必须明确说无法判断。
                对数据治理报告必须区分修复、去重和隔离；100 分仅表示通过当前规则检查。
                对机器学习报告必须区分验证与测试指标、估计概率与已观测标签，并说明所用模型及数据版本。
                机器学习结果中的 provenance 是其数据来源；任务壳的 inputVersion/outputVersion 是治理任务预留字段，不得误认为机器学习输入或输出版本。
                回答应简洁、使用中文，并在涉及数字时引用报告中的具体值。
                """;
        String userPrompt = """
                任务壳元数据：
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
