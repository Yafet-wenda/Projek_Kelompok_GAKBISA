CREATE TABLE IF NOT EXISTS session_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    learning_session_id UUID NOT NULL REFERENCES learning_sessions(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    achieved TEXT NOT NULL,
    remaining TEXT,
    needs_followup BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (learning_session_id, user_id)
);

CREATE TABLE IF NOT EXISTS learning_goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    barter_request_id UUID NOT NULL REFERENCES barter_requests(id) ON DELETE CASCADE,
    description VARCHAR(500) NOT NULL,
    is_done BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_learning_goals_barter ON learning_goals(barter_request_id);

CREATE TABLE IF NOT EXISTS study_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    skill_id UUID NOT NULL REFERENCES skills(id),
    level VARCHAR(20) NOT NULL CHECK (level IN ('BEGINNER','INTERMEDIATE','ADVANCED')),
    max_members INTEGER NOT NULL CHECK (max_members BETWEEN 2 AND 100),
    visibility VARCHAR(20) NOT NULL CHECK (visibility IN ('OPEN','INVITE')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','CLOSED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS group_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES study_groups(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER','MEMBER')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING','ACTIVE','LEFT')),
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (group_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_group_members_user ON group_members(user_id, status);

ALTER TABLE chat_rooms ADD COLUMN IF NOT EXISTS study_group_id UUID REFERENCES study_groups(id) ON DELETE CASCADE;
CREATE UNIQUE INDEX IF NOT EXISTS uq_chat_room_study_group ON chat_rooms(study_group_id) WHERE study_group_id IS NOT NULL;

ALTER TABLE users ADD COLUMN IF NOT EXISTS strike_count INTEGER NOT NULL DEFAULT 0;
CREATE TABLE IF NOT EXISTS disputes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    barter_request_id UUID NOT NULL REFERENCES barter_requests(id) ON DELETE CASCADE,
    reporter_id UUID NOT NULL REFERENCES users(id),
    partner_id UUID NOT NULL REFERENCES users(id),
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','RESOLVED','DISMISSED')),
    resolution TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_disputes_status ON disputes(status, created_at);
