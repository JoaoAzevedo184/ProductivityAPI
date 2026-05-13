package com.github.joaovictor.productivity_api.domain.dto.mapper;

import com.github.joaovictor.productivity_api.domain.Task;
import com.github.joaovictor.productivity_api.domain.dto.request.CreateTaskRequest;
import com.github.joaovictor.productivity_api.domain.dto.request.UpdateTaskRequest;
import com.github.joaovictor.productivity_api.domain.dto.response.TaskResponse;

// Mapper para converter entre Task e seus DTOs
public class TaskMapper {

  // Construtor privado para evitar instânciação
  private TaskMapper() {}

  // Converte CreateTaskRequest para Task, sem id e timestamps, que serão gerados automaticamente
  public static Task toEntity(CreateTaskRequest dto) {
    return Task.builder()
        .title(dto.title())
        .description(dto.description())
        .status(dto.status())
        .priority(dto.priority())
        .build();
    // createdAt é setado pelo @PrePersist da entidade
  }

  // Converte Task para TaskResponse, incluindo todos os campos
  public static TaskResponse toResponse(Task task) {
    return new TaskResponse(
        task.getId(),
        task.getTitle(),
        task.getDescription(),
        task.getStatus(),
        task.getPriority(),
        task.getCreatedAt(),
        task.getCompletedAt());
  }

  // Atualiza os campos de uma entidade Task com os valores do UpdateTaskRequest, se não forem nulos
  public static void updateEntity(Task task, UpdateTaskRequest dto) {
    if (dto.title() != null) task.setTitle(dto.title());
    if (dto.description() != null) task.setDescription(dto.description());
    if (dto.status() != null) task.setStatus(dto.status());
    if (dto.priority() != null) task.setPriority(dto.priority());
  }
}
