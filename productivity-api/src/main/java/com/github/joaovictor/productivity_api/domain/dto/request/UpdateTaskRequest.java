package com.github.joaovictor.productivity_api.domain.dto.request;

import com.github.joaovictor.productivity_api.domain.enums.Priority;
import com.github.joaovictor.productivity_api.domain.enums.TaskStatus;
import jakarta.validation.constraints.Size;

// DTO para atualização de tarefa, permite atualização parcial dos campos, sem validação de obrigatoriedade
public record UpdateTaskRequest(

        @Size(max = 100)
        String title,

        @Size(max = 500)
        String description,

        TaskStatus status,

        Priority priority
) {}
