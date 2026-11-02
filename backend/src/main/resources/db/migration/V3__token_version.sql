-- Revogação de sessões: token JWT carrega a versão; divergência (troca/reset de senha) não autentica.
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
