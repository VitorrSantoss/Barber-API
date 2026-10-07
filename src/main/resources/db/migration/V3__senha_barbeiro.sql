-- Hash BCrypt da senha numérica (PIN) do barbeiro, usada no login do balcão.
-- Nulo = barbeiro ainda sem senha (não consegue entrar).
ALTER TABLE tb_barbeiros ADD COLUMN senha_hash VARCHAR(100) NULL;
