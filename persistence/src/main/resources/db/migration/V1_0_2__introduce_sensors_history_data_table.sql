CREATE TABLE smart_home.sensor_history_data (
    sensor_uuid UUID NOT NULL,
    time TIMESTAMP NOT NULL,
    data_type TEXT NOT NULL,
    value DECIMAL NOT NULL,

    CONSTRAINT pk_sensor_history_data PRIMARY KEY (sensor_uuid, time, data_type),

    CONSTRAINT fk_device
        FOREIGN KEY (sensor_uuid)
        REFERENCES smart_home.device(uuid)
        ON DELETE CASCADE
);

SELECT create_hypertable('smart_home.sensor_history_data', 'time');
