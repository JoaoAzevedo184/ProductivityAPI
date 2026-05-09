package com.github.joaovictor.productivity_api.domain.dto.response;

import com.github.joaovictor.productivity_api.domain.enums.Priority;
import com.github.joaovictor.productivity_api.domain.enums.TaskStatus;

import java.time.LocalDateTime;

// DTO para resposta de tarefas
public record TaskResponse(

    Long id,
    String title,
    String description,
    TaskStatus status,
    Priority priority,
    LocalDateTime createdAt,
    LocalDateTime completedAt
) {}
