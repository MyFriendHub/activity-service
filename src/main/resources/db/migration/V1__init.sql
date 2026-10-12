-- V1__init.sql — начальная схема Activity Service
-- Модель: Activity + ActivityParticipant + ChatMessage (без групп, без FK)

-- ============================================================
-- activities — активности (корень агрегата)
-- ============================================================
CREATE TABLE activities (
    id               BIGSERIAL PRIMARY KEY,
    creator_id       BIGINT      NOT NULL,          -- User Service, просто id
    title            VARCHAR(200) NOT NULL,
    description      TEXT        NOT NULL DEFAULT '',
    type             VARCHAR(30) NOT NULL DEFAULT 'OTHER',
    start_at         TIMESTAMPTZ NOT NULL,
    location         VARCHAR(200) NOT NULL DEFAULT '',
    status           VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    finished_at      TIMESTAMPTZ,                   -- момент FINISHED/CANCELLED (retention чата)
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- статус и тип — только из допустимого набора
    CONSTRAINT chk_activities_status CHECK (status IN ('DRAFT', 'PLANNED', 'ACTIVE', 'FINISHED', 'CANCELLED')),
    CONSTRAINT chk_activities_type   CHECK (type IN ('BOARD_GAMES', 'VIDEO_GAMES', 'MOVIE', 'RESTAURANT',
                                                     'TRIP', 'PARTY', 'SPORT', 'WALK', 'CONCERT', 'OTHER'))
);

CREATE INDEX idx_activities_creator_id ON activities (creator_id);
CREATE INDEX idx_activities_status_start ON activities (status, start_at);

-- ============================================================
-- activity_participants — участники (RSVP + список доступа к чату)
-- ============================================================
CREATE TABLE activity_participants (
    id           BIGSERIAL PRIMARY KEY,
    activity_id  BIGINT      NOT NULL,
    user_id      BIGINT      NOT NULL,              -- User Service, просто id
    status       VARCHAR(20) NOT NULL DEFAULT 'INVITED',
    invited_by   BIGINT      NOT NULL,              -- User Service, просто id
    invited_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ,

    CONSTRAINT chk_participants_status CHECK (status IN ('INVITED', 'ACCEPTED', 'DECLINED', 'MAYBE')),

    -- страховка от гонок при конкурентных инвайтах (вместо FK — проверки в приложении)
    CONSTRAINT uq_participants_activity_user UNIQUE (activity_id, user_id)
);

CREATE INDEX idx_participants_activity_id ON activity_participants (activity_id);
CREATE INDEX idx_participants_user_id ON activity_participants (user_id);

-- ============================================================
-- chat_messages — сообщения чатов (чат = все сообщения активности)
-- ============================================================
CREATE TABLE chat_messages (
    id           BIGSERIAL PRIMARY KEY,             -- монотонный курсор поллинга
    activity_id  BIGINT      NOT NULL,
    author_id    BIGINT      NOT NULL,              -- User Service, просто id
    text         TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at   TIMESTAMPTZ                       -- soft delete автором своего сообщения
);

-- курсор поллинга: сообщения одного чата по порядку
CREATE INDEX idx_chat_messages_activity_id_id ON chat_messages (activity_id, id);
