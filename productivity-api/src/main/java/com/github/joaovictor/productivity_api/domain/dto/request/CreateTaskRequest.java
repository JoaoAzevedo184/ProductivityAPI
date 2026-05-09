package com.github.joaovictor.productivity_api.domain.dto.request;

import com.github.joaovictor.productivity_api.domain.enums.Priority;
import com.github.joaovictor.productivity_api.domain.enums.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


// DTO para criação de tarefa, com validação dos campos
public record CreateTaskRequest(

        @NotBlank(message = "O título é obrigatório")
        @Size(max = 100)
        String title,

        @NotBlank(message = "A descrição é obrigatória")
        @Size(max = 500)
        String description,

        @NotBlank(message = "O status é obrigatória")
        TaskStatus status,

        @NotBlank(message = "A prioridade é obrigatória")
        Priority priority
) {}
