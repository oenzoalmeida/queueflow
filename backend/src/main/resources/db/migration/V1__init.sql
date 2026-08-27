CREATE TABLE establishments (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(160) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(80)  NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN','ATTENDANT')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE queues (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(120) NOT NULL UNIQUE,
    prefix     VARCHAR(3)   NOT NULL UNIQUE,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE counters (
    id                    BIGSERIAL PRIMARY KEY,
    name                  VARCHAR(80) NOT NULL UNIQUE,
    active                BOOLEAN     NOT NULL DEFAULT TRUE,
    current_attendant_id  BIGINT REFERENCES users(id),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- per-queue sequential numbering; one row per queue/day, row-locked at issue time
CREATE TABLE daily_sequences (
    queue_id    BIGINT      NOT NULL REFERENCES queues(id) ON DELETE CASCADE,
    day         DATE        NOT NULL,
    last_number INTEGER     NOT NULL DEFAULT 0,
    PRIMARY KEY (queue_id, day)
);

-- priority tickets get their own establishment-wide "P" numbering, independent of each queue's normal sequence
CREATE TABLE priority_daily_sequences (
    day         DATE        NOT NULL PRIMARY KEY,
    last_number INTEGER     NOT NULL DEFAULT 0
);

CREATE TABLE tickets (
    id                 BIGSERIAL PRIMARY KEY,
    queue_id           BIGINT      NOT NULL REFERENCES queues(id),
    number             INTEGER     NOT NULL,
    display_code       VARCHAR(12) NOT NULL,
    priority_type      VARCHAR(10) NOT NULL CHECK (priority_type IN ('NORMAL','PRIORITY')),
    status             VARCHAR(15) NOT NULL CHECK (status IN ('WAITING','CALLED','IN_SERVICE','FINISHED','ABSENT','CANCELLED')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    called_at          TIMESTAMPTZ,
    service_started_at TIMESTAMPTZ,
    finished_at        TIMESTAMPTZ,
    counter_id         BIGINT REFERENCES counters(id),
    attendant_id       BIGINT REFERENCES users(id)
);

CREATE INDEX idx_tickets_status_created   ON tickets (status, created_at);
CREATE INDEX idx_tickets_queue_status     ON tickets (queue_id, status);
CREATE INDEX idx_tickets_called_at        ON tickets (called_at);
CREATE INDEX idx_tickets_counter_id       ON tickets (counter_id);
CREATE INDEX idx_tickets_attendant_id     ON tickets (attendant_id);
CREATE FUNCTION ticket_issue_day(ts TIMESTAMPTZ) RETURNS DATE AS $$
    SELECT (ts AT TIME ZONE 'UTC')::date
$$ LANGUAGE sql IMMUTABLE;

CREATE UNIQUE INDEX uq_daily_seq_numbers  ON tickets (queue_id, priority_type, number, ticket_issue_day(created_at)); -- same number can't repeat per queue/type within a day

CREATE TABLE ticket_events (
    id           BIGSERIAL PRIMARY KEY,
    ticket_id    BIGINT     NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    type         VARCHAR(20) NOT NULL CHECK (type IN ('CREATED','CALLED','RECALLED','SERVICE_STARTED','FINISHED','ABSENT','CANCELLED')),
    attendant_id BIGINT REFERENCES users(id),
    counter_id   BIGINT REFERENCES counters(id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ticket_events_ticket ON ticket_events (ticket_id);

CREATE TABLE priority_settings (
    id                       BIGSERIAL PRIMARY KEY,
    normals_before_priority  INTEGER NOT NULL DEFAULT 2 CHECK (normals_before_priority BETWEEN 0 AND 99)
);

INSERT INTO priority_settings (id, normals_before_priority) VALUES (1, 2);
SELECT setval('priority_settings_id_seq', 1);
