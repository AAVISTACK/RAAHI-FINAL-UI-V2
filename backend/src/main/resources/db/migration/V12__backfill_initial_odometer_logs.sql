-- Any vehicle that has no odometer history gets one row holding the odometer value that is
-- actually stored on the vehicle, stamped with the time that value was recorded (falling back
-- to when the vehicle was created). Nothing is invented: it is the vehicle's own stored reading.
INSERT INTO vehicle_odometer_logs (vehicle_id, odometer_km, recorded_at)
SELECT v.id, v.odometer_km, COALESCE(v.odometer_updated_at, v.created_at)
FROM vehicles v
WHERE NOT EXISTS (SELECT 1 FROM vehicle_odometer_logs l WHERE l.vehicle_id = v.id);
