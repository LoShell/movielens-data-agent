package com.jhz.movielens.web.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
        @NotBlank(message = "请求内容不能为空")
        @Size(max = 2000, message = "请求内容不能超过 2000 个字符")
        String prompt) {
}
