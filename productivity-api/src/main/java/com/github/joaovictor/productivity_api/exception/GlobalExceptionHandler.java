package com.github.joaovictor.productivity_api.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.github.joaovictor.productivity_api")
public class GlobalExceptionHandler {

  // Logger para registrar erros (opcional, mas recomendado)
  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  // 404 - Recurso não encontrado
  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ApiError> handleNotFound(
      ResourceNotFoundException ex, HttpServletRequest request) {
    ApiError error =
        new ApiError(
            HttpStatus.NOT_FOUND.value(),
            "NOT_FOUND",
            ex.getMessage(),
            request.getRequestURI(), // Você pode adicionar o caminho da requisição aqui
            LocalDateTime.now());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
  }

  // 400 - validação de DTO
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(err -> err.getField() + ": " + err.getDefaultMessage())
            .collect(Collectors.joining(", "));

    ApiError error =
        new ApiError(
            HttpStatus.BAD_REQUEST.value(),
            "VALIDATION_ERROR",
            message, // ✅ era ex.getMessage()
            request.getRequestURI(),
            LocalDateTime.now());
    return ResponseEntity.badRequest().body(error);
  }

  // 400 - parâmetro com tipo inválido (ex: enum com valor que não existe)
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiError> handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    String message =
        String.format(
            "Parâmetro '%s' com valor inválido: '%s'. Valores aceitos: %s",
            ex.getName(),
            ex.getValue(),
            ex.getRequiredType() != null && ex.getRequiredType().isEnum()
                ? Arrays.toString(ex.getRequiredType().getEnumConstants())
                : ex.getRequiredType());

    ApiError error =
        new ApiError(
            HttpStatus.BAD_REQUEST.value(),
            "INVALID_PARAMETER",
            message,
            request.getRequestURI(),
            LocalDateTime.now());
    return ResponseEntity.badRequest().body(error);
  }

  // 400 - erros de constraint (query params, etc.)
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiError> handleConstraint(
      ConstraintViolationException ex, HttpServletRequest request) {
    ApiError error =
        new ApiError(
            HttpStatus.BAD_REQUEST.value(),
            "CONSTRAINT_ERROR",
            ex.getMessage(),
            request.getRequestURI(),
            LocalDateTime.now());

    return ResponseEntity.badRequest().body(error);
  }

  // 500 - Erro genérico

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest request) {
    log.error("Erro inesperado em {}: {}", request.getRequestURI(), ex.getMessage(), ex);

    ApiError error =
        new ApiError(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "INTERNAL_SERVER_ERROR",
            "Ocorreu um erro inesperado. Por favor, tente novamente mais tarde.",
            request.getRequestURI(),
            LocalDateTime.now());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
  }
}
