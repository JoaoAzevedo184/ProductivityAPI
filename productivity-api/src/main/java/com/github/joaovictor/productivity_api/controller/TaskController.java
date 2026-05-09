package com.github.joaovictor.productivity_api.controller;

import com.github.joaovictor.productivity_api.domain.Task;
import com.github.joaovictor.productivity_api.domain.dto.request.CreateTaskRequest;
import com.github.joaovictor.productivity_api.domain.dto.request.UpdateTaskRequest;
import com.github.joaovictor.productivity_api.domain.dto.response.TaskResponse;
import com.github.joaovictor.productivity_api.domain.enums.Priority;
import com.github.joaovictor.productivity_api.domain.enums.TaskStatus;
import com.github.joaovictor.productivity_api.service.TaskService;
import jakarta.validation.Valid;
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
    // GET 2.0: /tasks
    @GetMapping
    public ResponseEntity<List<TaskResponse>> findAll() {
        return ResponseEntity.ok(taskService.findAll());
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
                                               @RequestBody UpdateTaskRequest updated){
        return ResponseEntity.ok(taskService.update(id, updated));
    }

    // DELETE 2.0: /tasks/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // GET  2.0: /tasks/status/{status}
    @GetMapping("/status/{status}")
    public ResponseEntity<List<TaskResponse>> findByStatus(@PathVariable String status) {
        return ResponseEntity.ok(
                taskService.findByStatus(TaskStatus.valueOf(status.toUpperCase()))
        );
    }

    // GET 2.0: /tasks/priority/{priority}
    @GetMapping("/priority/{priority}")
    public ResponseEntity<List<TaskResponse>> findByPriority(@PathVariable Priority priority) {
        return ResponseEntity.ok(taskService.findByPriority(priority));
    }

    // GET 2.0: /tasks/search?title=algumTitulo
    @GetMapping("/search")
    public ResponseEntity<List<TaskResponse>> search(@RequestParam String title) {
        return ResponseEntity.ok(taskService.searchByTitle(title));
    }

    /* Metodo antigo, sem service e DTOs
    * // GET: /tasks
    @GetMapping
    public ResponseEntity<List<Task>> getAllTasks() {
        return ResponseEntity.ok(taskRepository.findAll());
    }

    // GET: /tasks/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Task> findById(@PathVariable Long id){
        return taskRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // POST: /tasks
    @PostMapping
    public ResponseEntity<Task> createTask(@RequestBody Task task){
        Task savedTask = taskRepository.save(task);
        return ResponseEntity.ok(savedTask);
    }

    // PUT: /tasks/{id}
    @PutMapping("/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable Long id, @RequestBody Task updated){
        return taskRepository.findById(id)
                .map(task -> {
                    task.setTitle(updated.getTitle());
                    task.setDescription(updated.getDescription());
                    task.setStatus(updated.getStatus());
                    task.setPriority(updated.getPriority());
                    return ResponseEntity.ok(taskRepository.save(task));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // DELETE: /tasks/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!taskRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        taskRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // GET: /tasks/status/{status}
    @GetMapping("/status/{status}")
    public ResponseEntity<List<Task>> findByStatus(@PathVariable TaskStatus status) {
        return ResponseEntity.ok(taskRepository.findByStatus(status));
    }

    // GET: /tasks/priority/{priority}
    @GetMapping("/priority/{priority}")
    public ResponseEntity<List<Task>> findByPriority(@PathVariable Priority priority) {
        return ResponseEntity.ok(taskRepository.findByPriority(priority));
    }

    // GET: /tasks/search?title=algumTitulo
    @GetMapping("/search")
    public ResponseEntity<List<Task>> search(@RequestParam String title) {
        return ResponseEntity.ok(taskRepository.findByTitleContainingIgnoreCase(title));
    }*/
}
