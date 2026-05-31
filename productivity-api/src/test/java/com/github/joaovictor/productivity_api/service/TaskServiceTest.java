package com.github.joaovictor.productivity_api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.joaovictor.productivity_api.domain.Task;
import com.github.joaovictor.productivity_api.domain.dto.request.CreateTaskRequest;
import com.github.joaovictor.productivity_api.domain.dto.request.UpdateTaskRequest;
import com.github.joaovictor.productivity_api.domain.dto.response.TaskResponse;
import com.github.joaovictor.productivity_api.domain.enums.Priority;
import com.github.joaovictor.productivity_api.domain.enums.TaskStatus;
import com.github.joaovictor.productivity_api.exception.ResourceNotFoundException;
import com.github.joaovictor.productivity_api.repository.TaskRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Testes unitários puros do {@link TaskService}.
 *
 * <p>Não sobem contexto Spring — usam {@link MockitoExtension} para injetar um mock do {@link
 * TaskRepository}. Foco: regras de negócio do service e tratamento de erros.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskService — testes unitários")
class TaskServiceTest {

  @Mock private TaskRepository taskRepository;

  private TaskService taskService;

  private Task tarefaPersistida;

  @BeforeEach
  void setUp() {
    taskService = new TaskService(taskRepository, new SimpleMeterRegistry());

    tarefaPersistida =
        Task.builder()
            .id(1L)
            .title("Tarefa exemplo")
            .description("Descrição exemplo")
            .status(TaskStatus.PENDING)
            .priority(Priority.MEDIUM)
            .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
            .completedAt(null)
            .build();
  }

  // ============================================================
  // create
  // ============================================================

  @Nested
  @DisplayName("create()")
  class Create {

    @Test
    @DisplayName("deve persistir a tarefa e retornar TaskResponse com os mesmos campos")
    void deveCriarTarefaERetornarResponse() {
      CreateTaskRequest request =
          new CreateTaskRequest("Nova tarefa", "Descrição", TaskStatus.PENDING, Priority.HIGH);
      when(taskRepository.save(any(Task.class)))
          .thenAnswer(
              invocation -> {
                Task t = invocation.getArgument(0);
                t.setId(42L);
                t.setCreatedAt(LocalDateTime.now());
                return t;
              });

      TaskResponse response = taskService.create(request);

      assertThat(response.id()).isEqualTo(42L);
      assertThat(response.title()).isEqualTo("Nova tarefa");
      assertThat(response.description()).isEqualTo("Descrição");
      assertThat(response.status()).isEqualTo(TaskStatus.PENDING);
      assertThat(response.priority()).isEqualTo(Priority.HIGH);
      assertThat(response.completedAt()).isNull();
    }

    @Test
    @DisplayName("não deve setar completedAt mesmo quando criada já como COMPLETED")
    void naoDeveSetarCompletedAtNaCriacao() {
      // Razão: a regra de completedAt vive no update(), não no create.
      // Se uma tarefa nasce COMPLETED, o completedAt fica null até alguém atualizar.
      CreateTaskRequest request =
          new CreateTaskRequest("Tarefa já concluída", "x", TaskStatus.COMPLETED, Priority.LOW);
      ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
      when(taskRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

      taskService.create(request);

      assertThat(captor.getValue().getCompletedAt()).isNull();
    }
  }

  // ============================================================
  // update
  // ============================================================

  @Nested
  @DisplayName("update()")
  class Update {

    @Test
    @DisplayName("deve atualizar apenas os campos não-nulos do request")
    void deveAtualizarApenasCamposNaoNulos() {
      UpdateTaskRequest request = new UpdateTaskRequest("Título novo", null, null, null);
      when(taskRepository.findById(1L)).thenReturn(Optional.of(tarefaPersistida));
      when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

      TaskResponse response = taskService.update(1L, request);

      assertThat(response.title()).isEqualTo("Título novo");
      assertThat(response.description()).isEqualTo("Descrição exemplo"); // não mudou
      assertThat(response.status()).isEqualTo(TaskStatus.PENDING); // não mudou
      assertThat(response.priority()).isEqualTo(Priority.MEDIUM); // não mudou
    }

    @Test
    @DisplayName("deve setar completedAt ao transitar para COMPLETED")
    void deveSetarCompletedAtAoTransitarParaCompleted() {
      UpdateTaskRequest request = new UpdateTaskRequest(null, null, TaskStatus.COMPLETED, null);
      when(taskRepository.findById(1L)).thenReturn(Optional.of(tarefaPersistida));
      when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

      LocalDateTime antes = LocalDateTime.now();
      TaskResponse response = taskService.update(1L, request);
      LocalDateTime depois = LocalDateTime.now();

      assertThat(response.status()).isEqualTo(TaskStatus.COMPLETED);
      assertThat(response.completedAt()).isNotNull().isBetween(antes, depois);
    }

    @Test
    @DisplayName("deve limpar completedAt ao reabrir tarefa concluída (COMPLETED → PENDING)")
    void deveLimparCompletedAtAoReabrir() {
      // Tarefa já concluída
      tarefaPersistida.setStatus(TaskStatus.COMPLETED);
      tarefaPersistida.setCompletedAt(LocalDateTime.of(2026, 1, 5, 14, 30));

      UpdateTaskRequest request = new UpdateTaskRequest(null, null, TaskStatus.PENDING, null);
      when(taskRepository.findById(1L)).thenReturn(Optional.of(tarefaPersistida));
      when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

      TaskResponse response = taskService.update(1L, request);

      assertThat(response.status()).isEqualTo(TaskStatus.PENDING);
      assertThat(response.completedAt()).isNull();
    }

    @Test
    @DisplayName("deve limpar completedAt ao voltar de COMPLETED para IN_PROGRESS")
    void deveLimparCompletedAtAoVoltarParaInProgress() {
      tarefaPersistida.setStatus(TaskStatus.COMPLETED);
      tarefaPersistida.setCompletedAt(LocalDateTime.now());

      UpdateTaskRequest request = new UpdateTaskRequest(null, null, TaskStatus.IN_PROGRESS, null);
      when(taskRepository.findById(1L)).thenReturn(Optional.of(tarefaPersistida));
      when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

      TaskResponse response = taskService.update(1L, request);

      assertThat(response.completedAt()).isNull();
    }

    @Test
    @DisplayName("não deve mexer no completedAt em transições que não envolvem COMPLETED")
    void naoDeveMexerNoCompletedAtEmTransicoesNeutras() {
      // PENDING → IN_PROGRESS, completedAt continua null
      UpdateTaskRequest request = new UpdateTaskRequest(null, null, TaskStatus.IN_PROGRESS, null);
      when(taskRepository.findById(1L)).thenReturn(Optional.of(tarefaPersistida));
      when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

      TaskResponse response = taskService.update(1L, request);

      assertThat(response.status()).isEqualTo(TaskStatus.IN_PROGRESS);
      assertThat(response.completedAt()).isNull();
    }

    @Test
    @DisplayName("não deve mexer em completedAt se status no request é igual ao atual")
    void naoDeveMexerEmCompletedAtSeStatusIgual() {
      // Cenário: tarefa já está COMPLETED com completedAt setado.
      // Update sem mudança real de status (manda COMPLETED de novo).
      LocalDateTime completedAtOriginal = LocalDateTime.of(2026, 1, 5, 14, 30);
      tarefaPersistida.setStatus(TaskStatus.COMPLETED);
      tarefaPersistida.setCompletedAt(completedAtOriginal);

      UpdateTaskRequest request =
          new UpdateTaskRequest("novo título", null, TaskStatus.COMPLETED, null);
      when(taskRepository.findById(1L)).thenReturn(Optional.of(tarefaPersistida));
      when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

      TaskResponse response = taskService.update(1L, request);

      // completedAt preservado — não foi sobrescrito
      assertThat(response.completedAt()).isEqualTo(completedAtOriginal);
    }

    @Test
    @DisplayName("deve lançar ResourceNotFoundException quando id não existe")
    void deveLancarQuandoIdNaoExiste() {
      UpdateTaskRequest request = new UpdateTaskRequest("qualquer", null, null, null);
      when(taskRepository.findById(999L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> taskService.update(999L, request))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("999");

      verify(taskRepository, never()).save(any());
    }
  }

  // ============================================================
  // delete
  // ============================================================

  @Nested
  @DisplayName("delete()")
  class Delete {

    @Test
    @DisplayName("deve deletar quando id existe")
    void deveDeletarQuandoIdExiste() {
      when(taskRepository.existsById(1L)).thenReturn(true);

      taskService.delete(1L);

      verify(taskRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("deve lançar ResourceNotFoundException quando id não existe")
    void deveLancarQuandoIdNaoExiste() {
      when(taskRepository.existsById(999L)).thenReturn(false);

      assertThatThrownBy(() -> taskService.delete(999L))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("999");

      verify(taskRepository, never()).deleteById(any());
    }
  }

  // ============================================================
  // findById
  // ============================================================

  @Nested
  @DisplayName("findById()")
  class FindById {

    @Test
    @DisplayName("deve retornar TaskResponse quando id existe")
    void deveRetornarQuandoIdExiste() {
      when(taskRepository.findById(1L)).thenReturn(Optional.of(tarefaPersistida));

      TaskResponse response = taskService.findById(1L);

      assertThat(response.id()).isEqualTo(1L);
      assertThat(response.title()).isEqualTo("Tarefa exemplo");
    }

    @Test
    @DisplayName("deve lançar ResourceNotFoundException quando id não existe")
    void deveLancarQuandoIdNaoExiste() {
      when(taskRepository.findById(999L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> taskService.findById(999L))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("999");
    }
  }

  // ============================================================
  // findAll (paginado)
  // ============================================================

  @Nested
  @DisplayName("findAll(Pageable)")
  class FindAllPageable {

    @Test
    @DisplayName("deve retornar página de TaskResponse")
    void deveRetornarPaginaDeResponses() {
      Pageable pageable = PageRequest.of(0, 10);
      Page<Task> pagina = new PageImpl<>(List.of(tarefaPersistida), pageable, 1);
      when(taskRepository.findAll(pageable)).thenReturn(pagina);

      Page<TaskResponse> resultado = taskService.findAll(pageable);

      assertThat(resultado.getTotalElements()).isEqualTo(1);
      assertThat(resultado.getContent()).hasSize(1);
      assertThat(resultado.getContent().get(0).title()).isEqualTo("Tarefa exemplo");
    }

    @Test
    @DisplayName("deve retornar página vazia quando não há tarefas")
    void deveRetornarPaginaVazia() {
      Pageable pageable = PageRequest.of(0, 10);
      when(taskRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(), pageable, 0));

      Page<TaskResponse> resultado = taskService.findAll(pageable);

      assertThat(resultado.getTotalElements()).isZero();
      assertThat(resultado.getContent()).isEmpty();
    }
  }

  // ============================================================
  // findAll (sem paginação — método legado)
  // ============================================================

  @Nested
  @DisplayName("findAll() sem paginação")
  class FindAllList {

    @Test
    @DisplayName("deve retornar lista completa")
    void deveRetornarLista() {
      when(taskRepository.findAll()).thenReturn(List.of(tarefaPersistida));

      List<TaskResponse> resultado = taskService.findAll();

      assertThat(resultado).hasSize(1);
      assertThat(resultado.get(0).id()).isEqualTo(1L);
    }
  }

  // ============================================================
  // findByStatus
  // ============================================================

  @Nested
  @DisplayName("findByStatus()")
  class FindByStatus {

    @Test
    @DisplayName("deve filtrar tarefas pelo status informado")
    void deveFiltrarPorStatus() {
      Pageable pageable = PageRequest.of(0, 10);
      Page<Task> pagina = new PageImpl<>(List.of(tarefaPersistida), pageable, 1);
      when(taskRepository.findByStatus(TaskStatus.PENDING, pageable)).thenReturn(pagina);

      Page<TaskResponse> resultado = taskService.findByStatus(TaskStatus.PENDING, pageable);

      assertThat(resultado.getContent()).hasSize(1);
      assertThat(resultado.getContent().get(0).status()).isEqualTo(TaskStatus.PENDING);
    }
  }

  // ============================================================
  // findByPriority
  // ============================================================

  @Nested
  @DisplayName("findByPriority()")
  class FindByPriority {

    @Test
    @DisplayName("deve filtrar tarefas pela prioridade informada")
    void deveFiltrarPorPrioridade() {
      Pageable pageable = PageRequest.of(0, 10);
      tarefaPersistida.setPriority(Priority.HIGH);
      Page<Task> pagina = new PageImpl<>(List.of(tarefaPersistida), pageable, 1);
      when(taskRepository.findByPriority(Priority.HIGH, pageable)).thenReturn(pagina);

      Page<TaskResponse> resultado = taskService.findByPriority(Priority.HIGH, pageable);

      assertThat(resultado.getContent()).hasSize(1);
      assertThat(resultado.getContent().get(0).priority()).isEqualTo(Priority.HIGH);
    }
  }

  // ============================================================
  // searchByTitle
  // ============================================================

  @Nested
  @DisplayName("searchByTitle()")
  class SearchByTitle {

    @Test
    @DisplayName("deve buscar por título usando contains case-insensitive")
    void deveBuscarPorTituloContains() {
      Pageable pageable = PageRequest.of(0, 10);
      Page<Task> pagina = new PageImpl<>(List.of(tarefaPersistida), pageable, 1);
      when(taskRepository.findByTitleContainingIgnoreCase("exemplo", pageable)).thenReturn(pagina);

      Page<TaskResponse> resultado = taskService.searchByTitle("exemplo", pageable);

      assertThat(resultado.getContent()).hasSize(1);
      assertThat(resultado.getContent().get(0).title()).containsIgnoringCase("exemplo");
    }

    @Test
    @DisplayName("deve retornar página vazia quando nenhum título casa")
    void deveRetornarVazioQuandoNadaCasa() {
      Pageable pageable = PageRequest.of(0, 10);
      when(taskRepository.findByTitleContainingIgnoreCase("nada", pageable))
          .thenReturn(new PageImpl<>(List.of(), pageable, 0));

      Page<TaskResponse> resultado = taskService.searchByTitle("nada", pageable);

      assertThat(resultado.getContent()).isEmpty();
    }
  }
}
