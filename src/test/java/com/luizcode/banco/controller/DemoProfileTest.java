package com.luizcode.banco.controller;

import com.fasterxml.jackson.databind.*;
import com.luizcode.banco.security.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class DemoProfileTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired JwtProperties properties;

    @Test
    void demonstraCadastroInicialLoginTransferenciaERepeticaoSemMysql() throws Exception {
        assertThat(properties.secret()).isNotBlank();
        String alice = login("alice@demo.local", "DemoAlice123!");
        String bruno = login("bruno@demo.local", "DemoBruno123!");
        JsonNode a = contas(alice).get(0);
        JsonNode b = contas(bruno).get(0);
        assertThat(a.path("saldo").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(b.path("saldo").decimalValue()).isEqualByComparingTo("500.00");
        String corpo = json.writeValueAsString(Map.of("contaOrigem", a.path("numero").asText(),
                "contaDestino", b.path("numero").asText(), "valor", 10, "chaveIdempotencia", "demo-1", "tipoOperacao", "PIX"));
        String primeira = http.perform(post("/api/v1/transferencia").header("Authorization", "Bearer " + alice)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String repetida = http.perform(post("/api/v1/transferencia").header("Authorization", "Bearer " + alice)
                .contentType(MediaType.APPLICATION_JSON).content(corpo)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(repetida)).isEqualTo(json.readTree(primeira));
        assertThat(contas(alice).get(0).path("saldo").decimalValue()).isEqualByComparingTo("990.00");
        assertThat(contas(bruno).get(0).path("saldo").decimalValue()).isEqualByComparingTo("510.00");
        http.perform(get("/api/v1/contas/{numero}/saldo", b.path("numero").asText())
                .header("Authorization", "Bearer " + alice)).andExpect(status().isNotFound());
    }
    private String login(String email, String senha) throws Exception {
        return json.readTree(http.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "senha", senha))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("token").asText();
    }
    private JsonNode contas(String token) throws Exception {
        return json.readTree(http.perform(get("/api/v1/contas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
