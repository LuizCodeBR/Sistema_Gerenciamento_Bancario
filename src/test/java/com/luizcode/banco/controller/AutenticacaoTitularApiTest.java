package com.luizcode.banco.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.luizcode.banco.model.Conta;
import com.luizcode.banco.model.enums.StatusConta;
import com.luizcode.banco.model.enums.TipoConta;
import com.luizcode.banco.repository.ClienteRepository;
import com.luizcode.banco.repository.ContaRepository;
import com.luizcode.banco.security.JwtProperties;
import com.luizcode.banco.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:jwt-api;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AutenticacaoTitularApiTest {
    @Autowired private MockMvc http;
    @Autowired private ObjectMapper json;
    @Autowired private ClienteRepository clientes;
    @Autowired private ContaRepository contas;
    @Autowired private JwtProperties properties;
    @Autowired private JwtService jwt;
    private UUID idA;
    private UUID idB;
    private String contaA;
    private String contaB;

    @BeforeEach
    void criarDoisTitularesPelaApiReal() throws Exception {
        contas.deleteAllInBatch();
        clientes.deleteAllInBatch();
        JsonNode a = cadastrar("12345678909", "cliente-a@exemplo.test");
        JsonNode b = cadastrar("52998224725", "cliente-b@exemplo.test");
        idA = UUID.fromString(a.path("id").asText());
        idB = UUID.fromString(b.path("id").asText());
        contaA = a.path("conta").path("numero").asText();
        contaB = b.path("conta").path("numero").asText();
    }

    @Test
    void loginValidoEmiteBearerDoTitularSemCriarSessao() throws Exception {
        MvcResult resultado = login("  CLIENTE-A@EXEMPLO.TEST  ", "SenhaLogin123!")
                .andExpect(status().isOk()).andExpect(jsonPath("tipo").value("Bearer"))
                .andExpect(jsonPath("expiraEm").isNotEmpty()).andReturn();
        JsonNode resposta = json.readTree(resultado.getResponse().getContentAsString());
        assertThat(jwt.validar(resposta.path("token").asText())).isEqualTo(idA);
        assertThat(Instant.parse(resposta.path("expiraEm").asText())).isAfter(Instant.now());
        assertThat(resultado.getRequest().getSession(false)).isNull();
        assertThat(resultado.getResponse().getContentAsString())
                .doesNotContain("SenhaLogin123!", "senhaHash", "12345678909");
    }

    @Test
    void emailInexistenteESenhaErradaRetornamMesmoErro() throws Exception {
        String senhaErrada = login("cliente-a@exemplo.test", "SenhaErrada!")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String emailInexistente = login("naoexiste@exemplo.test", "SenhaLogin123!")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(senhaErrada).path("mensagem"))
                .isEqualTo(json.readTree(emailInexistente).path("mensagem"));
        assertThat(senhaErrada + emailInexistente).doesNotContain("token", "SenhaErrada!", "senhaHash");
    }

    @Test
    void senhaQueExcedeLimiteEmBytesNaoAutentica() throws Exception {
        login("cliente-a@exemplo.test", "á".repeat(37)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginSemCamposRetornaErroDeValidacao() throws Exception {
        http.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("campos.email").isString());
    }

    @Test
    void titularConsultaSaldoDaPropriaConta() throws Exception {
        saldo(contaA, tokenA()).andExpect(status().isOk())
                .andExpect(jsonPath("numero").value(contaA)).andExpect(jsonPath("saldo").value(0.0))
                .andExpect(jsonPath("tipo").value("CORRENTE"))
                .andExpect(jsonPath("cpf").doesNotExist()).andExpect(jsonPath("senhaHash").doesNotExist());
    }

    @Test
    void segundoTitularTambemAcessaSomenteSuaConta() throws Exception {
        String tokenB = token("cliente-b@exemplo.test");
        saldo(contaB, tokenB).andExpect(status().isOk());
        saldo(contaA, tokenB).andExpect(status().isNotFound());
    }

    @Test
    void contaDeOutroTitularENumeroInexistenteRetornamMesmoErro() throws Exception {
        String token = tokenA();
        String alheia = saldo(contaB, token).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        String inexistente = saldo("000000000000", token).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(alheia).path("mensagem"))
                .isEqualTo(json.readTree(inexistente).path("mensagem"));
        assertThat(alheia).doesNotContain(contaB, "cliente-b", idB.toString());
    }

    @Test
    void parametroDeClienteNaoSubstituiIdentidadeDoToken() throws Exception {
        http.perform(get("/api/v1/contas/{numero}/saldo", contaB)
                        .param("clienteId", idB.toString()).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA()))
                .andExpect(status().isNotFound());
    }

    @Test
    void titularAcessaMaisDeUmaContaSua() throws Exception {
        contas.saveAndFlush(Conta.builder().cliente(clientes.findById(idA).orElseThrow())
                .agencia("0001").numero("900000000001").saldo(new BigDecimal("10.50"))
                .tipo(TipoConta.POUPANCA).status(StatusConta.ATIVA).build());
        saldo("900000000001", tokenA()).andExpect(status().isOk())
                .andExpect(jsonPath("saldo").value(10.5)).andExpect(jsonPath("tipo").value("POUPANCA"));
    }

    @Test
    void semTokenRetorna401EIndicacaoBearer() throws Exception {
        http.perform(get("/api/v1/contas/{numero}/saldo", contaA))
                .andExpect(status().isUnauthorized()).andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("status").value(401));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer nao-e-jwt", "Bearer ", "Basic abc", "Bearer eyJhbGciOiJub25lIn0.e30."})
    void rejeitaCabecalhosInvalidosETokenSemAssinatura(String header) throws Exception {
        http.perform(get("/api/v1/contas/{numero}/saldo", contaA).header(HttpHeaders.AUTHORIZATION, header))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adulterarTitularNoPayloadInvalidaAssinatura() throws Exception {
        String[] partes = tokenA().split("\\.");
        ObjectNode payload = (ObjectNode) json.readTree(Base64.getUrlDecoder().decode(partes[1]));
        payload.put("sub", idB.toString());
        String adulterado = partes[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.writeValueAsBytes(payload)) + "." + partes[2];
        saldo(contaB, adulterado).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpiradoNaoConsultaSaldo() throws Exception {
        String token = assinar(idA, Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600),
                properties.issuer(), properties.audience(), properties.secret());
        saldo(contaA, token).andExpect(status().isUnauthorized());
    }

    @Test
    void assinaturaDeOutraChaveNaoAutentica() throws Exception {
        String outraChave = Base64.getEncoder().encodeToString(
                "outra-chave-de-teste-com-32-bytes!".getBytes(StandardCharsets.UTF_8));
        String token = assinar(idA, Instant.now().minusSeconds(1), Instant.now().plusSeconds(3600),
                properties.issuer(), properties.audience(), outraChave);
        saldo(contaA, token).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"emissor", "destinatario"})
    void emissorEDestinatarioPrecisamCorresponder(String campo) throws Exception {
        String token = assinar(idA, Instant.now().minusSeconds(1), Instant.now().plusSeconds(3600),
                campo.equals("emissor") ? "outro" : properties.issuer(),
                campo.equals("destinatario") ? "outra-api" : properties.audience(), properties.secret());
        saldo(contaA, token).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeClienteExcluidoNaoAutentica() throws Exception {
        String token = tokenA();
        contas.deleteAll(contas.findAllByClienteId(idA));
        clientes.deleteById(idA);
        saldo(contaA, token).andExpect(status().isUnauthorized());
    }

    @Test
    void naoReaproveitaAutenticacaoDaRequisicaoAnterior() throws Exception {
        saldo(contaA, tokenA()).andExpect(status().isOk());
        http.perform(get("/api/v1/contas/{numero}/saldo", contaA)).andExpect(status().isUnauthorized());
    }

    @Test
    void cookieNaoSubstituiCabecalhoBearer() throws Exception {
        http.perform(get("/api/v1/contas/{numero}/saldo", contaA).cookie(new Cookie("token", tokenA())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejeitaDoisCabecalhosDeAutorizacao() throws Exception {
        String token = tokenA();
        http.perform(get("/api/v1/contas/{numero}/saldo", contaA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void permiteNovoLoginMesmoComBearerAntigoInvalido() throws Exception {
        http.perform(post("/api/v1/auth/login").header(HttpHeaders.AUTHORIZATION, "Bearer invalido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "cliente-a@exemplo.test", "senha", "SenhaLogin123!"))))
                .andExpect(status().isOk());
    }

    @Test
    void tokenValidoNaoLiberaRotasNaoImplementadas() throws Exception {
        http.perform(get("/api/v1/clientes").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA()))
                .andExpect(status().isForbidden());
    }

    private String assinar(UUID id, Instant inicio, Instant fim, String emissor, String destinatario, String secret) {
        return Jwts.builder().setSubject(id.toString()).setIssuer(emissor).setAudience(destinatario)
                .setIssuedAt(Date.from(inicio)).setExpiration(Date.from(fim))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret)), SignatureAlgorithm.HS256).compact();
    }

    private JsonNode cadastrar(String cpf, String email) throws Exception {
        String corpo = http.perform(post("/api/v1/clientes").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("cpf", cpf, "nomeCompleto", "Cliente de Teste",
                                "email", email, "senha", "SenhaLogin123!", "tipoConta", "CORRENTE"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(corpo);
    }

    private ResultActions login(String email, String senha) throws Exception {
        return http.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "senha", senha))));
    }

    private String tokenA() throws Exception {
        return token("cliente-a@exemplo.test");
    }

    private String token(String email) throws Exception {
        return json.readTree(login(email, "SenhaLogin123!").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("token").asText();
    }

    private ResultActions saldo(String numero, String token) throws Exception {
        return http.perform(get("/api/v1/contas/{numero}/saldo", numero)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}
