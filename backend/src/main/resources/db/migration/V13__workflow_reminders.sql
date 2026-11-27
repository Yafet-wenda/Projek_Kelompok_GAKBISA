CREATE TABLE IF NOT EXISTS workflow_reminders (
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  reference_id UUID NOT NULL,
  reminder_type VARCHAR(24) NOT NULL CHECK (reminder_type IN ('UNSCHEDULED_BARTER','SESSION_REVIEW')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id,reference_id,reminder_type)
);
