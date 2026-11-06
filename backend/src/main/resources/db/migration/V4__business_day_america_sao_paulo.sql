-- Alinha o "dia" da unicidade de numeração ao fuso do negócio (America/Sao_Paulo),
-- o mesmo usado pela aplicação (BusinessClock). Antes a função usava UTC fixo
-- enquanto as sequências diárias usavam o fuso do servidor: em qualquer fuso
-- diferente de UTC, senhas emitidas após a meia-noite local e antes da meia-noite
-- UTC reutilizavam números ainda dentro do mesmo "dia UTC" e a emissão falhava
-- com violação de unicidade. Verificado em produção antes da migração: nenhum
-- par (fila, tipo, número) colide sob o dia em America/Sao_Paulo.
DROP INDEX IF EXISTS uq_daily_seq_numbers;
DROP FUNCTION IF EXISTS ticket_issue_day(TIMESTAMPTZ);

CREATE FUNCTION ticket_issue_day(ts TIMESTAMPTZ) RETURNS DATE AS $$
    SELECT (ts AT TIME ZONE 'America/Sao_Paulo')::date
$$ LANGUAGE sql IMMUTABLE;

CREATE UNIQUE INDEX uq_daily_seq_numbers ON tickets (queue_id, priority_type, number, ticket_issue_day(created_at));
