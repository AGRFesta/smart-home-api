-- Step 1: add area_uuid to temperature_interval, populated via join with temperature_setting
ALTER TABLE smart_home.temperature_interval
    ADD COLUMN area_uuid UUID;

UPDATE smart_home.temperature_interval ti
SET area_uuid = ts.area_uuid
FROM smart_home.temperature_setting ts
WHERE ti.setting_uuid = ts.uuid;

ALTER TABLE smart_home.temperature_interval
    ALTER COLUMN area_uuid SET NOT NULL;

-- Step 2: drop old FK and column on temperature_interval
ALTER TABLE smart_home.temperature_interval
    DROP CONSTRAINT fk_interval_setting,
    DROP COLUMN setting_uuid;

-- Step 3: drop surrogate PK and UNIQUE constraint from temperature_setting, then promote area_uuid to PK
ALTER TABLE smart_home.temperature_setting
    DROP CONSTRAINT temperature_setting_pkey,
    DROP CONSTRAINT temperature_setting_area_uuid_key,
    DROP COLUMN uuid;

ALTER TABLE smart_home.temperature_setting
    ADD PRIMARY KEY (area_uuid);

-- Step 4: re-add FK on temperature_interval pointing to the new PK
ALTER TABLE smart_home.temperature_interval
    ADD CONSTRAINT fk_interval_setting
        FOREIGN KEY (area_uuid) REFERENCES smart_home.temperature_setting(area_uuid)
            ON DELETE CASCADE;
