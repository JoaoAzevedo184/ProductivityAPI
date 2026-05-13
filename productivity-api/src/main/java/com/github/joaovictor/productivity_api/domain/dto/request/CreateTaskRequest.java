package com.github.joaovictor.productivity_api.domain.dto.request;

import com.github.joaovictor.productivity_api.domain.enums.Priority;
import com.github.joaovictor.productivity_api.domain.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// DTO para criação de tarefa, com validação dos campos
public record CreateTaskRequest(
    @NotBlank(message = "O título é obrigatório") @Size(max = 100) String title,
    @NotBlank(message = "A descrição é obrigatória") @Size(max = 500) String description,
    @NotNull(message = "O status é obrigatório") TaskStatus status,
    @NotNull(message = "A prioridade é obrigatória") Priority priority) {}
