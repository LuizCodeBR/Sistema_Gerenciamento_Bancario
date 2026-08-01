# Sistema Gerenciamento Bancário

Projeto de simulação de um sistema bancário utilizando Java, Spring Boot, JPA e MySQL.

## Funcionalidades

- Cadastro de clientes com validação de CPF e hash de senha (BCrypt)
- Autenticação via JWT
- Criação de contas correntes ou poupança
- Consulta de saldo (requer autenticação)
- Transferências entre contas com controle de concorrência (SELECT FOR UPDATE) e idempotência
- Restrição de acesso às operações financeiras via token JWT

## Tecnologias

- Java 17
- Spring Boot 3.2.0
- Spring Data JPA
- MySQL Connector/J
- BCrypt for password hashing
- JJWT for JSON Web Tokens
- Jakarta Validation
- Maven

## Pré-requisitos

- JDK 17+
- MySQL 8+ (ou Docker)
- Maven 3.8+

## Configuração

1. Clone o repositório:
   ```bash
   git clone https://github.com/LuizCodeBR/Sistema_Gerenciamento_Bancario.git
   cd Sistema_Gerenciamento_Bancario
   ```

2. Crie um banco de dados MySQL chamado `banco` (ou ajuste o nome em `src/main/resources/application.yml`).

3. Configure as variáveis de ambiente (opcional):
   - `DB_USER` (padrão: root)
   - `DB_PASS` (padrão: vazio)
   - `JWT_SECRET` (padrão: supersecretkeychangeme)
   - `JWT_EXPIRATION_MS` (padrão: 3600000 - 1 hora)

   Exemplo usando Docker:
   ```bash
   docker run --name mysql-banco -e MYSQL_ROOT_PASSWORD=senha -e MYSQL_DATABASE=banco -p 3306:3306 -d mysql:8
   ```

4. Execute a aplicação:
   ```bash
   mvn spring-boot:run
   ```

   A API estará disponível em http://localhost:8080

## Endpoints

### Autenticação
- `POST /api/v1/auth/login`
  - Corpo: `{ "email": "...", "senha": "..." }`
  - Retorna: `{ "token": "..." }`

### Clientes
- `POST /api/v1/clientes`
  - Corpo:
    ```json
    {
      "cpf": "123.456.789-09",
      "nomeCompleto": "Nome Completo",
      "email": "email@exemplo.com",
      "senha": "senha123",
      "tipoConta": "CORRENTE"
    }
    ```
  - Retorna dados do cliente criado (incluindo data de criação)

### Contas
- `GET /api/v1/contas/{numero}/saldo`
  - Header: `Authorization: Bearer <token>`
  - Retorna saldo, agência, tipo e status da conta

### Transferências
- `POST /api/v1/transferencia`
  - Header: `Authorization: Bearer <token>`
  - Corpo:
    ```json
    {
      "contaDestino": "12345",
      "valor": 100.00,
      "chaveIdempotencia": "unique-id-123",
      "tipoOperacao": "PIX"
    }
    ```
  - Retorna 200 OK se a transferência for realizada com sucesso

## Testes

Para executar os testes unitários:
```bash
mvn test
```

## Licença

Este projeto está sob a licença MIT.