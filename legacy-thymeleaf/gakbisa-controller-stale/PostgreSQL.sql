-- GAKBISA - Skema Supabase PostgreSQL
-- File dipertahankan dengan nama lama agar tidak menambah file baru.
-- Jalankan di Supabase Dashboard > SQL Editor.
-- PERINGATAN: skrip ini menghapus tabel GAKBISA yang bernama sama sebelum membuat ulang.
-- Jangan jalankan pada project berisi data penting tanpa backup.

DROP TABLE IF EXISTS public.reports CASCADE;
DROP TABLE IF EXISTS public.notifications CASCADE;
DROP TABLE IF EXISTS public.reviews CASCADE;
DROP TABLE IF EXISTS public.learning_sessions CASCADE;
DROP TABLE IF EXISTS public.barter_requests CASCADE;
DROP TABLE IF EXISTS public.portfolios CASCADE;
DROP TABLE IF EXISTS public.user_skills CASCADE;
DROP TABLE IF EXISTS public.skills CASCADE;
DROP TABLE IF EXISTS public.skill_categories CASCADE;
DROP TABLE IF EXISTS public.users CASCADE;

CREATE TABLE public.users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name VARCHAR(120) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'STUDENT' CHECK (role IN ('STUDENT','ADMIN')),
    bio TEXT,
    campus VARCHAR(150),
    avatar_path TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE public.skill_categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(80) NOT NULL UNIQUE,
    description TEXT
);

CREATE TABLE public.skills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID NOT NULL REFERENCES public.skill_categories(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    UNIQUE (category_id, name)
);

CREATE TABLE public.user_skills (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    skill_id UUID NOT NULL REFERENCES public.skills(id) ON DELETE RESTRICT,
    type VARCHAR(10) NOT NULL CHECK (type IN ('OFFER','WANT')),
    level VARCHAR(20) NOT NULL DEFAULT 'BEGINNER' CHECK (level IN ('BEGINNER','INTERMEDIATE','ADVANCED')),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, skill_id, type)
);
CREATE INDEX idx_user_skills_skill_type ON public.user_skills(skill_id, type);

CREATE TABLE public.portfolios (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    description TEXT,
    file_path TEXT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size > 0),
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC' CHECK (visibility IN ('PUBLIC','PRIVATE')),
    is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_portfolios_user ON public.portfolios(user_id);
CREATE INDEX idx_portfolios_visibility ON public.portfolios(visibility, is_hidden);

CREATE TABLE public.barter_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    target_user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    offered_user_skill_id UUID NOT NULL REFERENCES public.user_skills(id) ON DELETE RESTRICT,
    wanted_user_skill_id UUID NOT NULL REFERENCES public.user_skills(id) ON DELETE RESTRICT,
    message TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACCEPTED','REJECTED','CANCELLED','COMPLETED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);
CREATE INDEX idx_barter_requester ON public.barter_requests(requester_id, status);
CREATE INDEX idx_barter_target ON public.barter_requests(target_user_id, status);

CREATE TABLE public.learning_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    barter_request_id UUID NOT NULL REFERENCES public.barter_requests(id) ON DELETE CASCADE,
    scheduled_at TIMESTAMPTZ NOT NULL,
    duration_minutes INTEGER NOT NULL DEFAULT 60 CHECK (duration_minutes > 0),
    meeting_provider VARCHAR(20) NOT NULL DEFAULT 'JITSI' CHECK (meeting_provider IN ('JITSI','EXTERNAL_LINK')),
    meeting_room VARCHAR(150) NOT NULL UNIQUE,
    meeting_url TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED' CHECK (status IN ('SCHEDULED','ONGOING','COMPLETED','CANCELLED')),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_sessions_barter ON public.learning_sessions(barter_request_id);
CREATE INDEX idx_sessions_schedule ON public.learning_sessions(scheduled_at, status);

CREATE TABLE public.reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    learning_session_id UUID NOT NULL REFERENCES public.learning_sessions(id) ON DELETE CASCADE,
    reviewer_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    reviewee_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (learning_session_id, reviewer_id)
);
CREATE INDEX idx_reviews_reviewee ON public.reviews(reviewee_id);

CREATE TABLE public.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    title VARCHAR(120) NOT NULL,
    body TEXT,
    type VARCHAR(40) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notif_user ON public.notifications(user_id, is_read, created_at DESC);

CREATE TABLE public.reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES public.users(id) ON DELETE CASCADE,
    target_type VARCHAR(20) NOT NULL CHECK (target_type IN ('PORTFOLIO','USER')),
    target_id UUID NOT NULL,
    reason TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','REVIEWING','RESOLVED','DISMISSED')),
    reviewed_by UUID REFERENCES public.users(id) ON DELETE SET NULL,
    admin_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ
);
CREATE INDEX idx_reports_status ON public.reports(status, created_at);
CREATE INDEX idx_reports_target ON public.reports(target_type, target_id);

-- Data awal kategori; aman dijalankan ulang.
INSERT INTO public.skill_categories (name, description) VALUES
 ('Pemrograman', 'Bahasa pemrograman, web, mobile, dan pengembangan perangkat lunak'),
 ('Desain', 'Desain grafis, UI/UX, dan multimedia'),
 ('Bahasa', 'Bahasa asing dan kemampuan komunikasi'),
 ('Data', 'Analisis data, statistik, dan basis data'),
 ('Soft Skill', 'Public speaking, kepemimpinan, manajemen waktu')
ON CONFLICT (name) DO NOTHING;

INSERT INTO public.skills (category_id, name)
SELECT c.id, s.name
FROM (VALUES
 ('Pemrograman','Java'), ('Pemrograman','Python'), ('Pemrograman','JavaScript'),
 ('Pemrograman','Spring Boot'), ('Desain','UI/UX'), ('Desain','Desain Grafis'),
 ('Desain','Video Editing'), ('Bahasa','Bahasa Inggris'), ('Bahasa','Bahasa Jepang'),
 ('Data','SQL'), ('Data','Excel'), ('Soft Skill','Public Speaking')
) AS s(category_name, name)
JOIN public.skill_categories c ON c.name = s.category_name
ON CONFLICT (category_id, name) DO NOTHING;

-- Jangan menyimpan akun admin dengan hash placeholder. Buat admin melalui alur aman aplikasi.
