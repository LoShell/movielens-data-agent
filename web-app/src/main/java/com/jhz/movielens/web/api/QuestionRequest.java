package com.jhz.movielens.web.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuestionRequest(
        @NotBlank(message = "追问内容不能为空")
        @Size(max = 500, message = "追问内容不能超过 500 个字符")
        String question) {
}
