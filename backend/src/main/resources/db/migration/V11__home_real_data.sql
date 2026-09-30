-- Real data sources for the Home screen (replaces hardcoded 142 km / 2 helps / ₹840 / "1 alert").

-- User-entered fuel fill-ups. Scoped by user_id (from the JWT, never a client-supplied id).
CREATE TABLE fuel_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vehicle_id      UUID REFERENCES vehicles(id) ON DELETE SET NULL,
    filled_at       TIMESTAMPTZ NOT NULL,
    litres          NUMERIC(8,2) CHECK (litres > 0),
    price_per_litre NUMERIC(7,2) CHECK (price_per_litre > 0),
    total_cost      NUMERIC(10,2) NOT NULL CHECK (total_cost > 0),
    odometer_km     INT CHECK (odometer_km >= 0),
    fuel_type       VARCHAR(20),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_fuel_logs_user_filled ON fuel_logs(user_id, filled_at DESC);

-- In-app notification inbox. Every NotificationService.send() persists a row here, so the
-- unread badge on Home reflects notifications that were really sent to this user.
CREATE TABLE user_notifications (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title      VARCHAR(200) NOT NULL,
    body       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at    TIMESTAMPTZ
);
CREATE INDEX idx_user_notifications_user ON user_notifications(user_id, read_at, created_at DESC);

-- Where a fuel rate came from, so the UI never presents a seed value as "live".
ALTER TABLE fuel_rates ADD COLUMN source VARCHAR(30) NOT NULL DEFAULT 'ADMIN_MANUAL';
UPDATE fuel_rates SET source = 'SEED_INDICATIVE';
