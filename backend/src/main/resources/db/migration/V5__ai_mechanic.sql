CREATE TABLE ai_chat_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id),
    title           VARCHAR(120),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    last_message_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_ai_chat_sessions_user ON ai_chat_sessions(user_id, last_message_at DESC);

CREATE TABLE ai_chat_messages (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES ai_chat_sessions(id),
    role       VARCHAR(10) NOT NULL CHECK (role IN ('USER','ASSISTANT')),
    content    TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_ai_chat_messages_session ON ai_chat_messages(session_id, created_at ASC);
