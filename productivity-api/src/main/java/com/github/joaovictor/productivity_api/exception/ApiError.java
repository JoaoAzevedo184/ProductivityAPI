package com.github.joaovictor.productivity_api.exception;


import java.time.LocalDateTime;

// Classe para representar erros de API
public record ApiError(
        int status,
        String error,
        String message,
        String path,
        LocalDateTime timestamp
) { }
