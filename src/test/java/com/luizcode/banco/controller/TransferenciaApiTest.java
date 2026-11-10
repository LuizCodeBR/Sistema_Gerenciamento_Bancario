package com.luizcode.banco.controller;

import com.fasterxml.jackson.databind.*;
import com.luizcode.banco.model.Conta;
import com.luizcode.banco.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:transferencias-api;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferenciaApiTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired ClienteRepository clientes;
    @Autowired ContaRepository contas;
    @Autowired TransacaoRepository transacoes;
    String numeroA;
    String numeroB;
    String tokenA;
    String tokenB;

    @BeforeEach
    void preparar() throws Exception {
        transacoes.deleteAllInBatch(); contas.deleteAllInBatch(); clientes.deleteAllInBatch();
        numeroA = cadastrar("12345678909", "a@exemplo.test");
        numeroB = cadastrar("52998224725", "b@exemplo.test");
        Conta origem = contas.findByNumero(numeroA).orElseThrow();
        origem.setSaldo(new BigDecimal("100.00")); contas.saveAndFlush(origem);
        tokenA = login("a@exemplo.test"); tokenB = login("b@exemplo.test");
    }

    @Test
    void transferenciaRepetidaDevolveMesmoJsonSemDebitarNovamente() throws Exception {
        String primeira = transferir("10.50", "pedido-1", tokenA).andExpect(status().isOk())
                .andExpect(jsonPath("contaOrigem").value(numeroA)).andReturn().getResponse().getContentAsString();
        String repetida = transferir("10.50", "pedido-1", tokenA).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(repetida)).isEqualTo(json.readTree(primeira));
        assertThat(contas.findByNumero(numeroA).orElseThrow().getSaldo()).isEqualByComparingTo("89.50");
        assertThat(transacoes.count()).isEqualTo(1);
    }

    @Test
    void chaveComOutroValorRetorna409() throws Exception {
        transferir("1.00", "chave", tokenA).andExpect(status().isOk());
        transferir("2.00", "chave", tokenA).andExpect(status().isConflict());
    }

    @Test
    void saldoInsuficienteRetorna422() throws Exception {
        transferir("100.01", "sem-saldo", tokenA).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void titularNaoDebitaContaDeOutraPessoa() throws Exception {
        transferir("1.00", "alheia", tokenB).andExpect(status().isNotFound());
        assertThat(transacoes.count()).isZero();
    }

    @Test
    void transferenciaSemAutenticacaoRetorna401() throws Exception {
        http.perform(post("/api/v1/transferencia").contentType(MediaType.APPLICATION_JSON)
                .content(corpo("1.00", "anonimo"))).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.001"})
    void valorInvalidoRetorna400(String valor) throws Exception {
        transferir(valor, "invalido", tokenA).andExpect(status().isBadRequest());
    }

    @Test
    void listagemRetornaSomenteContasDoTitular() throws Exception {
        http.perform(get("/api/v1/contas").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].numero").value(numeroA));
        http.perform(get("/api/v1/contas")).andExpect(status().isUnauthorized());
    }

    private ResultActions transferir(String valor, String chave, String token) throws Exception {
        return http.perform(post("/api/v1/transferencia").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(corpo(valor, chave)));
    }
    private String corpo(String valor, String chave) throws Exception {
        return json.writeValueAsString(Map.of("contaOrigem", numeroA, "contaDestino", numeroB,
                "valor", new BigDecimal(valor), "chaveIdempotencia", chave, "tipoOperacao", "PIX"));
    }
    private String cadastrar(String cpf, String email) throws Exception {
        String resposta = http.perform(post("/api/v1/clientes").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("cpf", cpf, "nomeCompleto", "Cliente Teste", "email", email,
                        "senha", "SenhaTeste123!", "tipoConta", "CORRENTE"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).path("conta").path("numero").asText();
    }
    private String login(String email) throws Exception {
        String resposta = http.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "senha", "SenhaTeste123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).path("token").asText();
    }
}
