-- ─────────────────────────────────────────────────────────────────────────
-- Serviços da barbearia (corte, barba...)
-- ─────────────────────────────────────────────────────────────────────────
CREATE TABLE tb_servicos (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    nome            VARCHAR(100)  NOT NULL,
    descricao       VARCHAR(255)  NULL,
    preco           DECIMAL(10,2) NOT NULL,
    duracao_minutos INT           NOT NULL,
    ativo           BIT(1)        NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_servico_nome UNIQUE (nome)
);

-- ─────────────────────────────────────────────────────────────────────────
-- Clientes
-- ─────────────────────────────────────────────────────────────────────────
-- Índice único duplicado (o Hibernate criou dois para a mesma coluna)
ALTER TABLE tb_clientes DROP CONSTRAINT UK2mf83n8eo73lb2f0aimr18mjf;

-- Telefone passa a aceitar NULL: clientes removidos por inatividade que têm
-- histórico de atendimentos são anonimizados (não apagados), e o telefone
-- deles precisa ficar livre para um novo cadastro.
ALTER TABLE tb_clientes MODIFY numero_telefone VARCHAR(20) NULL;

ALTER TABLE tb_clientes ADD COLUMN removido_em DATETIME(6) NULL;

-- ultimo_login passa a registrar o último acesso/atividade do cliente e é a
-- base da remoção por inatividade; registros antigos partem da data de cadastro.
UPDATE tb_clientes SET data_cadastro = CURRENT_TIMESTAMP WHERE data_cadastro IS NULL;
UPDATE tb_clientes SET ultimo_login = data_cadastro WHERE ultimo_login IS NULL;

-- ─────────────────────────────────────────────────────────────────────────
-- Agendamentos / fila
-- ─────────────────────────────────────────────────────────────────────────
-- servico_id é opcional no banco só por causa dos registros antigos; a API
-- exige o serviço em todo agendamento/entrada na fila novo.
ALTER TABLE tb_agendamentos ADD COLUMN servico_id BIGINT NULL;
ALTER TABLE tb_agendamentos ADD COLUMN hora_inicio_atendimento DATETIME(6) NULL;
ALTER TABLE tb_agendamentos ADD COLUMN hora_fim_atendimento DATETIME(6) NULL;
ALTER TABLE tb_agendamentos ADD COLUMN hora_cancelamento DATETIME(6) NULL;
-- Controle de concorrência otimista (@Version)
ALTER TABLE tb_agendamentos ADD COLUMN versao BIGINT NOT NULL DEFAULT 0;

ALTER TABLE tb_agendamentos
    ADD CONSTRAINT fk_agendamento_servico FOREIGN KEY (servico_id) REFERENCES tb_servicos (id);

-- Quem está na fila precisa ter hora de chegada (é ela que define a ordem)
UPDATE tb_agendamentos
SET hora_chegada = data_hora
WHERE status_agendamento IN ('AGUARDANDO', 'EM_ATENDIMENTO') AND hora_chegada IS NULL;

CREATE INDEX idx_agendamento_fila ON tb_agendamentos (barbeiro_id, status_agendamento, hora_chegada);
CREATE INDEX idx_agendamento_cliente_status ON tb_agendamentos (cliente_id, status_agendamento);
CREATE INDEX idx_agendamento_status_data ON tb_agendamentos (status_agendamento, data_hora);
