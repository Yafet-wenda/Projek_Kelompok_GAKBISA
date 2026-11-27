ALTER TABLE users ADD COLUMN IF NOT EXISTS study_program VARCHAR(150);
ALTER TABLE users ADD COLUMN IF NOT EXISTS interests TEXT;

ALTER TABLE learning_sessions ADD COLUMN IF NOT EXISTS schedule_proposed_by UUID REFERENCES users(id);
ALTER TABLE learning_sessions ADD COLUMN IF NOT EXISTS schedule_confirmed BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS barter_close_confirmations (
    barter_request_id UUID NOT NULL REFERENCES barter_requests(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (barter_request_id,user_id)
);

CREATE TABLE IF NOT EXISTS learning_needs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic VARCHAR(150) NOT NULL,
    level VARCHAR(20) NOT NULL CHECK (level IN ('BEGINNER','INTERMEDIATE','ADVANCED')),
    target_date DATE,
    details TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_learning_needs_open ON learning_needs(status,created_at DESC);

CREATE TABLE IF NOT EXISTS app_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS chat_message_reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id UUID NOT NULL REFERENCES chat_messages(id) ON DELETE CASCADE,
    reporter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RESOLVED','DISMISSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(message_id,reporter_id)
);

ALTER TABLE campus_verification_requests ADD COLUMN IF NOT EXISTS code_hash VARCHAR(64);
ALTER TABLE campus_verification_requests ADD COLUMN IF NOT EXISTS code_expires_at TIMESTAMPTZ;
ALTER TABLE campus_verification_requests ADD COLUMN IF NOT EXISTS email_confirmed_at TIMESTAMPTZ;
