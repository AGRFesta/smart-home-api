CREATE TABLE smart_home.temperature_setting (
    uuid UUID PRIMARY KEY,
    area_uuid UUID UNIQUE NOT NULL,
    default_temperature DOUBLE PRECISION NOT NULL,

    CONSTRAINT fk_temperature_area
        FOREIGN KEY (area_uuid) REFERENCES smart_home.area(uuid)
        ON DELETE CASCADE
);

CREATE TABLE smart_home.temperature_interval (
    uuid UUID PRIMARY KEY,
    setting_uuid UUID NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    temperature DOUBLE PRECISION NOT NULL,

    CONSTRAINT fk_interval_setting
        FOREIGN KEY (setting_uuid) REFERENCES smart_home.temperature_setting(uuid)
        ON DELETE CASCADE
);
