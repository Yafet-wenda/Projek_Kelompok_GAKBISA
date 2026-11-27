ALTER TABLE learning_sessions ALTER COLUMN barter_request_id DROP NOT NULL;
ALTER TABLE learning_sessions ADD COLUMN IF NOT EXISTS group_id UUID REFERENCES study_groups(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_sessions_group ON learning_sessions(group_id, scheduled_at);

CREATE TABLE IF NOT EXISTS session_schedule_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    learning_session_id UUID NOT NULL REFERENCES learning_sessions(id) ON DELETE CASCADE,
    actor_id UUID NOT NULL REFERENCES users(id),
    old_scheduled_at TIMESTAMPTZ NOT NULL,
    proposed_scheduled_at TIMESTAMPTZ NOT NULL,
    old_duration_minutes INTEGER NOT NULL,
    proposed_duration_minutes INTEGER NOT NULL,
    decision VARCHAR(20) NOT NULL CHECK (decision IN ('PROPOSED','ACCEPTED','REJECTED','WITHDRAWN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS session_reminders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    learning_session_id UUID NOT NULL REFERENCES learning_sessions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    remind_minutes INTEGER NOT NULL CHECK (remind_minutes IN (60, 1440)),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (learning_session_id, user_id, remind_minutes)
);

CREATE TABLE IF NOT EXISTS session_attendance (
    learning_session_id UUID NOT NULL REFERENCES learning_sessions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    attended BOOLEAN NOT NULL,
    marked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (learning_session_id, user_id)
);
ALTER TABLE learning_sessions ADD COLUMN IF NOT EXISTS no_show_processed BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE users ADD COLUMN IF NOT EXISTS is_verified BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE IF NOT EXISTS campus_verification_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    campus_email VARCHAR(254) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID REFERENCES users(id),
    UNIQUE (user_id, campus_email)
);
CREATE INDEX IF NOT EXISTS idx_campus_verification_status ON campus_verification_requests(status, requested_at);
