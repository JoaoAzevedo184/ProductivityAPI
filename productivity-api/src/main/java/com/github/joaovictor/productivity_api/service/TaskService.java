package com.github.joaovictor.productivity_api.service;

import com.github.joaovictor.productivity_api.domain.Task;
import com.github.joaovictor.productivity_api.domain.dto.mapper.TaskMapper;
import com.github.joaovictor.productivity_api.domain.dto.request.CreateTaskRequest;
import com.github.joaovictor.productivity_api.domain.dto.request.UpdateTaskRequest;
import com.github.joaovictor.productivity_api.domain.dto.response.TaskResponse;
import com.github.joaovictor.productivity_api.domain.enums.Priority;
import com.github.joaovictor.productivity_api.domain.enums.TaskStatus;
import com.github.joaovictor.productivity_api.exception.ResourceNotFoundException;
import com.github.joaovictor.productivity_api.repository.TaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskService {
  private final TaskRepository taskRepository;

  public TaskService(TaskRepository taskRepository) {
    this.taskRepository = taskRepository;
  }

  // criar tarefa
  @Transactional
  public TaskResponse create(CreateTaskRequest request) {
    Task task = TaskMapper.toEntity(request);
    Task savedTask = taskRepository.save(task);
    return TaskMapper.toResponse(savedTask);
  }

  // atualizar tarefa
  @Transactional
  public TaskResponse update(Long id, UpdateTaskRequest request) {
    Task task =
        taskRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("Tarefa não encontrada com id: " + id));

    TaskStatus statusAnterior = task.getStatus();
    TaskMapper.updateEntity(task, request);

    // Regra de negócio: gerenciar completedAt conforme transição de status
    if (request.status() != null && request.status() != statusAnterior) {
      if (request.status() == TaskStatus.COMPLETED) {
        task.setCompletedAt(LocalDateTime.now());
      } else {
        task.setCompletedAt(null); // reabriu a tarefa → limpa
      }
    }

    Task updatedTask = taskRepository.save(task);
    return TaskMapper.toResponse(updatedTask);
  }

  // excluir tarefa
  @Transactional
  public void delete(Long id) {
    if (!taskRepository.existsById(id)) {
      throw new ResourceNotFoundException("Tarefa não encontrada com id:" + id);
    }
    taskRepository.deleteById(id);
  }

  // obter tarefa por ID
  public TaskResponse findById(Long id) {
    Task task =
        taskRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("Tarefa não encontrada com id: " + id));
    return TaskMapper.toResponse(task);
  }

  // listar todas as tarefas
  public List<TaskResponse> findAll() {
    List<Task> tasks = taskRepository.findAll();
    return tasks.stream().map(TaskMapper::toResponse).toList();
  }

  // listar tarefas com paginação
  public Page<TaskResponse> findAll(Pageable pageable) {
    return taskRepository.findAll(pageable).map(TaskMapper::toResponse);
  }

  // listar tarefas por status com paginação
  public Page<TaskResponse> findByStatus(TaskStatus status, Pageable pageable) {
    return taskRepository.findByStatus(status, pageable).map(TaskMapper::toResponse);
  }

  // listar tarefas por prioridade com paginação
  public Page<TaskResponse> findByPriority(Priority priority, Pageable pageable) {
    return taskRepository.findByPriority(priority, pageable).map(TaskMapper::toResponse);
  }

  // listar tarefas por título com paginação
  public Page<TaskResponse> searchByTitle(String title, Pageable pageable) {
    return taskRepository
        .findByTitleContainingIgnoreCase(title, pageable)
        .map(TaskMapper::toResponse);
  }
}
