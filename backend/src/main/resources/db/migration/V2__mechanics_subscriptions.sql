CREATE TABLE mechanic_profiles (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    shop_name           VARCHAR(100),
    specializations     TEXT,
    is_available        BOOLEAN DEFAULT FALSE,
    current_lat         DOUBLE PRECISION,
    current_lng         DOUBLE PRECISION,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                            CHECK (verification_status IN ('PENDING','APPROVED','REJECTED'))
);

CREATE INDEX idx_mechanic_profiles_location ON mechanic_profiles(current_lat, current_lng)
    WHERE verification_status = 'APPROVED';

CREATE TABLE subscriptions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tier        VARCHAR(20) NOT NULL DEFAULT 'NONE' CHECK (tier IN ('NONE','BASIC','PRO')),
    status      VARCHAR(20) NOT NULL DEFAULT 'INACTIVE' CHECK (status IN ('ACTIVE','INACTIVE')),
    expires_at  TIMESTAMPTZ
);
