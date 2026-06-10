package com.github.joaovictor.productivity_api.boundary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.joaovictor.productivity_api.domain.dto.request.CreateTaskRequest;
import com.github.joaovictor.productivity_api.domain.enums.Priority;
import com.github.joaovictor.productivity_api.domain.enums.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Teste de CAIXA PRETA — Análise de Valor-Limite (Boundary Value Analysis).
 *
 * <p>O campo {@code title} do {@link CreateTaskRequest} é anotado com {@code @Size(max = 100)} e
 * {@code @NotBlank}. As fronteiras de validação são, portanto:
 *
 * <ul>
 *   <li>0 caracteres (vazio) → inválido por {@code @NotBlank}
 *   <li>1 caractere → menor entrada válida
 *   <li>100 caracteres → maior entrada válida (limite superior aceito)
 *   <li>101 caracteres → primeira entrada inválida acima do limite
 * </ul>
 *
 * <p>Testamos a aplicação pela borda HTTP (MockMvc), sem conhecer a implementação interna — apenas
 * o contrato documentado em {@code docs/api.md}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@DisplayName("Caixa Preta — Valor-Limite na validação de title")
class TaskValidationBoundaryTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private String corpoComTitle(String title) throws Exception {
    return objectMapper.writeValueAsString(
        new CreateTaskRequest(title, "descricao valida", TaskStatus.PENDING, Priority.MEDIUM));
  }

  // ---------- Fronteira inferior ----------

  @Test
  @DisplayName("title vazio (0 chars) deve retornar 400 VALIDATION_ERROR")
  void titleVazio_deveRetornar400() throws Exception {
    mockMvc
        .perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(corpoComTitle("")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("title em branco (só espaços) deve retornar 400 — @NotBlank rejeita")
  void titleEmBranco_deveRetornar400() throws Exception {
    mockMvc
        .perform(
            post("/tasks").contentType(MediaType.APPLICATION_JSON).content(corpoComTitle("   ")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("title com 1 char (menor válido) deve retornar 200")
  void titleUmChar_deveRetornar200() throws Exception {
    mockMvc
        .perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(corpoComTitle("A")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("A"));
  }

  // ---------- Fronteira superior ----------

  @Test
  @DisplayName("title com exatamente 100 chars (limite aceito) deve retornar 200")
  void titleCem_deveRetornar200() throws Exception {
    String cemChars = "a".repeat(100);
    mockMvc
        .perform(
            post("/tasks").contentType(MediaType.APPLICATION_JSON).content(corpoComTitle(cemChars)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value(cemChars));
  }

  @Test
  @DisplayName("title com 101 chars (primeiro inválido acima do limite) deve retornar 400")
  void titleCentoEUm_deveRetornar400() throws Exception {
    String centoEUmChars = "a".repeat(101);
    mockMvc
        .perform(
            post("/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpoComTitle(centoEUmChars)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  // ---------- Partição de equivalência: campos obrigatórios ausentes ----------

  @Test
  @DisplayName("description ausente no create deve retornar 400 — @NotBlank obrigatório")
  void descriptionAusente_deveRetornar400() throws Exception {
    String json =
        """
        { "title": "valida", "status": "PENDING", "priority": "HIGH" }
        """;
    mockMvc
        .perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  @Test
  @DisplayName("status ausente no create deve retornar 400 — @NotNull obrigatório")
  void statusAusente_deveRetornar400() throws Exception {
    String json =
        """
        { "title": "valida", "description": "valida", "priority": "HIGH" }
        """;
    mockMvc
        .perform(post("/tasks").contentType(MediaType.APPLICATION_JSON).content(json))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }
}
