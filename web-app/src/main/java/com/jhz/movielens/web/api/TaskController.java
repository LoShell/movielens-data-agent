package com.jhz.movielens.web.api;

import com.jhz.movielens.web.agent.ReportQuestionAnswerer;
import com.jhz.movielens.web.task.GovernanceTaskService;
import com.jhz.movielens.web.task.TaskSnapshot;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final GovernanceTaskService taskService;
    private final ReportQuestionAnswerer questionAnswerer;

    public TaskController(GovernanceTaskService taskService, ReportQuestionAnswerer questionAnswerer) {
        this.taskService = taskService;
        this.questionAnswerer = questionAnswerer;
    }

    @PostMapping
    public ResponseEntity<TaskSnapshot> create(@Valid @RequestBody CreateTaskRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(taskService.submit(request.prompt().trim()));
    }

    @GetMapping("/{taskId}")
    public TaskSnapshot get(@PathVariable String taskId) {
        TaskSnapshot task = taskService.get(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        return task;
    }

    @PostMapping("/{taskId}/questions")
    public QuestionResponse ask(@PathVariable String taskId, @Valid @RequestBody QuestionRequest request) {
        TaskSnapshot task = get(taskId);
        String question = request.question().trim();
        return new QuestionResponse(taskId, question, questionAnswerer.answer(task, question));
    }
}
