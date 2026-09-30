CREATE TABLE fuel_rates (
    state      VARCHAR(50) PRIMARY KEY,
    petrol     NUMERIC(6,2) NOT NULL,
    diesel     NUMERIC(6,2) NOT NULL,
    cng        NUMERIC(6,2),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- Seed with the same indicative starting figures the old Node backend hardcoded — the
-- difference is `updated_at` here reflects when this row was actually last written, not a
-- fabricated "today" on every request. Whoever operates this needs an admin process (or the
-- PUT below) to actually refresh these periodically; until then, the timestamp will honestly
-- show its age instead of lying about it.
INSERT INTO fuel_rates (state, petrol, diesel, cng, updated_at) VALUES
    ('Punjab', 94.24, 82.39, 91.50, NOW()),
    ('Haryana', 94.63, 82.56, 89.20, NOW()),
    ('Delhi', 94.77, 87.67, 74.09, NOW()),
    ('Maharashtra', 104.21, 90.48, 73.00, NOW()),
    ('Karnataka', 101.94, 87.89, 78.00, NOW()),
    ('Gujarat', 94.38, 82.33, 66.00, NOW()),
    ('UP', 94.69, 87.91, 90.00, NOW()),
    ('Rajasthan', 104.72, 90.21, 80.00, NOW());

-- Amazon affiliate-link catalog — matches the actual Flutter/reference product (a Google
-- Sheet CSV of ASIN + price + rating that the app renders and deep-links to Amazon; no
-- in-app cart, checkout, or payment ever existed for Shop). This table is that same shape,
-- just in Postgres instead of a published Google Sheet, with an admin able to edit rows
-- directly rather than via a spreadsheet CSV import job.
CREATE TABLE shop_products (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200) NOT NULL,
    category        VARCHAR(30) NOT NULL,
    price           NUMERIC(8,2) NOT NULL,
    discount_price  NUMERIC(8,2),
    asin            VARCHAR(20) NOT NULL,
    brand           VARCHAR(100),
    rating          NUMERIC(2,1),
    review_count    INT,
    description     TEXT,
    active          BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO shop_products (name, category, price, discount_price, asin, brand, rating, review_count, description, active) VALUES
    ('3M Car Polish', 'cleaning', 599, 449, 'B08XYZ1234', '3M', 4.3, 2847, 'Deep shine polish', TRUE),
    ('Puncture Repair Kit', 'tyres', 399, 299, 'B09ABC5678', 'Maruti', 4.1, 5621, 'Emergency tyre puncture repair kit', TRUE),
    ('Car Vacuum Cleaner', 'cleaning', 1299, 899, 'B07DEF9012', 'Eureka Forbes', 4.0, 8934, '120W wet & dry vacuum', TRUE);
