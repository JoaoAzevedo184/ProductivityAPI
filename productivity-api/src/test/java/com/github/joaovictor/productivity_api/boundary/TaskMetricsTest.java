package com.github.joaovictor.productivity_api.boundary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.github.joaovictor.productivity_api.domain.Task;
import com.github.joaovictor.productivity_api.domain.dto.request.CreateTaskRequest;
import com.github.joaovictor.productivity_api.domain.dto.request.UpdateTaskRequest;
import com.github.joaovictor.productivity_api.domain.enums.Priority;
import com.github.joaovictor.productivity_api.domain.enums.TaskStatus;
import com.github.joaovictor.productivity_api.repository.TaskRepository;
import com.github.joaovictor.productivity_api.service.TaskService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Teste de CAIXA BRANCA — Instrumentação de métricas de negócio (Micrometer).
 *
 * <p>Conhecendo a estrutura interna do {@link TaskService}, sabemos que ele instrumenta dois
 * meters:
 *
 * <ul>
 *   <li>{@code tasks_created_total} — Counter incrementado a cada {@code create()}, rotulado por
 *       {@code priority}.
 *   <li>{@code tasks_completion_duration_seconds} — Timer registrado apenas na transição real para
 *       {@code COMPLETED}.
 * </ul>
 *
 * <p>Usamos um {@link SimpleMeterRegistry} real (em memória) para inspecionar os meters após
 * exercer o código — exercitando o caminho de instrumentação, não apenas o retorno do método.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Caixa Branca — Métricas de negócio (Micrometer)")
class TaskMetricsTest {

  @Mock private TaskRepository taskRepository;

  private SimpleMeterRegistry registry;
  private TaskService taskService;
  private Task tarefaBase;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    taskService = new TaskService(taskRepository, registry);

    tarefaBase =
        Task.builder()
            .id(1L)
            .title("Tarefa")
            .description("desc")
            .status(TaskStatus.PENDING)
            .priority(Priority.MEDIUM)
            .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .completedAt(null)
            .build();
  }

  @Test
  @DisplayName("create() deve incrementar tasks_created_total com a tag de prioridade correta")
  void create_deveIncrementarContadorPorPrioridade() {
    when(taskRepository.save(any(Task.class)))
        .thenAnswer(
            inv -> {
              Task t = inv.getArgument(0);
              t.setId(10L);
              return t;
            });

    taskService.create(new CreateTaskRequest("nova", "desc", TaskStatus.PENDING, Priority.HIGH));

    Counter counter = registry.find("tasks_created_total").tag("priority", "HIGH").counter();

    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("dois create() de prioridades diferentes geram contadores separados por tag")
  void create_contadoresSeparadosPorTag() {
    when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

    taskService.create(new CreateTaskRequest("a", "d", TaskStatus.PENDING, Priority.LOW));
    taskService.create(new CreateTaskRequest("b", "d", TaskStatus.PENDING, Priority.LOW));
    taskService.create(new CreateTaskRequest("c", "d", TaskStatus.PENDING, Priority.HIGH));

    Counter low = registry.find("tasks_created_total").tag("priority", "LOW").counter();
    Counter high = registry.find("tasks_created_total").tag("priority", "HIGH").counter();

    assertThat(low).isNotNull();
    assertThat(low.count()).isEqualTo(2.0);
    assertThat(high).isNotNull();
    assertThat(high.count()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("update() para COMPLETED deve registrar uma amostra no Timer de conclusão")
  void update_paraCompleted_deveRegistrarTimer() {
    when(taskRepository.findById(1L)).thenReturn(Optional.of(tarefaBase));
    when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

    taskService.update(1L, new UpdateTaskRequest(null, null, TaskStatus.COMPLETED, null));

    Timer timer = registry.find("tasks_completion_duration_seconds").timer();
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isEqualTo(1L);
  }

  @Test
  @DisplayName("update() em transição neutra (PENDING→IN_PROGRESS) NÃO deve registrar o Timer")
  void update_transicaoNeutra_naoRegistraTimer() {
    when(taskRepository.findById(1L)).thenReturn(Optional.of(tarefaBase));
    when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

    taskService.update(1L, new UpdateTaskRequest(null, null, TaskStatus.IN_PROGRESS, null));

    Timer timer = registry.find("tasks_completion_duration_seconds").timer();
    // O Timer é criado no construtor, mas não deve ter amostras nesta transição.
    assertThat(timer).isNotNull();
    assertThat(timer.count()).isZero();
  }
}
