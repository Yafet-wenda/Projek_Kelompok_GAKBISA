-- GAKBISA skema awal (bagian backend, selaras ERD proposal §6.3)
-- Penanggung jawab integrasi: Yafet (backend) + Haudajun (database/QA)

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. users
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name     VARCHAR(120) NOT NULL,
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('STUDENT', 'ADMIN')),
    bio           TEXT,
    campus        VARCHAR(150),
    avatar_path   TEXT,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 2a. skill_categories
CREATE TABLE skill_categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(80) NOT NULL UNIQUE,
    description TEXT
);

-- 2b. skills
CREATE TABLE skills (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES skill_categories(id),
    name        VARCHAR(100) NOT NULL,
    description TEXT
);
CREATE INDEX idx_skills_category ON skills(category_id);

-- 3. user_skills
CREATE TABLE user_skills (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id),
    skill_id   UUID NOT NULL REFERENCES skills(id),
    type       VARCHAR(10) NOT NULL CHECK (type IN ('OFFER', 'WANT')),
    level      VARCHAR(20) NOT NULL CHECK (level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED')),
    notes      TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, skill_id, type)
);
CREATE INDEX idx_user_skills_user ON user_skills(user_id, type);

-- 4. portfolios
CREATE TABLE portfolios (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id),
    title       VARCHAR(150) NOT NULL,
    description TEXT,
    file_path   TEXT NOT NULL,
    file_name   VARCHAR(255) NOT NULL,
    mime_type   VARCHAR(100) NOT NULL,
    file_size   BIGINT NOT NULL,
    visibility  VARCHAR(20) NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_portfolios_user ON portfolios(user_id);
CREATE INDEX idx_portfolios_visibility ON portfolios(visibility);

-- 5. barter_requests
CREATE TABLE barter_requests (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id         UUID NOT NULL REFERENCES users(id),
    target_user_id       UUID NOT NULL REFERENCES users(id),
    offered_user_skill_id UUID NOT NULL REFERENCES user_skills(id),
    wanted_user_skill_id  UUID NOT NULL REFERENCES user_skills(id),
    message              TEXT,
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING','ACCEPTED','REJECTED','CANCELLED','COMPLETED')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (requester_id <> target_user_id)
);
CREATE INDEX idx_barter_requester ON barter_requests(requester_id, status);
CREATE INDEX idx_barter_target ON barter_requests(target_user_id, status);

-- 6. learning_sessions
CREATE TABLE learning_sessions (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    barter_request_id UUID NOT NULL REFERENCES barter_requests(id),
    scheduled_at      TIMESTAMPTZ NOT NULL,
    duration_minutes  INTEGER NOT NULL CHECK (duration_minutes > 0),
    meeting_provider  VARCHAR(20) NOT NULL DEFAULT 'JITSI' CHECK (meeting_provider IN ('JITSI','EXTERNAL_LINK')),
    meeting_room      VARCHAR(255) NOT NULL UNIQUE,
    meeting_url       TEXT,
    status            VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED'
                      CHECK (status IN ('SCHEDULED','ONGOING','COMPLETED','CANCELLED')),
    completed_at      TIMESTAMPTZ
);
CREATE INDEX idx_sessions_status ON learning_sessions(status);

-- 7. reviews
CREATE TABLE reviews (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    learning_session_id UUID NOT NULL REFERENCES learning_sessions(id),
    reviewer_id         UUID NOT NULL REFERENCES users(id),
    reviewee_id         UUID NOT NULL REFERENCES users(id),
    rating              SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment             TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (learning_session_id, reviewer_id),
    CHECK (reviewer_id <> reviewee_id)
);
CREATE INDEX idx_reviews_reviewee ON reviews(reviewee_id);

-- 8. notifications
CREATE TABLE notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id),
    title      VARCHAR(120) NOT NULL,
    body       TEXT,
    type       VARCHAR(40) NOT NULL,
    is_read    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications(user_id, is_read);

-- 9. reports
CREATE TABLE reports (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES users(id),
    target_type VARCHAR(20) NOT NULL CHECK (target_type IN ('PORTFOLIO','USER')),
    target_id   UUID NOT NULL,
    reason      TEXT NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                CHECK (status IN ('OPEN','REVIEWING','RESOLVED','DISMISSED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reports_status ON reports(status);
