ALTER TABLE smart_home.association
RENAME TO sensor_assignment;

CREATE TABLE IF NOT EXISTS smart_home.actuator_assignment (
    uuid UUID PRIMARY KEY,
    area_uuid UUID NOT NULL,
    device_uuid UUID NOT NULL,

    CONSTRAINT fk_actuator_area
        FOREIGN KEY (area_uuid)
        REFERENCES smart_home.area(uuid)
        ON DELETE CASCADE,

    CONSTRAINT fk_actuator_device
        FOREIGN KEY (device_uuid)
        REFERENCES smart_home.device(uuid)
        ON DELETE CASCADE,

    UNIQUE (area_uuid, device_uuid)
);
