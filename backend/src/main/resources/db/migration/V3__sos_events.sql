CREATE TABLE sos_events (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id),
    lat         DOUBLE PRECISION NOT NULL,
    lng         DOUBLE PRECISION NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE','RESOLVED')),
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

-- Old Node backend's GET /sos/active returned every active SOS event (with the triggering
-- user's phone + vehicle reg) to ANY authenticated caller, with no radius filter and no role
-- check. That is fixed at the query/controller level (see MechanicController-style nearby
-- query + role gate in SosController) — this index just supports that lookup pattern.
CREATE INDEX idx_sos_events_status_created ON sos_events(status, created_at);
CREATE INDEX idx_sos_events_user ON sos_events(user_id);
