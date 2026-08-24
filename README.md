# Barber - API
# 💈 Barbearia - API

API REST para gerenciamento de uma barbearia, responsável pelo controle de autenticação, agendamentos, barbeiros, clientes, serviços e filas de atendimento em tempo real.

> 🚧 **Projeto em fase inicial de desenvolvimento.** Funcionalidades, contratos de endpoint e modelagem ainda estão mudando com frequência - não use em produção. Este README reflete o que está implementado até o momento, não o produto final planejado.

---

## 🛠️ Tecnologias

- Java 21
- Spring Boot
- Spring Security
- Spring Data -
- Hibernate
- Maven
- WebSocket (STOMP + SockJS)
- JWT Authentication
- Lombok
- Swagger / OpenAPI

> ⚠️ Algumas dependências acima (Spring Security, JWT, WebSocket) já estão no projeto, mas **ainda não estão implementadas de fato** — ver seção [O que ainda não existe](#-o-que-ainda-não-existe) abaixo.

---

## ✅ O que já está implementado

### Clientes
- Cadastro com telefone único (validação de formato + checagem de duplicidade)
- Normalização automática do telefone (aceita com ou sem máscara, salva só dígitos)
- Listagem e remoção

### Barbeiros
- Cadastro, listagem e remoção (CRUD básico)

### Agendamentos
- Criação de agendamento (cliente + barbeiro + data/hora)
- Ciclo de vida com transições de status controladas: `AGENDADO → AGUARDANDO → EM_ATENDIMENTO → FINALIZADO`, com `CANCELADO` possível a partir de `AGENDADO` ou `AGUARDANDO`
- Transições fora dessa ordem são bloqueadas automaticamente
- **Transição automática por horário:** um job (`@Scheduled`, roda a cada 1 min) move o agendamento de `AGENDADO` para `AGUARDANDO` sozinho, assim que a data/hora marcada chega — sem precisar de ação manual
- **Confirmação de presença:** o cliente pode confirmar presença a qualquer momento antes do dia chegar; isso não muda o status, só sinaliza pro barbeiro que aquele cliente é mais confiável

### Fila de atendimento
- Consulta da fila de um barbeiro específico, ordenada por ordem de chegada, com posição calculada
- Consulta da fila de todos os barbeiros de uma vez (painel geral)
- Cada cliente na fila mostra se confirmou presença previamente ou não
- **Modelo atual: polling.** O front precisa consultar os endpoints periodicamente — ainda não há push em tempo real (ver roadmap)

### Tratamento de erros
- Hierarquia de exceções de negócio própria (`ResourceNotFoundException`, `ConflitoDeRegraException`, `ValidacaoException`)
- Handler global padroniza toda resposta de erro da API no mesmo formato, incluindo erros de validação de campo, violação de constraint do banco e falhas inesperadas

### Documentação
- Endpoints principais documentados via Swagger/OpenAPI (`/swagger-ui/index.html` após subir a aplicação)

---

## 🚫 O que ainda não existe

- **Autenticação/autorização real** — `Spring Security` está configurado para liberar tudo (`permitAll`), e a dependência de JWT ainda não tem nenhuma implementação por trás
- **WebSocket em tempo real** — a fila hoje só atualiza por polling; o push automático via STOMP é o próximo passo (issue #9)
- **Exclusão automática de clientes inativos** — ainda não implementada
- **Testes automatizados** — nenhum teste de integração/unitário está no projeto no momento (foi uma decisão consciente de adiar, para focar primeiro na modelagem e nas regras de negócio)
- **Cadastro de serviços** (corte, barba, combo etc.) e vínculo com agendamento — mencionado na descrição do projeto, mas ainda não modelado

---

## 🗺️ Próximos passos

| # | O que é | Status |
|---|---|---|
| 9 | WebSocket (STOMP) — push automático de atualização da fila, substituindo o polling | 🔜 Próxima |
| 10 | Query que identifica clientes inativos há 90+ dias sem agendamento futuro | ⬜ Planejada |
| 11 | Job agendado de exclusão respeitando agendamentos futuros | ⬜ Planejada |
| 12 | Testes de integração cobrindo os cenários de borda da exclusão | ⬜ Planejada |

Depois disso, os próximos grandes blocos (ainda sem issues detalhadas) são: autenticação real com JWT, cadastro de serviços, e testes automatizados cobrindo o que já existe.

---