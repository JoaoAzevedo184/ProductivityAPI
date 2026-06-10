package com.github.joaovictor.productivity_api.boundary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Teste de CAIXA PRETA — Endpoints de listagem, filtro e ciclo de vida CRUD.
 *
 * <p>Complementa o {@code TaskApiIntegrationTest}, cobrindo os endpoints do {@code TaskController}
 * que antes não tinham teste de integração: filtros por status e prioridade, busca por título e o
 * ciclo completo criar → atualizar → buscar → deletar pela borda HTTP.
 *
 * <p>Roda no profile {@code dev}, onde o Flyway popula 15 tarefas de exemplo ({@code
 * V2__seed.sql}), garantindo que os filtros tenham dados reais para retornar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@DisplayName("Caixa Preta — Listagem, filtros e ciclo de vida CRUD")
class TaskControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  // ---------- Filtros (partição de equivalência: valores válidos) ----------

  @Test
  @DisplayName("GET /tasks/status/PENDING deve retornar página com content array")
  void filtrarPorStatus_deveRetornarPagina() throws Exception {
    mockMvc
        .perform(get("/tasks/status/PENDING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.totalElements").exists());
  }

  @Test
  @DisplayName("GET /tasks/priority/HIGH deve retornar página com content array")
  void filtrarPorPrioridade_deveRetornarPagina() throws Exception {
    mockMvc
        .perform(get("/tasks/priority/HIGH"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray())
        .andExpect(jsonPath("$.totalElements").exists());
  }

  @Test
  @DisplayName("GET /tasks/search?title=... deve retornar página (busca contains case-insensitive)")
  void buscarPorTitulo_deveRetornarPagina() throws Exception {
    mockMvc
        .perform(get("/tasks/search").param("title", "a"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  @Test
  @DisplayName("GET /tasks com paginação custom (size/sort) deve respeitar o size")
  void listarComPaginacaoCustom_deveRespeitarSize() throws Exception {
    mockMvc
        .perform(get("/tasks").param("page", "0").param("size", "5").param("sort", "title,asc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.size").value(5))
        .andExpect(jsonPath("$.content").isArray());
  }

  // ---------- Ciclo de vida completo: create → update → findById → delete ----------

  @Test
  @DisplayName("Ciclo CRUD: criar, atualizar status, confirmar e deletar uma tarefa")
  void cicloCompletoCrud() throws Exception {
    // 1. CREATE
    String corpoCriacao =
        """
        {
          "title": "Tarefa do ciclo CRUD",
          "description": "criada pelo teste de integracao",
          "status": "PENDING",
          "priority": "LOW"
        }
        """;

    MvcResult criacao =
        mockMvc
            .perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(corpoCriacao))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.completedAt").doesNotExist())
            .andReturn();

    JsonNode criada = objectMapper.readTree(criacao.getResponse().getContentAsString());
    long id = criada.get("id").asLong();

    // 2. UPDATE — transita para COMPLETED (deve preencher completedAt)
    String corpoUpdate =
        """
        { "status": "COMPLETED" }
        """;

    mockMvc
        .perform(put("/tasks/" + id).contentType(MediaType.APPLICATION_JSON).content(corpoUpdate))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.completedAt").exists());

    // 3. FIND BY ID — confirma o estado persistido
    mockMvc
        .perform(get("/tasks/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.status").value("COMPLETED"));

    // 4. DELETE — deve retornar 204 sem corpo
    mockMvc.perform(delete("/tasks/" + id)).andExpect(status().isNoContent());

    // 5. CONFIRMA REMOÇÃO — agora o id não existe mais
    mockMvc
        .perform(get("/tasks/" + id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("DELETE de id inexistente deve retornar 404 NOT_FOUND")
  void deletarInexistente_deveRetornar404() throws Exception {
    mockMvc
        .perform(delete("/tasks/99999"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  @DisplayName("PUT de id inexistente deve retornar 404 NOT_FOUND")
  void atualizarInexistente_deveRetornar404() throws Exception {
    String corpo =
        """
        { "title": "qualquer" }
        """;
    mockMvc
        .perform(put("/tasks/99999").contentType(MediaType.APPLICATION_JSON).content(corpo))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }
}
