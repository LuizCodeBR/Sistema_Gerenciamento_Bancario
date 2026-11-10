package com.luizcode.banco.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luizcode.banco.dto.CadastroClienteRequest;
import com.luizcode.banco.model.Cliente;
import com.luizcode.banco.model.Conta;
import com.luizcode.banco.model.enums.StatusConta;
import com.luizcode.banco.model.enums.TipoConta;
import com.luizcode.banco.repository.ClienteRepository;
import com.luizcode.banco.repository.ContaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cadastro-api;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CadastroClienteApiTest {
    @Autowired
    private MockMvc http;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @SpyBean
    private ClienteRepository clientes;
    @SpyBean
    private ContaRepository contas;

    @BeforeEach
    void limparDadosDeTeste() {
        contas.deleteAllInBatch();
        clientes.deleteAllInBatch();
    }

    @ParameterizedTest
    @EnumSource(TipoConta.class)
    void criaClienteEContaComSenhaProtegida(TipoConta tipo) throws Exception {
        Map<String, Object> entrada = cadastroValido();
        entrada.put("tipoConta", tipo.name());
        String corpo = cadastrar(entrada)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("id").isNotEmpty())
                .andExpect(jsonPath("nomeCompleto").value("Cliente de Teste"))
                .andExpect(jsonPath("email").value("cliente@exemplo.test"))
                .andExpect(jsonPath("dataCriacao").isNotEmpty())
                .andExpect(jsonPath("conta.agencia").value("0001"))
                .andExpect(jsonPath("conta.tipo").value(tipo.name()))
                .andExpect(jsonPath("conta.status").value("ATIVA"))
                .andExpect(jsonPath("senha").doesNotExist())
                .andExpect(jsonPath("senhaHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        JsonNode resposta = json.readTree(corpo);
        assertThat(new BigDecimal(resposta.path("conta").path("saldo").asText()))
                .isEqualByComparingTo("0.00");
        assertThat(clientes.count()).isEqualTo(1);
        assertThat(contas.count()).isEqualTo(1);
        Cliente cliente = clientes.findByCpf("12345678909").orElseThrow();
        assertThat(cliente.getEmail()).isEqualTo("cliente@exemplo.test");
        assertThat(cliente.getSenhaHash()).isNotEqualTo("SenhaDeTeste123!");
        assertThat(passwordEncoder.matches("SenhaDeTeste123!", cliente.getSenhaHash())).isTrue();
        assertThat(corpo).doesNotContain("SenhaDeTeste123!", cliente.getSenhaHash());
        assertThat(cliente.toString()).doesNotContain(cliente.getSenhaHash());
        assertThat(json.writeValueAsString(cliente)).doesNotContain("senhaHash", cliente.getSenhaHash());
        Conta conta = contas.findAllByClienteId(cliente.getId()).get(0);
        assertThat(conta.getNumero()).matches("[0-9]{12}");
        assertThat(conta.getSaldo()).isEqualByComparingTo("0.00");
        assertThat(conta.getStatus()).isEqualTo(StatusConta.ATIVA);
    }

    @Test
    void aceitaCpfSemMascara() throws Exception {
        Map<String, Object> entrada = cadastroValido();
        entrada.put("cpf", "12345678909");
        cadastrar(entrada).andExpect(status().isCreated());
        assertThat(clientes.findByCpf("12345678909")).isPresent();
    }

    @ParameterizedTest
    @MethodSource("dadosInvalidos")
    void rejeitaCamposInvalidosSemGravar(String campo, Object valor) throws Exception {
        Map<String, Object> entrada = cadastroValido();
        entrada.put(campo, valor);
        cadastrar(entrada)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("status").value(400))
                .andExpect(jsonPath("campos." + campo).isString());
        assertThat(clientes.count()).isZero();
        assertThat(contas.count()).isZero();
    }

    static Stream<Arguments> dadosInvalidos() {
        return Stream.of(
                Arguments.of("cpf", null),
                Arguments.of("cpf", "11111111111"),
                Arguments.of("cpf", "12345678900"),
                Arguments.of("cpf", "123"),
                Arguments.of("cpf", "abcdefghijk"),
                Arguments.of("nomeCompleto", "   "),
                Arguments.of("nomeCompleto", "A"),
                Arguments.of("nomeCompleto", "x".repeat(151)),
                Arguments.of("email", null),
                Arguments.of("email", "nao-e-email"),
                Arguments.of("email", "   "),
                Arguments.of("senha", null),
                Arguments.of("senha", "1234567"),
                Arguments.of("senha", "        "),
                Arguments.of("senha", "x".repeat(73)),
                Arguments.of("senha", "á".repeat(37)),
                Arguments.of("tipoConta", null));
    }

    @Test
    void rejeitaCorpoSemCampos() throws Exception {
        cadastrar(Map.of()).andExpect(status().isBadRequest());
        assertThat(clientes.count()).isZero();
    }

    @Test
    void rejeitaTipoDeContaDesconhecido() throws Exception {
        Map<String, Object> entrada = cadastroValido();
        entrada.put("tipoConta", "INVESTIMENTO");
        cadastrar(entrada).andExpect(status().isBadRequest());
        assertThat(clientes.count()).isZero();
    }

    @Test
    void rejeitaJsonMalformadoSemExporConteudo() throws Exception {
        String resposta = http.perform(post("/api/v1/clientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"senha\":\"SegredoNaoDeveSerExposto\","))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(resposta).doesNotContain("SegredoNaoDeveSerExposto", "stackTrace");
    }

    @Test
    void rejeitaCpfDuplicadoMesmoComOutraFormatacao() throws Exception {
        cadastrar(cadastroValido()).andExpect(status().isCreated());
        Map<String, Object> repetido = cadastroValido();
        repetido.put("cpf", "12345678909");
        repetido.put("email", "outro@exemplo.test");
        cadastrar(repetido)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("mensagem").value("CPF já cadastrado."));
        assertThat(clientes.count()).isEqualTo(1);
        assertThat(contas.count()).isEqualTo(1);
    }

    @Test
    void rejeitaEmailDuplicadoIndependentementeDeMaiusculas() throws Exception {
        cadastrar(cadastroValido()).andExpect(status().isCreated());
        Map<String, Object> repetido = cadastroValido();
        repetido.put("cpf", "52998224725");
        repetido.put("email", "CLIENTE@EXEMPLO.TEST");
        cadastrar(repetido)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("mensagem").value("E-mail já cadastrado."));
        assertThat(clientes.count()).isEqualTo(1);
        assertThat(contas.count()).isEqualTo(1);
    }

    @Test
    void bancoProtegeUnicidadeMesmoQuandoPreConsultaNaoDetectaDuplicado() throws Exception {
        cadastrar(cadastroValido()).andExpect(status().isCreated());
        doReturn(false).when(clientes).existsByCpf(anyString());
        doReturn(false).when(clientes).existsByEmail(anyString());
        Map<String, Object> repetido = cadastroValido();
        repetido.put("email", "outro@exemplo.test");
        String resposta = cadastrar(repetido).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertThat(clientes.count()).isEqualTo(1);
        assertThat(contas.count()).isEqualTo(1);
        assertThat(resposta).doesNotContain("insert into", "12345678909", "ConstraintViolation");
    }

    @Test
    void falhaAoCriarContaDesfazCriacaoDoCliente() throws Exception {
        doThrow(new DataIntegrityViolationException("detalhe-interno-do-banco"))
                .when(contas).saveAndFlush(any(Conta.class));
        String resposta = cadastrar(cadastroValido())
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertThat(clientes.count()).isZero();
        assertThat(contas.count()).isZero();
        assertThat(resposta).doesNotContain("detalhe-interno-do-banco", "SenhaDeTeste123!");
    }

    @Test
    void camposExtrasNaoPermitemEscolherSaldoAgenciaOuStatus() throws Exception {
        Map<String, Object> entrada = cadastroValido();
        entrada.put("saldo", 1000000);
        entrada.put("agencia", "9999");
        entrada.put("status", "BLOQUEADA");
        entrada.put("senhaHash", "hash-escolhido-pelo-cliente");
        cadastrar(entrada).andExpect(status().isCreated());
        Conta conta = contas.findAll().get(0);
        assertThat(conta.getSaldo()).isEqualByComparingTo("0.00");
        assertThat(conta.getAgencia()).isEqualTo("0001");
        assertThat(conta.getStatus()).isEqualTo(StatusConta.ATIVA);
        assertThat(clientes.findByCpf("12345678909").orElseThrow().getSenhaHash()).isNotEqualTo("hash-escolhido-pelo-cliente");
    }

    @ParameterizedTest
    @ValueSource(strings = {"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
            "áááááááááááááááááááááááááááááááááááá"})
    void aceitaSenhaNoLimiteDe72Bytes(String senha) throws Exception {
        Map<String, Object> entrada = cadastroValido();
        entrada.put("senha", senha);
        cadastrar(entrada).andExpect(status().isCreated());
        Cliente cliente = clientes.findByCpf("12345678909").orElseThrow();
        assertThat(passwordEncoder.matches(senha, cliente.getSenhaHash())).isTrue();
    }

    @Test
    void dtoNaoIncluiSenhaNaSerializacaoOuNoToString() throws Exception {
        CadastroClienteRequest request = new CadastroClienteRequest("12345678909", "Cliente",
                "cliente@exemplo.test", "SenhaDeTeste123!", TipoConta.CORRENTE);
        assertThat(request.toString()).doesNotContain("SenhaDeTeste123!");
        assertThat(json.writeValueAsString(request)).doesNotContain("senha", "SenhaDeTeste123!");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/clientes", "/api/v1/contas/10001/saldo", "/api/v1/auth/login"})
    void outrasRotasPermanecemBloqueadas(String rota) throws Exception {
        http.perform(get(rota)).andExpect(status().isUnauthorized());
    }

    private ResultActions cadastrar(Map<String, Object> entrada) throws Exception {
        return http.perform(post("/api/v1/clientes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(entrada)));
    }

    private Map<String, Object> cadastroValido() {
        Map<String, Object> entrada = new LinkedHashMap<>();
        entrada.put("cpf", "123.456.789-09");
        entrada.put("nomeCompleto", "  Cliente de Teste  ");
        entrada.put("email", "  Cliente@Exemplo.Test  ");
        entrada.put("senha", "SenhaDeTeste123!");
        entrada.put("tipoConta", "CORRENTE");
        return entrada;
    }
}
