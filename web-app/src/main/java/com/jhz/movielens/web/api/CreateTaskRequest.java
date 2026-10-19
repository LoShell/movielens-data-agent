package com.jhz.movielens.web.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateTaskRequest(
        @NotBlank(message = "请求内容不能为空")
        @Size(max = 2000, message = "请求内容不能超过 2000 个字符")
        String prompt,
        @Size(max = 2, message = "最多指定两个工具") List<String> requestedTools) {
}
