CREATE TABLE daily_streaks (
    user_id         UUID PRIMARY KEY REFERENCES users(id),
    current_streak  INT NOT NULL DEFAULT 0,
    longest_streak  INT NOT NULL DEFAULT 0,
    total_checkins  INT NOT NULL DEFAULT 0,
    last_checkin    DATE,
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE highway_alerts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id),
    type        VARCHAR(30) NOT NULL,
    message     TEXT NOT NULL,
    location    VARCHAR(255),
    lat         DOUBLE PRECISION,
    lng         DOUBLE PRECISION,
    upvotes     INT NOT NULL DEFAULT 0,
    downvotes   INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ DEFAULT NOW(),
    expires_at  TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '12 hours')
);
CREATE INDEX idx_highway_alerts_expires ON highway_alerts(expires_at);

-- The old Node backend's POST /daily/alerts/:id/vote had NO duplicate-vote prevention at
-- all — any user could call it repeatedly and inflate the count arbitrarily. This table plus
-- a unique constraint is the fix: one vote per (alert, user), enforced at the DB level.
CREATE TABLE highway_alert_votes (
    alert_id  UUID NOT NULL REFERENCES highway_alerts(id),
    user_id   UUID NOT NULL REFERENCES users(id),
    vote      VARCHAR(4) NOT NULL CHECK (vote IN ('up','down')),
    voted_at  TIMESTAMPTZ DEFAULT NOW(),
    PRIMARY KEY (alert_id, user_id)
);

-- Real curated content, not a live feed — same idea as the old Node backend's hardcoded
-- _getDailyTip() rotation, just moved into the DB so it's manageable without a redeploy.
CREATE TABLE daily_tips (
    id     SERIAL PRIMARY KEY,
    title  VARCHAR(120) NOT NULL,
    body   TEXT NOT NULL
);
INSERT INTO daily_tips (title, body) VALUES
    ('Tyre Pressure', 'Check tyre pressure every 15 days. Correct pressure saves about 3% fuel.'),
    ('Engine Oil', 'Check your engine oil color regularly. Dark/black oil means it needs changing.'),
    ('Battery Terminals', 'White powder on battery terminals means corrosion — clean it or the car may not start.'),
    ('Coolant Level', 'Never let coolant run low — it is the most common cause of engine overheating.'),
    ('Brake Fluid', 'Dark brake fluid should be changed — fresh fluid means better brake response.'),
    ('Wiper Blades', 'Replace wiper blades if they streak or squeak — worn rubber reduces visibility in rain.'),
    ('AC Filter', 'A clogged cabin air filter reduces AC cooling and increases fuel use — check it seasonally.');
