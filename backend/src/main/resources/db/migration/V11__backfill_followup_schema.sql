-- Compatibility backfill if an earlier deployment created the follow-up tables
-- with the initial partial migration definitions.
ALTER TABLE learning_goals ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users(id);
UPDATE learning_goals g SET created_by=b.requester_id FROM barter_requests b
    WHERE g.barter_request_id=b.id AND g.created_by IS NULL;
ALTER TABLE learning_goals ALTER COLUMN created_by SET NOT NULL;

ALTER TABLE disputes ADD COLUMN IF NOT EXISTS partner_id UUID REFERENCES users(id);
ALTER TABLE disputes ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ;
ALTER TABLE disputes ADD COLUMN IF NOT EXISTS resolution TEXT;
UPDATE disputes d SET partner_id=CASE WHEN d.reporter_id=b.requester_id THEN b.target_user_id ELSE b.requester_id END
    FROM barter_requests b WHERE d.barter_request_id=b.id AND d.partner_id IS NULL;
ALTER TABLE disputes ALTER COLUMN partner_id SET NOT NULL;
