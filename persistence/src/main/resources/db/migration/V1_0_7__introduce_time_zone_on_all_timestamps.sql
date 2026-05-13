SET TIME ZONE 'UTC';

ALTER TABLE smart_home.sensor_history_data DROP CONSTRAINT IF EXISTS pk_sensor_history_data;
DROP INDEX IF EXISTS smart_home.sensor_history_data_time_idx;

ALTER TABLE smart_home.sensor_history_data
  ALTER COLUMN time
  TYPE TIMESTAMPTZ
  USING time AT TIME ZONE 'UTC';

-- Recreate the Primary Key.
ALTER TABLE smart_home.sensor_history_data ADD CONSTRAINT pk_sensor_history_data PRIMARY KEY (sensor_uuid, time, data_type);

-- Recreate the index on the hypertable
CREATE INDEX IF NOT EXISTS sensor_history_data_time_idx ON smart_home.sensor_history_data (time DESC);

ALTER TABLE smart_home.device
  ALTER COLUMN created_on
  TYPE TIMESTAMPTZ
  USING created_on AT TIME ZONE 'UTC',
  ALTER COLUMN updated_on
  TYPE TIMESTAMPTZ
  USING updated_on AT TIME ZONE 'UTC';

ALTER TABLE smart_home.area
  ALTER COLUMN created_on
  TYPE TIMESTAMPTZ
  USING created_on AT TIME ZONE 'UTC',
  ALTER COLUMN updated_on
  TYPE TIMESTAMPTZ
  USING updated_on AT TIME ZONE 'UTC';

ALTER TABLE smart_home.sensor_assignment
  ALTER COLUMN connected_on
  TYPE TIMESTAMPTZ
  USING connected_on AT TIME ZONE 'UTC',
  ALTER COLUMN disconnected_on
  TYPE TIMESTAMPTZ
  USING disconnected_on AT TIME ZONE 'UTC';

ALTER TABLE smart_home.cache
  ALTER COLUMN created_at
  TYPE TIMESTAMPTZ
  USING created_at AT TIME ZONE 'UTC',
  ALTER COLUMN expires_at
  TYPE TIMESTAMPTZ
  USING expires_at AT TIME ZONE 'UTC';
