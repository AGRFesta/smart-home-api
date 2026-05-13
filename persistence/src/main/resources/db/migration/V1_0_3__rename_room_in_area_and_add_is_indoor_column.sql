-- Rename the table from 'room' to 'area'
ALTER TABLE smart_home.room RENAME TO area;

-- Add the new column 'is_indoor' of type BOOLEAN with a default value of 'false'
-- (using a default value avoids errors with the NOT NULL constraint on existing records)
ALTER TABLE smart_home.area
ADD COLUMN is_indoor BOOLEAN NOT NULL DEFAULT true;

-- Rename the unique constraint on 'name' to reflect the new table name
ALTER TABLE smart_home.area
RENAME CONSTRAINT room_name_key TO area_name_key;

-- Rename the column 'room_uuid' to 'area_uuid'
ALTER TABLE smart_home.association
RENAME COLUMN room_uuid TO area_uuid;

-- Drop the existing foreign key constraint
ALTER TABLE smart_home.association
DROP CONSTRAINT fk_room;

-- Add a new foreign key constraint for 'area_uuid' referencing 'smart_home.area(uuid)'
ALTER TABLE smart_home.association
ADD CONSTRAINT fk_area
    FOREIGN KEY (area_uuid)
    REFERENCES smart_home.area(uuid)
    ON DELETE CASCADE;
