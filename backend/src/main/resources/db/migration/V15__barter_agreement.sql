CREATE TABLE IF NOT EXISTS barter_agreements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    barter_request_id UUID NOT NULL UNIQUE REFERENCES barter_requests(id) ON DELETE CASCADE,
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes BETWEEN 15 AND 240),
    session_count INTEGER NOT NULL CHECK (session_count BETWEEN 1 AND 50),
    proposed_by UUID NOT NULL REFERENCES users(id),
    requester_accepted_at TIMESTAMPTZ,
    target_accepted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_barter_agreements_updated ON barter_agreements(updated_at DESC);

ALTER TABLE learning_sessions
    ADD COLUMN IF NOT EXISTS followup_of_id UUID REFERENCES learning_sessions(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_learning_sessions_followup_of ON learning_sessions(followup_of_id);
