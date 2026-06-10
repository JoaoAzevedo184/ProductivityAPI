package com.github.joaovictor.productivity_api.boundary;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Teste de CAIXA PRETA — Particionamento de equivalência em path params do tipo enum.
 *
 * <p>Os endpoints {@code GET /tasks/status/{status}} e {@code GET /tasks/priority/{priority}}
 * recebem enums diretamente no path. A classe de equivalência "valor inválido" deve ser tratada
 * pelo {@code GlobalExceptionHandler} como 400 {@code INVALID_PARAMETER}, com mensagem listando os
 * valores aceitos — conforme {@code docs/api.md}.
 *
 * <p>Comportamento documentado mas anteriormente sem cobertura automatizada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@DisplayName("Caixa Preta — Enums inválidos em path param")
class TaskEnumPathParamTest {

  @Autowired private MockMvc mockMvc;

  // ---------- status ----------

  @Test
  @DisplayName("status válido (PENDING) deve retornar 200")
  void statusValido_deveRetornar200() throws Exception {
    mockMvc.perform(get("/tasks/status/PENDING")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("status inválido (INVALIDO) deve retornar 400 INVALID_PARAMETER")
  void statusInvalido_deveRetornar400() throws Exception {
    mockMvc
        .perform(get("/tasks/status/INVALIDO"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("PENDING")));
  }

  @Test
  @DisplayName("status com caixa errada (pending minúsculo) deve retornar 400 — case-sensitive")
  void statusCaixaErrada_deveRetornar400() throws Exception {
    mockMvc.perform(get("/tasks/status/pending")).andExpect(status().isBadRequest());
  }

  // ---------- priority ----------

  @Test
  @DisplayName("priority válida (HIGH) deve retornar 200")
  void priorityValida_deveRetornar200() throws Exception {
    mockMvc.perform(get("/tasks/priority/HIGH")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("priority inválida (URGENTE) deve retornar 400 INVALID_PARAMETER")
  void priorityInvalida_deveRetornar400() throws Exception {
    mockMvc
        .perform(get("/tasks/priority/URGENTE"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("HIGH")));
  }
}
