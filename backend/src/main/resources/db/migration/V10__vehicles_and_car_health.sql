-- Real, user-entered vehicle data + service history for the Car Health feature.
-- Replaces the old approach (no vehicle schema at all — Car Health was explicitly excluded,
-- see docs/STATUS.md) with a real, minimal model. One vehicle per authenticated user.

CREATE TABLE vehicles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,

    brand VARCHAR(60) NOT NULL,
    model VARCHAR(80) NOT NULL,
    variant VARCHAR(80),
    model_year INT NOT NULL,
    fuel_type VARCHAR(20) NOT NULL CHECK (fuel_type IN ('PETROL','DIESEL','CNG','ELECTRIC','HYBRID','LPG')),
    registration_number VARCHAR(20) NOT NULL,

    odometer_km INT NOT NULL DEFAULT 0 CHECK (odometer_km >= 0),
    odometer_updated_at TIMESTAMPTZ,

    -- Optional fields — all nullable by design; a real "no data" is never a fabricated 0/false.
    last_service_date DATE,
    last_service_odometer_km INT,
    insurance_expiry DATE,
    puc_expiry DATE,
    tyre_replaced_date DATE,
    tyre_replaced_odometer_km INT,
    battery_replaced_date DATE,
    battery_replaced_odometer_km INT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE vehicle_service_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,

    service_date DATE NOT NULL,
    odometer_km INT NOT NULL CHECK (odometer_km >= 0),
    service_type VARCHAR(60) NOT NULL,
    notes TEXT,
    cost NUMERIC(10, 2),
    workshop_name VARCHAR(120),
    parts_replaced TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Append-only audit trail of odometer updates — "every update must be persisted", not just
-- overwritten on vehicles.odometer_km.
CREATE TABLE vehicle_odometer_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    odometer_km INT NOT NULL CHECK (odometer_km >= 0),
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vehicle_service_records_vehicle_id ON vehicle_service_records(vehicle_id);
CREATE INDEX idx_vehicle_odometer_logs_vehicle_id ON vehicle_odometer_logs(vehicle_id);
