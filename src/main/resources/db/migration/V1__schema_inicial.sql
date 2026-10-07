-- Schema que existia antes do Flyway (gerado pelo antigo ddl-auto=update).
-- Bancos já existentes são marcados como baseline nesta versão e NÃO
-- executam este script; ele só roda em bancos novos/vazios.
-- Os nomes das constraints são os mesmos que o Hibernate gerou, para que a
-- V2 funcione igual nos dois cenários.

CREATE TABLE tb_clientes (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    data_cadastro   DATETIME(6)  NULL,
    nome            VARCHAR(100) NOT NULL,
    numero_telefone VARCHAR(255) NOT NULL,
    ultimo_login    DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT UK2mf83n8eo73lb2f0aimr18mjf UNIQUE (numero_telefone),
    CONSTRAINT uk_cliente_telefone UNIQUE (numero_telefone)
);

CREATE TABLE tb_barbeiros (
    id    BIGINT       NOT NULL AUTO_INCREMENT,
    ativo BIT(1)       NOT NULL,
    nome  VARCHAR(100) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE tb_agendamentos (
    id                      BIGINT      NOT NULL AUTO_INCREMENT,
    data_hora               DATETIME(6) NOT NULL,
    hora_chegada            DATETIME(6) NULL,
    status_agendamento      ENUM ('AGENDADO','AGUARDANDO','CANCELADO','EM_ATENDIMENTO','FINALIZADO') NOT NULL,
    barbeiro_id             BIGINT      NOT NULL,
    cliente_id              BIGINT      NOT NULL,
    confirmado_pelo_cliente BIT(1)      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT FK7ct0o43m3632x3lxv8bmkordt FOREIGN KEY (barbeiro_id) REFERENCES tb_barbeiros (id),
    CONSTRAINT FKawv0m1ep3hccjl2moig66g0kw FOREIGN KEY (cliente_id) REFERENCES tb_clientes (id)
);
