-- WARNING: This schema is for context only and is not meant to be run.
-- Table order and constraints may not be valid for execution.

CREATE TABLE public.users (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  full_name character varying NOT NULL,
  email character varying NOT NULL UNIQUE,
  password_hash character varying NOT NULL,
  role character varying NOT NULL DEFAULT 'STUDENT'::character varying CHECK (role::text = ANY (ARRAY['STUDENT'::character varying, 'ADMIN'::character varying]::text[])),
  bio text,
  campus character varying,
  avatar_path text,
  status character varying NOT NULL DEFAULT 'ACTIVE'::character varying CHECK (status::text = ANY (ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying]::text[])),
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT users_pkey PRIMARY KEY (id)
);
CREATE TABLE public.skill_categories (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  name character varying NOT NULL UNIQUE,
  description text,
  CONSTRAINT skill_categories_pkey PRIMARY KEY (id)
);
CREATE TABLE public.skills (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  category_id uuid NOT NULL,
  name character varying NOT NULL,
  description text,
  CONSTRAINT skills_pkey PRIMARY KEY (id),
  CONSTRAINT skills_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.skill_categories(id)
);
CREATE TABLE public.user_skills (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  skill_id uuid NOT NULL,
  type character varying NOT NULL CHECK (type::text = ANY (ARRAY['OFFER'::character varying, 'WANT'::character varying]::text[])),
  level character varying NOT NULL DEFAULT 'BEGINNER'::character varying CHECK (level::text = ANY (ARRAY['BEGINNER'::character varying, 'INTERMEDIATE'::character varying, 'ADVANCED'::character varying]::text[])),
  notes text,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT user_skills_pkey PRIMARY KEY (id),
  CONSTRAINT user_skills_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id),
  CONSTRAINT user_skills_skill_id_fkey FOREIGN KEY (skill_id) REFERENCES public.skills(id)
);
CREATE TABLE public.portfolios (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  title character varying NOT NULL,
  description text,
  file_path text NOT NULL,
  file_name character varying NOT NULL,
  mime_type character varying NOT NULL CHECK (mime_type::text = ANY (ARRAY['image/jpeg'::character varying, 'image/png'::character varying, 'image/webp'::character varying, 'application/pdf'::character varying]::text[])),
  file_size bigint NOT NULL CHECK (file_size > 0 AND file_size <= 10485760),
  visibility character varying NOT NULL DEFAULT 'PRIVATE'::character varying CHECK (visibility::text = ANY (ARRAY['PUBLIC'::character varying, 'PRIVATE'::character varying]::text[])),
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT portfolios_pkey PRIMARY KEY (id),
  CONSTRAINT portfolios_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id)
);
CREATE TABLE public.barter_requests (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  requester_id uuid NOT NULL,
  target_user_id uuid NOT NULL,
  offered_user_skill_id uuid NOT NULL,
  wanted_user_skill_id uuid NOT NULL,
  message text,
  status character varying NOT NULL DEFAULT 'PENDING'::character varying CHECK (status::text = ANY (ARRAY['PENDING'::character varying, 'ACCEPTED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying, 'COMPLETED'::character varying]::text[])),
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  updated_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT barter_requests_pkey PRIMARY KEY (id),
  CONSTRAINT barter_requests_requester_id_fkey FOREIGN KEY (requester_id) REFERENCES public.users(id),
  CONSTRAINT barter_requests_target_user_id_fkey FOREIGN KEY (target_user_id) REFERENCES public.users(id),
  CONSTRAINT barter_requests_offered_user_skill_id_fkey FOREIGN KEY (offered_user_skill_id) REFERENCES public.user_skills(id),
  CONSTRAINT barter_requests_wanted_user_skill_id_fkey FOREIGN KEY (wanted_user_skill_id) REFERENCES public.user_skills(id)
);
CREATE TABLE public.learning_sessions (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  barter_request_id uuid NOT NULL,
  scheduled_at timestamp with time zone NOT NULL,
  duration_minutes integer NOT NULL CHECK (duration_minutes > 0),
  meeting_provider character varying NOT NULL DEFAULT 'JITSI'::character varying CHECK (meeting_provider::text = ANY (ARRAY['JITSI'::character varying, 'EXTERNAL_LINK'::character varying]::text[])),
  meeting_room character varying UNIQUE,
  meeting_url text,
  status character varying NOT NULL DEFAULT 'SCHEDULED'::character varying CHECK (status::text = ANY (ARRAY['SCHEDULED'::character varying, 'ONGOING'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying]::text[])),
  completed_at timestamp with time zone,
  CONSTRAINT learning_sessions_pkey PRIMARY KEY (id),
  CONSTRAINT learning_sessions_barter_request_id_fkey FOREIGN KEY (barter_request_id) REFERENCES public.barter_requests(id)
);
CREATE TABLE public.reviews (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  learning_session_id uuid NOT NULL,
  reviewer_id uuid NOT NULL,
  reviewee_id uuid NOT NULL,
  rating smallint NOT NULL CHECK (rating >= 1 AND rating <= 5),
  comment text,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT reviews_pkey PRIMARY KEY (id),
  CONSTRAINT reviews_learning_session_id_fkey FOREIGN KEY (learning_session_id) REFERENCES public.learning_sessions(id),
  CONSTRAINT reviews_reviewer_id_fkey FOREIGN KEY (reviewer_id) REFERENCES public.users(id),
  CONSTRAINT reviews_reviewee_id_fkey FOREIGN KEY (reviewee_id) REFERENCES public.users(id)
);
CREATE TABLE public.notifications (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL,
  title character varying NOT NULL,
  body text NOT NULL,
  type character varying NOT NULL,
  is_read boolean NOT NULL DEFAULT false,
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT notifications_pkey PRIMARY KEY (id),
  CONSTRAINT notifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id)
);
CREATE TABLE public.reports (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  reporter_id uuid NOT NULL,
  target_type character varying NOT NULL CHECK (target_type::text = ANY (ARRAY['PORTFOLIO'::character varying, 'USER'::character varying]::text[])),
  target_id uuid NOT NULL,
  reason text NOT NULL,
  status character varying NOT NULL DEFAULT 'OPEN'::character varying CHECK (status::text = ANY (ARRAY['OPEN'::character varying, 'REVIEWING'::character varying, 'RESOLVED'::character varying, 'DISMISSED'::character varying]::text[])),
  created_at timestamp with time zone NOT NULL DEFAULT now(),
  CONSTRAINT reports_pkey PRIMARY KEY (id),
  CONSTRAINT reports_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES public.users(id)
);
CREATE TABLE public.flyway_schema_history (
  installed_rank integer NOT NULL,
  version character varying,
  description character varying NOT NULL,
  type character varying NOT NULL,
  script character varying NOT NULL,
  checksum integer,
  installed_by character varying NOT NULL,
  installed_on timestamp without time zone NOT NULL DEFAULT now(),
  execution_time integer NOT NULL,
  success boolean NOT NULL,
  CONSTRAINT flyway_schema_history_pkey PRIMARY KEY (installed_rank)
);