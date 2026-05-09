package com.github.joaovictor.productivity_api.domain.dto.mapper;

import com.github.joaovictor.productivity_api.domain.Task;
import com.github.joaovictor.productivity_api.domain.dto.request.CreateTaskRequest;
import com.github.joaovictor.productivity_api.domain.dto.request.UpdateTaskRequest;
import com.github.joaovictor.productivity_api.domain.dto.response.TaskResponse;

import java.time.LocalDateTime;

// Mapper para converter entre Task e seus DTOs
public class TaskMapper {

    // Converte CreateTaskRequest para Task
    public static Task toEntity(CreateTaskRequest dto) {
        return Task.builder()
                .title(dto.title())
                .description(dto.description())
                .status(dto.status())
                .priority(dto.priority())
                .createdAt(LocalDateTime.now())
                .build();
    }

    // Converte Task para TaskResponse
    public static TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getCreatedAt(),
                task.getCompletedAt()
        );
    }

    // Atualiza os campos de Task com os valores de UpdateTaskRequest, se não forem nulos
    public static void updateEntity(Task task, UpdateTaskRequest dto) {

        // titulo
        if (dto.title() != null) {
            task.setTitle(dto.title());
        }

        // descrição
        if (dto.description() != null) {
            task.setDescription(dto.description());
        }

        // status
        if (dto.status() != null) {
            task.setStatus(dto.status());

            // regra de negócio (nível profissional)
            if (dto.status().name().equals("COMPLETED")) {
                task.setCompletedAt(LocalDateTime.now());
            }
        }

        // prioridade
        if (dto.priority() != null) {
            task.setPriority(dto.priority());
        }
    }
}
