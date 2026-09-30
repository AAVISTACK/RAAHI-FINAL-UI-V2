CREATE TABLE verification_documents (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id),
    doc_type     VARCHAR(20) NOT NULL CHECK (doc_type IN ('AADHAAR_FRONT','AADHAAR_BACK','SELFIE')),
    content      BYTEA NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    -- AI tamper/edit screening result on the submitted IMAGE only — never a claim about
    -- whether the Aadhaar itself is officially genuine (no UIDAI/eKYC integration exists).
    ai_status    VARCHAR(30) NOT NULL DEFAULT 'UNAVAILABLE'
                     CHECK (ai_status IN ('UNAVAILABLE','LIKELY_UNMODIFIED','POTENTIALLY_MANIPULATED','SUSPICIOUS')),
    ai_note      TEXT,
    created_at   TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_verification_documents_user ON verification_documents(user_id);

CREATE TABLE helper_applications (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID NOT NULL UNIQUE REFERENCES users(id),
    email            VARCHAR(255),
    aadhaar_front_id UUID REFERENCES verification_documents(id),
    aadhaar_back_id  UUID REFERENCES verification_documents(id),
    selfie_id        UUID REFERENCES verification_documents(id),
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    rejection_reason TEXT,
    submitted_at     TIMESTAMPTZ DEFAULT NOW(),
    reviewed_at      TIMESTAMPTZ,
    reviewed_by      UUID REFERENCES users(id)
);
CREATE INDEX idx_helper_applications_status ON helper_applications(status);
