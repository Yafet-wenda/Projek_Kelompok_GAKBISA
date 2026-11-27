-- Extend the skill catalog with hard/soft skill classification.
-- This version is already applied to the active database; keep its version aligned.
ALTER TABLE skill_categories
    ADD COLUMN IF NOT EXISTS kind VARCHAR(10) NOT NULL DEFAULT 'HARD'
    CHECK (kind IN ('HARD', 'SOFT'));
