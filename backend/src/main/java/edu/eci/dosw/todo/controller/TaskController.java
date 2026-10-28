package edu.eci.dosw.todo.controller;

import edu.eci.dosw.todo.dto.TaskCreateRequest;
import edu.eci.dosw.todo.dto.TaskResponse;
import edu.eci.dosw.todo.dto.TaskUpdateRequest;
import edu.eci.dosw.todo.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public ResponseEntity<List<TaskResponse>> findAll(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(taskService.findAll(ownerId(jwt)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> findById(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return ResponseEntity.ok(taskService.findById(ownerId(jwt), id));
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody TaskCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.create(ownerId(jwt), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> update(@AuthenticationPrincipal Jwt jwt,
                                               @PathVariable Long id,
                                               @Valid @RequestBody TaskUpdateRequest request) {
        return ResponseEntity.ok(taskService.update(ownerId(jwt), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        taskService.delete(ownerId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    /** The owner is always the subject of the validated token, never a client-supplied id. */
    private Long ownerId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}
