ALTER TABLE learning_sessions
    ADD COLUMN IF NOT EXISTS reschedule_proposed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS reschedule_proposed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reschedule_proposed_time TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reschedule_proposed_duration INTEGER;
