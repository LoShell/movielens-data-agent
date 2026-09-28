package com.jhz.movielens.web.api;

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

    public TaskController(GovernanceTaskService taskService) {
        this.taskService = taskService;
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
}
