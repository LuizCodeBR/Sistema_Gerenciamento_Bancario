# Sistema de Gerenciamento Bancário

API de estudo em Java e Spring Boot: cadastro e abertura de conta, login JWT, autorização por titular e transferências PIX/TED simuladas com controle de concorrência e idempotência.

## Testar rapidamente após baixar

### Requisitos

- JDK 17 ou 21, com JAVA_HOME apontando para ele. Recomendado: JDK 21.
- Internet no primeiro uso para baixar Maven e dependências.
- No Linux/macOS, Bash e as ferramentas usuais do Maven Wrapper: curl ou wget e unzip.

Não é necessário instalar Maven, MySQL ou extensões do VS Code para o modo de demonstração. O Maven Wrapper fixa o Maven 3.9.16 e verifica o SHA-256 da distribuição. Os scripts abaixo guardam Maven, dependências e arquivos temporários na pasta .cache do próprio projeto.

Baixe o ZIP do repositório ou clone:

```sh
git clone https://github.com/LuizCodeBR/Sistema_Gerenciamento_Bancario.git
cd Sistema_Gerenciamento_Bancario
```

### Windows / PowerShell

Na pasta do projeto:

```powershell
.\scripts\verificar.cmd
.\scripts\iniciar-demo.cmd
```

O primeiro comando executa os testes e gera o JAR. O segundo mantém o servidor em execução. Em outro terminal:

```powershell
.\scripts\demo.ps1
```

O roteiro faz login, transfere R$ 10, repete o pedido, confere os saldos e verifica a recusa de acesso à conta de outro titular. A saída não imprime os tokens. O script PowerShell é opcional; também é possível usar as chamadas HTTP documentadas abaixo.

### Linux / macOS

```sh
bash scripts/verificar.sh
bash scripts/iniciar-demo.sh
```

As mesmas rotas podem ser chamadas com curl, Postman ou outro cliente HTTP. Os arquivos também podem ser executados com chmod +x, mas os comandos com bash não exigem isso.

### Executar o JAR já compilado

```sh
java -jar target/banco-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo
```

A API fica em http://127.0.0.1:8080. Não há interface gráfica nesta versão. Para mudar a porta, acrescente --server.port=8081 e use a nova URL nos exemplos.

## Dados da demonstração

| Cliente fictício | E-mail | Senha de exemplo | Saldo inicial |
| --- | --- | --- | --- |
| Alice | alice@demo.local | DemoAlice123! | R$ 1.000,00 |
| Bruno | bruno@demo.local | DemoBruno123! | R$ 500,00 |

Esses dados existem somente no perfil demo. Os números das contas são gerados ao iniciar; faça login e use GET /api/v1/contas para descobri-los. O banco H2 é em memória: reiniciar a demonstração descarta os dados e restaura os exemplos. A chave JWT é gerada com SecureRandom em memória a cada início se não houver chave configurada; não há chave de demonstração fixa no código.

O servidor de demonstração vincula-se a 127.0.0.1. O saldo fictício é criado pelo inicializador desse perfil; não existe endpoint público para depositar valores.

## Rotas

| Método e caminho | Acesso | Finalidade |
| --- | --- | --- |
| POST /api/v1/clientes | Público | Cadastrar e abrir conta com saldo zero |
| POST /api/v1/auth/login | Público | Autenticar e emitir JWT |
| GET /api/v1/contas | Titular autenticado | Listar somente suas contas |
| GET /api/v1/contas/{numero}/saldo | Titular autenticado | Consultar sua conta |
| POST /api/v1/transferencia | Titular da conta de origem | Transferir entre contas ativas |

### Login

```sh
curl -X POST http://127.0.0.1:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"email":"alice@demo.local","senha":"DemoAlice123!"}'
```

A resposta contém token, tipo (Bearer) e expiraEm (UTC). Faça também o login de Bruno para obter seu número de conta.

```sh
curl http://127.0.0.1:8080/api/v1/contas -H 'Authorization: Bearer TOKEN_DA_ALICE'
```

Substitua TOKEN_DA_ALICE pelo token retornado. Os exemplos curl usam aspas do Bash; no Windows, use scripts/demo.ps1 ou Invoke-RestMethod.

### Transferência

POST /api/v1/transferencia com Authorization: Bearer TOKEN_DA_ALICE e Content-Type: application/json:

```json
{
  "contaOrigem": "NUMERO_DA_ALICE",
  "contaDestino": "NUMERO_DO_BRUNO",
  "valor": 10.00,
  "chaveIdempotencia": "pedido-exemplo-1",
  "tipoOperacao": "PIX"
}
```

Substitua os números pelos obtidos na listagem. O tipo também pode ser TED. A resposta HTTP 200 é um comprovante contendo id, origem, destino, valor, tipo, dataHora e a chave enviada. O comprovante não inclui saldo atual, para que a repetição continue retornando a mesma resposta mesmo após outras operações.

Repita exatamente o mesmo corpo e confira que o comprovante é o mesmo e o saldo não sofre novo débito. Reutilizar a chave com outro valor, destino ou tipo retorna 409. A chave aceita até 64 caracteres: letras, números, ponto, hífen, sublinhado e dois-pontos. Valores equivalentes como 10.0 e 10.00 são considerados iguais.

### Cadastro próprio

```json
{
  "cpf": "123.456.789-09",
  "nomeCompleto": "Cliente de Teste",
  "email": "cliente@exemplo.test",
  "senha": "SenhaDeTeste123!",
  "tipoConta": "CORRENTE"
}
```

O cadastro retorna HTTP 201 e abre uma conta com saldo 0.00, agência 0001, número gerado e status ATIVA. CPF/e-mail não podem ser duplicados. No demo, o CPF do exemplo já pertence à Alice; para criar outro cliente, use outro CPF fictício com dígitos verificadores válidos.

CPF é normalizado e validado matematicamente; não há consulta de identidade ou situação cadastral. Nome aceita 3 a 150 caracteres; e-mail é normalizado em minúsculas; senha exige no mínimo 8 caracteres e no máximo 72 bytes UTF-8 e é armazenada com BCrypt. Senha/hash não aparecem nas respostas. Cliente e conta são criados na mesma transação.

## Como a transferência mantém a consistência

1. O UUID do titular vem do JWT validado, nunca do corpo enviado.
2. A conta de origem precisa pertencer ao titular. Não é possível debitar a conta de outra pessoa.
3. As duas contas são bloqueadas no banco em uma ordem determinística de UUID. Isso evita a inversão de ordem entre A->B e B->A.
4. As consultas iniciais obtêm apenas IDs, sem carregar um saldo que poderia ficar desatualizado antes do bloqueio.
5. A transação usa READ_COMMITTED. Depois de esperar um bloqueio, a consulta de idempotência enxerga um comprovante confirmado por outra chamada.
6. Débito, crédito e comprovante são confirmados juntos. Uma falha desfaz todas as alterações.
7. O valor deve ser positivo, ter no máximo duas casas decimais e caber na precisão do banco. As duas contas devem estar ativas; não há cheque especial nem transferência para a própria conta de origem.

A chave de idempotência é vinculada à conta de origem. Um SHA-256 de UUID da origem + chave compõe a chave interna única de 64 caracteres na tabela de transações. Isso permite que contas diferentes usem a mesma chave externa, sem mudar o formato do campo existente. O conteúdo do pedido é conferido na repetição. O comprovante só existe se a transação inteira tiver sido confirmada.

Não há implementação de Pix real nem comunicação com bancos externos. PIX e TED são tipos de operações simuladas.

## Autenticação e autorização

JWT usa HS256, UUID do titular, emissor, destinatário, emissão, expiração e identificador de token. Assinatura, algoritmo e campos obrigatórios são validados. CPF, e-mail e senha não são incluídos. A assinatura não criptografa o conteúdo do JWT.

Cada requisição usa o cabeçalho Bearer. Não há login por cookie ou sessão HTTP. Cliente excluído deixa de autenticar. Conta alheia e inexistente retornam o mesmo 404. Login inválido não distingue e-mail inexistente de senha incorreta.

As exceções de CSRF abrangem somente as três rotas POST JSON que não autenticam por cookie. As demais rotas mantêm a proteção. Não há renovação ou revogação individual de token nesta etapa; o prazo padrão é uma hora.

| HTTP | Situação |
| --- | --- |
| 400 | Entrada inválida, JSON inválido ou operação que não é PIX/TED |
| 401 | Autenticação ausente, credenciais ou token inválidos/expirados |
| 403 | Cliente autenticado acessando rota não liberada |
| 404 | Conta não encontrada no acesso permitido |
| 409 | Cadastro duplicado ou chave de idempotência com outro conteúdo |
| 422 | Saldo insuficiente, conta inativa, origem igual ao destino ou limite de saldo |
| 503 | Falha temporária de bloqueio; repetir com a mesma chave após o intervalo indicado |

## Testes

Os comandos scripts/verificar.cmd e bash scripts/verificar.sh executam clean verify pelo wrapper. Também é possível usar .\mvnw.cmd verify ou bash mvnw verify diretamente; nesse caso, os caches seguem a configuração padrão do Maven.

A suíte padrão usa H2 isolado e cobre cadastro, JWT, autorização, valores inválidos, rollback depois de gravar os saldos, repetição de pedidos, oito chamadas simultâneas, gasto concorrente do mesmo saldo, transferências opostas e pedidos simultâneos com uma chave e conteúdos diferentes. Os testes de concorrência utilizam threads e transações reais, sem substituir o banco por mocks.

### Testes específicos de MySQL

São opcionais e usam exclusivamente o banco descartável banco_portfolio_it em 127.0.0.1:3306. A suíte cria/remove tabelas nesse banco de testes. Não use esse schema para guardar dados necessários.

Prepare o banco e um usuário com permissões apenas nele, configure MYSQL_TEST_USER e MYSQL_TEST_PASS e execute:

```powershell
.\scripts\verificar.cmd -Pmysql-it
```

```sh
MYSQL_TEST_USER=banco_test MYSQL_TEST_PASS='senha-do-usuario-de-testes' bash scripts/verificar.sh -Pmysql-it
```

A mesma bateria de transferências e concorrência é herdada por TransferenciaMySqlIT. O workflow .github/workflows/ci.yml prepara um MySQL isolado para isso e também verifica Java 17/21 e a demonstração Docker. O workflow só executa depois de publicado no GitHub; sua presença não significa que já rodou.

## Docker opcional

Requer Docker com Compose. Não é necessário instalar Java no host para esta opção.

Demonstração descartável:

```sh
docker compose up --build
```

O serviço é publicado somente em 127.0.0.1:8080. O perfil demo gera os clientes fictícios e a chave JWT em memória. Pare com Ctrl+C e docker compose down.

### Aplicação com MySQL persistente

Copie .env.example para .env e preencha DB_PASS, MYSQL_ROOT_PASSWORD e JWT_SECRET com valores próprios. Depois:

```sh
docker compose -f compose.mysql.yml up --build
```

O MySQL não é publicado na rede do host. Os dados são mantidos em um volume Docker. Não há dados ou senhas de demonstração nesse modo.

## Executar com um MySQL já instalado

A aplicação sem o perfil demo usa MySQL e exige JWT_SECRET. Configure DB_URL, DB_USER e DB_PASS. O padrão da URL é jdbc:mysql://localhost:3306/banco?useSSL=false&serverTimezone=UTC.

Gere a chave JWT na sessão atual, sem imprimi-la nem salvá-la no projeto:

```powershell
$bankKeyBytes = New-Object byte[] 32
$bankRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bankRng.GetBytes($bankKeyBytes)
$bankRng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($bankKeyBytes)
.\mvnw.cmd spring-boot:run
```

A chave deve representar pelo menos 32 bytes aleatórios em Base64. JWT_EXPIRATION_MS aceita de 1000 a 86400000 ms, com padrão 3600000. Gerar outra chave invalida tokens anteriores. Não salve a chave no Git. A chave pública de fixture em src/test/resources é usada somente em testes.

## Escopo e validação

O projeto é destinado a estudo e portfólio. O esquema ainda usa ddl-auto=update no modo MySQL; migrações versionadas, histórico paginado, renovação/revogação de JWT e modernização de dependências continuam como evoluções.

Validação local em 10/11/2026: 124 testes aprovados com Java 21; uma cópia extraída do ZIP, sem dependências prévias do projeto, também compilou e passou na suíte. O JAR dessa cópia foi iniciado e a API real confirmou Alice com R$ 990, Bruno com R$ 510, repetição sem novo débito e recusa de conta alheia. Os arquivos Docker/CI e a suíte MySQL estão preparados; se não houver Docker ou credenciais de um banco de testes, essas validações não devem ser apresentadas como executadas.

## Licença

O README original declara licença MIT; o repositório ainda não inclui um arquivo LICENSE.
