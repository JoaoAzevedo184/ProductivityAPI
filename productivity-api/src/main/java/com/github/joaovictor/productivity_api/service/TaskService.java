package com.github.joaovictor.productivity_api.service;

import com.github.joaovictor.productivity_api.domain.Task;
import com.github.joaovictor.productivity_api.domain.dto.mapper.TaskMapper;
import com.github.joaovictor.productivity_api.domain.dto.request.CreateTaskRequest;
import com.github.joaovictor.productivity_api.domain.dto.request.UpdateTaskRequest;
import com.github.joaovictor.productivity_api.domain.dto.response.TaskResponse;
import com.github.joaovictor.productivity_api.domain.enums.Priority;
import com.github.joaovictor.productivity_api.domain.enums.TaskStatus;
import com.github.joaovictor.productivity_api.repository.TaskRepository;

import java.util.List;

public class TaskService {
    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    // criar tarefa
    public TaskResponse create(CreateTaskRequest request) {
        Task task = TaskMapper.toEntity(request);
        Task savedTask = taskRepository.save(task);
        return TaskMapper.toResponse(savedTask);
    }

    // atualizar tarefa
    public TaskResponse update(Long id, UpdateTaskRequest request){
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tarefa não encontrada com id: " + id));
        TaskMapper.updateEntity(task, request);
        Task updatedTask = taskRepository.save(task);
        return TaskMapper.toResponse(updatedTask);

    }

    // excluir tarefa
    public void delete(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new RuntimeException("Tarefa não encontrada com id:" + id);
        }
        taskRepository.deleteById(id);
    }

    // obter tarefa por ID
    public TaskResponse findById(Long id){
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tarefa não encontrada com id: " + id));
        return TaskMapper.toResponse(task);
    }

    // listar todas as tarefas
    public List<TaskResponse> findAll(){
        List<Task> tasks = taskRepository.findAll();
        return tasks.stream()
                .map(TaskMapper::toResponse)
                .toList();
    }

    // listar por status
    public List<TaskResponse> findByStatus(TaskStatus status) {
        return taskRepository.findByStatus(status)
                .stream()
                .map(TaskMapper::toResponse)
                .toList();
    }

    // listar por prioridade
    public List<TaskResponse> findByPriority(Priority priority) {
        return taskRepository.findByPriority(priority)
                .stream()
                .map(TaskMapper::toResponse)
                .toList();
    }

    // buscar por título
    public List<TaskResponse> searchByTitle(String title) {
        return taskRepository.findByTitleContainingIgnoreCase(title)
                .stream()
                .map(TaskMapper::toResponse)
                .toList();
    }
}
