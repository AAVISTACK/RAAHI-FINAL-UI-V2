CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid    VARCHAR(128) UNIQUE NOT NULL,
    phone           VARCHAR(15)  UNIQUE,
    email           VARCHAR(255),
    name            VARCHAR(100),
    profile_photo   TEXT,
    vehicle_type    VARCHAR(50),
    vehicle_reg     VARCHAR(20),
    role            VARCHAR(20) NOT NULL DEFAULT 'DRIVER' CHECK (role IN ('DRIVER','MECHANIC','HELPER','ADMIN')),
    rating_avg      DECIMAL(3,2) DEFAULT 0,
    total_helps     INT DEFAULT 0,
    wallet_balance  DECIMAL(10,2) DEFAULT 0,
    language        VARCHAR(10) DEFAULT 'hi',
    is_verified     BOOLEAN DEFAULT FALSE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED')),
    fcm_token       TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    last_seen_at    TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE jobs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id    UUID NOT NULL REFERENCES users(id),
    helper_id       UUID REFERENCES users(id),
    problem_type    VARCHAR(50) NOT NULL,
    problem_desc    TEXT,
    req_lat         DOUBLE PRECISION NOT NULL,
    req_lng         DOUBLE PRECISION NOT NULL,
    highway_name    VARCHAR(120),
    reward_amount   DECIMAL(10,2) DEFAULT 0,
    helper_otp      VARCHAR(6),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (status IN ('PENDING','MATCHED','IN_PROGRESS','COMPLETED','CANCELLED')),
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW(),
    expires_at      TIMESTAMPTZ
);

CREATE INDEX idx_jobs_status_expiry ON jobs(status, expires_at);
CREATE INDEX idx_jobs_requester ON jobs(requester_id);
CREATE INDEX idx_jobs_helper ON jobs(helper_id);
CREATE INDEX idx_users_firebase_uid ON users(firebase_uid);

-- Remaining tables (mechanic_profiles, subscriptions, sos_events, ai_sessions/messages,
-- daily_streaks, highway_alerts, selfie_verifications, aadhaar_verifications) land in
-- V2__... as each corresponding controller is built — see STATUS.md.
