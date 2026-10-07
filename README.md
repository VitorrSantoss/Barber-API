# 💈 Barbearia - API

API REST para gerenciar uma barbearia: clientes, barbeiros, serviços, agendamentos e uma **fila de atendimento independente para cada barbeiro**, com atualização em tempo real via WebSocket.

> 🚧 **Em desenvolvimento.** Só o login do balcão do barbeiro existe; o restante da API ainda é aberto (ver [Limitações](#-limitações-conhecidas)). Não use em produção como está.

O front-end que consome esta API fica no repositório `Barbearia - Front` (React + Vite).

## Sumário

- [Visão geral](#-visão-geral)
- [Tecnologias](#️-tecnologias)
- [Como rodar](#️-como-rodar)
- [Configuração](#️-configuração)
- [Arquitetura](#️-arquitetura)
- [Modelo de dados](#️-modelo-de-dados)
- [Regras de negócio](#-regras-de-negócio)
- [Endpoints](#-endpoints)
- [Tempo real (WebSocket)](#-tempo-real-websocket)
- [Formato de erros](#-formato-de-erros)
- [Testes](#-testes)
- [Limitações conhecidas](#-limitações-conhecidas)

---

## 🎯 Visão geral

O fluxo principal que a API suporta:

1. O cliente se cadastra (o telefone é a chave de identificação).
2. Escolhe um **serviço** e um **barbeiro** e entra na **fila daquele barbeiro**, ou marca um horário futuro.
3. Acompanha a posição na fila (1 = próximo).
4. O barbeiro chama o próximo cliente, atende e finaliza.
5. A fila se reorganiza sozinha e todo mundo que está acompanhando é avisado em tempo real.

A fila de um barbeiro nunca interfere na de outro.

---

## 🛠️ Tecnologias

| Área              | Tecnologia                                               |
| ----------------- | -------------------------------------------------------- |
| Linguagem / build | Java 21, Maven (wrapper incluso)                         |
| Framework         | Spring Boot 4 (Web MVC, Validation, Security, WebSocket) |
| Persistência      | Spring Data JPA / Hibernate, MySQL 8                     |
| Migrations        | Flyway                                                   |
| Tempo real        | WebSocket com STOMP + SockJS                             |
| Documentação      | springdoc-openapi (Swagger UI)                           |
| Outros            | Lombok, BCrypt (spring-security-crypto)                  |
| Testes            | JUnit 5, MockMvc, AssertJ, H2 em modo MySQL              |

---

## ▶️ Como rodar

**Pré-requisitos:** JDK 21 e um MySQL 8 rodando. Não precisa instalar Maven (use `./mvnw`).

```bash
# 1. Crie o banco (uma vez)
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS barbearia CHARACTER SET utf8mb4;"

# 2. Configure as credenciais
cp .env.example .env        # e edite o .env (DB_PASSWORD, etc.)

# 3. Suba a API (Windows: mvnw.cmd)
./mvnw spring-boot:run
```

Ao subir, o Flyway cria/atualiza as tabelas sozinho.

| O quê                        | Onde                                                                        |
| ---------------------------- | --------------------------------------------------------------------------- |
| API                          | http://localhost:8080                                                       |
| Swagger UI                   | http://localhost:8080/swagger-ui/index.html (abre sozinho no profile `dev`) |
| OpenAPI (JSON)               | http://localhost:8080/v3/api-docs                                           |
| WebSocket                    | `ws://localhost:8080/ws` (SockJS)                                           |
| Página de teste do WebSocket | http://localhost:8080/teste-websocket.html                                  |

---

## ⚙️ Configuração

Segredos **nunca** ficam no código. A API lê um arquivo `.env` na raiz (ignorado pelo git) ou variáveis de ambiente do sistema.

| Variável                    | Padrão                                  | Descrição                                                                 |
| --------------------------- | --------------------------------------- | ------------------------------------------------------------------------- |
| `DB_URL`                    | `jdbc:mysql://localhost:3306/barbearia` | URL JDBC do MySQL                                                         |
| `DB_USERNAME`               | `root`                                  | Usuário do banco                                                          |
| `DB_PASSWORD`               | _(vazio)_                               | Senha do banco                                                            |
| `CORS_ALLOWED_ORIGINS`      | `http://localhost:5173`                 | Origens do front liberadas (HTTP e WebSocket), separadas por vírgula      |
| `BARBEIROS_SENHAS_INICIAIS` | _(vazio)_                               | Senhas iniciais dos barbeiros, no formato `Nome:123456,Outro Nome:654321` |

Propriedades da aplicação (`application.properties`), ajustáveis também por variável de ambiente no padrão do Spring:

| Propriedade                                 | Padrão                            | Descrição                                               |
| ------------------------------------------- | --------------------------------- | ------------------------------------------------------- |
| `app.clientes.dias-inatividade`             | `90`                              | Dias sem atividade para um cliente ser removido         |
| `app.clientes.limpeza-cron`                 | `0 0 3 * * *`                     | Quando o job de limpeza roda (todo dia às 3h)           |
| `app.agendamentos.auto-status-intervalo-ms` | `60000`                           | Intervalo do job que move horários marcados para a fila |
| `app.auth.max-tentativas`                   | `5`                               | Senhas erradas seguidas antes de bloquear o login       |
| `app.auth.bloqueio-segundos`                | `30`                              | Duração do bloqueio                                     |
| `app.swagger.auto-open`                     | `false` (`true` no profile `dev`) | Abre o Swagger no navegador ao subir (Windows)          |

O profile `dev` vem ativo por padrão (liga o log de SQL e a abertura do Swagger).

---

## 🏛️ Arquitetura

Camadas clássicas, sem regra de negócio nos controllers:

```
Controller  →  Service  →  Repository  →  MySQL
   (HTTP/DTO)   (regras, transações)   (JPA)
```

```
src/main/java/com/vitorsantos/barbearia_api/
├── controller/   Endpoints REST + anotações OpenAPI
├── service/      Regras de negócio e transações (FilaService concentra a fila)
├── repository/   Spring Data JPA (queries da fila, locks)
├── models/       Entidades JPA (Cliente, Barbeiro, Servico, Agendamento)
├── dto/          Contratos da API (a entidade nunca é exposta)
├── enums/        StatusAgendamento, ErrorCode
├── exception/    Exceções de negócio + GlobalExceptionHandler
├── job/          Tarefas agendadas
├── event/        FilaAtualizadaEvent
├── listener/     FilaEventListener (evento → mensagem WebSocket)
├── config/       WebSocket, beans, inicialização de senhas, Swagger
└── security/     CORS e regras de acesso HTTP

src/main/resources/
├── db/migration/ Migrations do Flyway (V1, V2, V3...)
└── static/       teste-websocket.html
```

Decisões importantes:

- **DTOs sempre**: nenhuma entidade JPA vai para a API.
- **Migrations versionadas** (Flyway) em vez de `ddl-auto=update`; o Hibernate só **valida** o schema (`ddl-auto=validate`).
- **`open-in-view` desligado**: as queries trazem o que precisam (`JOIN FETCH`), sem N+1 nem `LazyInitializationException`.
- **Tratamento de erro único** em `GlobalExceptionHandler`.

---

## 🗄️ Modelo de dados

| Tabela            | Descrição                                                                                            |
| ----------------- | ---------------------------------------------------------------------------------------------------- |
| `tb_clientes`     | Nome, telefone (único), datas de cadastro e último acesso, `removido_em` (anonimização)              |
| `tb_barbeiros`    | Nome, `ativo`, `senha_hash` (BCrypt)                                                                 |
| `tb_servicos`     | Nome (único), descrição, preço, duração em minutos, `ativo`                                          |
| `tb_agendamentos` | Cliente, barbeiro, serviço, status, data/hora, horários de chegada/início/fim/cancelamento, `versao` |

Migrations (`src/main/resources/db/migration`):

| Versão | O que faz                                                                             |
| ------ | ------------------------------------------------------------------------------------- |
| V1     | Schema original (só roda em banco novo; bancos antigos são marcados como baseline V1) |
| V2     | Serviços, campos da fila, colunas de anonimização, índices e chaves estrangeiras      |
| V3     | Coluna `senha_hash` do barbeiro                                                       |

> Nunca edite uma migration já aplicada: crie uma nova versão.

---

## 📐 Regras de negócio

### Ciclo de vida do agendamento

```
AGENDADO ──► AGUARDANDO ──► EM_ATENDIMENTO ──► FINALIZADO
    │             │
    └─────────────┴──► CANCELADO
```

- **Entrar na fila** cria o agendamento direto como `AGUARDANDO`; **marcar horário** cria como `AGENDADO`.
- Um job move `AGENDADO → AGUARDANDO` quando a data/hora chega.
- Qualquer transição fora do diagrama é recusada (`400`). `FINALIZADO` e `CANCELADO` são estados finais.

### Fila por barbeiro

- A **posição não é gravada**: é calculada pela ordem de chegada (`hora_chegada`, desempate por `id`) entre os `AGUARDANDO` do mesmo barbeiro. Por isso a fila é sempre 1, 2, 3… sem buracos nem duplicatas, e quem finaliza ou cancela some e os demais sobem automaticamente.
- Só **um atendimento por vez** por barbeiro, e o atendimento só pode começar para o **primeiro da fila**.
- Um cliente só pode estar em **uma fila por vez**.
- Barbeiro ou serviço **inativo** não recebe novos clientes.

### Concorrência

Toda operação que altera uma fila trava no banco a linha do barbeiro (e a do cliente, ao entrar na fila) com `SELECT … FOR UPDATE`, com isolamento `READ_COMMITTED`. Assim, requisições simultâneas na mesma fila são serializadas pelo banco (inclusive entre várias instâncias da API), enquanto filas de barbeiros diferentes não se bloqueiam. O agendamento também usa `@Version` (controle otimista). Conflitos viram `409`.

### Exclusões seguras

- `DELETE` de **barbeiro** e **serviço** é lógico (desativa); o histórico fica intacto. Barbeiro com agendamento em aberto não pode ser desativado.
- `DELETE` de **cliente**: sem histórico, apaga; com histórico, **anonimiza** (nome genérico, telefone liberado). Bloqueado se houver agendamento em aberto.

### Limpeza de inativos

Job diário que remove clientes sem atividade há mais de 90 dias **e** sem agendamento em aberto, usando a mesma regra de exclusão segura acima. "Atividade" é cadastro, entrada na fila, agendamento ou confirmação de presença.

### Login do barbeiro

- Senha numérica de **6 dígitos, única por barbeiro** (ela identifica quem está entrando). No banco só fica o hash BCrypt, e a API nunca devolve senha nem hash.
- Depois de **5 senhas erradas seguidas** do mesmo IP, o login fica bloqueado por 30s (`429`). Um acerto zera o contador.
- Barbeiro inativo ou sem senha não entra.
- As senhas iniciais vêm de `BARBEIROS_SENHAS_INICIAIS` e só são aplicadas a barbeiros que ainda **não têm** senha (reiniciar a API não sobrescreve nada).

### Telefone

Aceita com ou sem máscara (`(81) 99999-0000` ou `81999990000`); a API normaliza para 11 dígitos.

---

## 🔌 Endpoints

A documentação completa e testável está no **Swagger UI**. Resumo:

### Clientes

| Método | Rota                          | Descrição                                       |
| ------ | ----------------------------- | ----------------------------------------------- |
| GET    | `/clientes`                   | Lista clientes                                  |
| GET    | `/clientes/{id}`              | Busca por id                                    |
| GET    | `/clientes/telefone/{numero}` | Busca por telefone                              |
| POST   | `/clientes`                   | Cadastra (`201`; `409` se o telefone já existe) |
| DELETE | `/clientes/{id}`              | Remove ou anonimiza (`204`)                     |

### Barbeiros

| Método | Rota                     | Descrição                                             |
| ------ | ------------------------ | ----------------------------------------------------- |
| GET    | `/barbeiros?ativos=true` | Lista barbeiros                                       |
| GET    | `/barbeiros/{id}`        | Busca por id                                          |
| POST   | `/barbeiros`             | Cadastra                                              |
| PUT    | `/barbeiros/{id}`        | Atualiza nome e/ou `ativo`                            |
| DELETE | `/barbeiros/{id}`        | Desativa                                              |
| POST   | `/barbeiros/login`       | Login pela senha de 6 dígitos (`200` / `401` / `429`) |

### Serviços

| Método | Rota                    | Descrição      |
| ------ | ----------------------- | -------------- |
| GET    | `/servicos?ativos=true` | Lista serviços |
| GET    | `/servicos/{id}`        | Busca por id   |
| POST   | `/servicos`             | Cadastra       |
| PUT    | `/servicos/{id}`        | Atualiza       |
| DELETE | `/servicos/{id}`        | Desativa       |

### Fila

| Método | Rota                           | Descrição                                                   |
| ------ | ------------------------------ | ----------------------------------------------------------- |
| GET    | `/barbeiros/{id}/fila`         | Fila do barbeiro: em atendimento, próximo, total e posições |
| GET    | `/barbeiros/fila`              | Filas de todos os barbeiros ativos (painel geral)           |
| POST   | `/barbeiros/{id}/fila`         | Cliente entra no fim da fila (`201`)                        |
| POST   | `/barbeiros/{id}/fila/proximo` | Barbeiro chama o próximo cliente                            |

### Agendamentos

| Método | Rota                                    | Descrição                                  |
| ------ | --------------------------------------- | ------------------------------------------ |
| GET    | `/agendamentos`                         | Lista (mais recentes primeiro)             |
| GET    | `/agendamentos/{id}`                    | Busca por id                               |
| GET    | `/agendamentos/{id}/posicao`            | Posição do agendamento na fila             |
| POST   | `/agendamentos`                         | Marca um horário futuro (nasce `AGENDADO`) |
| PATCH  | `/agendamentos/{id}/status`             | Muda o status (finalizar, cancelar…)       |
| PATCH  | `/agendamentos/{id}/confirmar-presenca` | Cliente confirma presença                  |

### Exemplo: fluxo completo

```bash
# cadastrar cliente e escolher barbeiro/serviço (ids de exemplo)
curl -X POST localhost:8080/clientes -H 'Content-Type: application/json' \
  -d '{"nome":"João","numero":"(81) 99999-0000"}'

# entrar na fila do barbeiro 1 com o serviço 1
curl -X POST localhost:8080/barbeiros/1/fila -H 'Content-Type: application/json' \
  -d '{"clienteId":1,"servicoId":1}'

# ver a fila
curl localhost:8080/barbeiros/1/fila

# barbeiro chama o próximo e depois finaliza
curl -X POST localhost:8080/barbeiros/1/fila/proximo
curl -X PATCH localhost:8080/agendamentos/1/status -H 'Content-Type: application/json' \
  -d '{"novoStatus":"FINALIZADO"}'
```

---

## 📡 Tempo real (WebSocket)

A cada mudança na fila de um barbeiro (alguém entra, é chamado, finaliza ou cancela), a API publica a **fila atualizada** — o mesmo JSON do `GET /barbeiros/{id}/fila` — depois que a alteração foi gravada no banco.

- **Endpoint:** `/ws` (STOMP sobre SockJS)
- **Tópico:** `/topic/fila/{barbeiroId}`

```js
const client = Stomp.over(new SockJS("http://localhost:8080/ws"));
client.connect({}, () => {
  client.subscribe("/topic/fila/1", (msg) => {
    const fila = JSON.parse(msg.body); // { barbeiroId, totalNaFila, proximo, clientes: [...] }
  });
});
```

Dá para testar abrindo `/teste-websocket.html` e mudando um status pelo Swagger.

> O broker é em memória, então vale para **uma instância** da API. Com várias instâncias, troque por um broker externo (RabbitMQ etc.).

---

## 🚨 Formato de erros

Todo erro sai no mesmo formato, sem stack trace:

```json
{
  "timestamp": "2026-10-07T14:30:00",
  "status": 409,
  "erro": "Conflict",
  "codigo": "CLIENTE_JA_NA_FILA",
  "mensagem": "O cliente João já está em uma fila de atendimento",
  "path": "/barbeiros/1/fila",
  "erros": ["campo: mensagem"]
}
```

`erros` só aparece em falhas de validação. Use `codigo` (estável) para decidir o que mostrar na tela, não o texto de `mensagem`.

| Status | Quando                                                                                             |
| ------ | -------------------------------------------------------------------------------------------------- |
| `400`  | Validação, JSON inválido, id não numérico, transição de status inválida                            |
| `401`  | Senha de barbeiro incorreta                                                                        |
| `404`  | Recurso ou rota inexistente                                                                        |
| `405`  | Método HTTP não suportado                                                                          |
| `409`  | Conflito de regra (já na fila, barbeiro ocupado, fora da ordem…), telefone duplicado, concorrência |
| `429`  | Muitas tentativas de login                                                                         |
| `500`  | Erro inesperado (detalhes só no log do servidor)                                                   |

---

## 🧪 Testes

```bash
./mvnw clean test
```

Os **76 testes** usam H2 em memória (modo MySQL) com **as mesmas migrations do Flyway**: não dependem do seu MySQL nem tocam nos seus dados.

| Área                                                      | O que cobre                                                                            |
| --------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| `AgendamentoTest`                                         | Máquina de estados e horários registrados                                              |
| `ClienteControllerTest`, `BarbeiroEServicoControllerTest` | CRUD, validações, 404, 405, exclusão/anonimização                                      |
| `FilaControllerTest`                                      | Fluxo completo com dois barbeiros, ordem, cancelamento, reorganização                  |
| `FilaConcorrenciaTest`                                    | 12 threads simultâneas: posições únicas, mesmo cliente 2×, "chamar próximo" simultâneo |
| `JobsTest`                                                | Job de auto-status e limpeza de inativos                                               |
| `FilaWebSocketTest`                                       | Cliente STOMP real recebendo a fila atualizada                                         |
| `BarbeiroLoginTest`                                       | Login, hash, bloqueio por tentativas                                                   |
| `ClienteRepositoryTest`                                   | Query de clientes inativos                                                             |
| `InfraestruturaTest`                                      | Migrations, Swagger/OpenAPI e CORS                                                     |

---

## ⚠️ Limitações conhecidas

- **Só o login do barbeiro existe**: o restante da API não exige token nem sessão. Quem chamar `PATCH /agendamentos/{id}/status` direto consegue mudar status. Proteger os endpoints é o próximo passo.
- O bloqueio de tentativas de login e o broker WebSocket são **em memória** (uma instância). O bloqueio usa o IP da conexão, então atrás de um proxy todos aparecem com o mesmo IP.
- Não há controle de **conflito de horário** entre agendamentos do mesmo barbeiro.
- Não há vínculo de **quais serviços cada barbeiro oferece**.
- Agendamentos anteriores ao cadastro de serviços têm `servico_id` nulo.
