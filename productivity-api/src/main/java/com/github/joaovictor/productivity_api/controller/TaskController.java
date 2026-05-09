package com.github.joaovictor.productivity_api.controller;

import com.github.joaovictor.productivity_api.domain.Task;
import com.github.joaovictor.productivity_api.domain.dto.request.CreateTaskRequest;
import com.github.joaovictor.productivity_api.domain.dto.request.UpdateTaskRequest;
import com.github.joaovictor.productivity_api.domain.dto.response.TaskResponse;
import com.github.joaovictor.productivity_api.domain.enums.Priority;
import com.github.joaovictor.productivity_api.domain.enums.TaskStatus;
import com.github.joaovictor.productivity_api.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    // Injeção direta do repositório para métodos que não utilizam o service, como GETs simples
    // GET 3.0: /tasks
    // Suporta paginação e ordenação, com valores padrão para evitar sobrecarga
    @GetMapping
    public ResponseEntity<Page<TaskResponse>> findAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(taskService.findAll(pageable));
    }

    // GET 2.0: /tasks/{id}
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> findById(@PathVariable Long id){
        return ResponseEntity.ok(taskService.findById(id));
    }

    // POST 2.0: /tasks
    @PostMapping
    public ResponseEntity<TaskResponse> create(@RequestBody @Valid CreateTaskRequest request){
        TaskResponse response = taskService.create(request);
        return ResponseEntity.ok(response);

    }

    // PUT 2.0: /tasks/{id}
    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdateTaskRequest updated){
        return ResponseEntity.ok(taskService.update(id, updated));
    }

    // DELETE 2.0: /tasks/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // GET  3.0: /tasks/status/{status}
    // Suporta paginação e ordenação, com valores padrão para evitar sobrecarga
    @GetMapping("/status/{status}")
    public ResponseEntity<Page<TaskResponse>> findByStatus(
            @PathVariable TaskStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(taskService.findByStatus(status, pageable));
    }

    // GET 3.0: /tasks/priority/{priority}
    @GetMapping("/priority/{priority}")
    public ResponseEntity<Page<TaskResponse>> findByPriority(
            @PathVariable Priority priority,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(taskService.findByPriority(priority, pageable));
    }

    // GET 3.0: /tasks/search?title=algumTitulo
    @GetMapping("/search")
    public ResponseEntity<Page<TaskResponse>> search(
            @RequestParam String title,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(taskService.searchByTitle(title, pageable));
    }
}
