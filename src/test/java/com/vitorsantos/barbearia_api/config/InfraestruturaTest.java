package com.vitorsantos.barbearia_api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.vitorsantos.barbearia_api.IntegrationTestSupport;

/** Contexto sobe, migrations rodam, Swagger publica a API e o CORS funciona. */
class InfraestruturaTest extends IntegrationTestSupport {

  @Test
  void migrationsDoFlywayForamAplicadas() {
    List<String> versoes = jdbcTemplate.queryForList(
        "SELECT version FROM flyway_schema_history WHERE type = 'SQL' AND success = TRUE ORDER BY installed_rank",
        String.class);
    assertThat(versoes).containsExactly("1", "2", "3");
  }

  @Test
  void openApiDocumentaOsEndpointsPrincipais() throws Exception {
    mockMvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths", hasKey("/clientes")))
        .andExpect(jsonPath("$.paths", hasKey("/barbeiros/{id}/fila")))
        .andExpect(jsonPath("$.paths", hasKey("/barbeiros/{id}/fila/proximo")))
        .andExpect(jsonPath("$.paths", hasKey("/agendamentos/{id}/status")))
        .andExpect(jsonPath("$.paths", hasKey("/agendamentos/{id}/posicao")))
        .andExpect(jsonPath("$.paths", hasKey("/servicos")));
  }

  @Test
  void swaggerUiEstaDisponivel() throws Exception {
    mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
  }

  @Test
  void corsLiberaOFrontConfigurado() throws Exception {
    mockMvc.perform(options("/clientes")
        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
  }

  @Test
  void corsBloqueiaOrigemDesconhecida() throws Exception {
    mockMvc.perform(options("/clientes")
        .header(HttpHeaders.ORIGIN, "http://site-malicioso.com")
        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(status().isForbidden());
  }
}
