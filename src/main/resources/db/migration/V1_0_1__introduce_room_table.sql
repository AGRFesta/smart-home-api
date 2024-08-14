CREATE TABLE IF NOT EXISTS smart_home.room (
    uuid UUID PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    created_on TIMESTAMP NOT NULL,
    updated_on TIMESTAMP
);

CREATE TABLE IF NOT EXISTS smart_home.room_device (
    uuid UUID PRIMARY KEY,
    room_uuid UUID NOT NULL,
    device_uuid UUID NOT NULL,
    connected_on TIMESTAMP NOT NULL,
    disconnected_on TIMESTAMP,

    CONSTRAINT fk_room
        FOREIGN KEY (room_uuid)
        REFERENCES smart_home.room(uuid)
        ON DELETE CASCADE,

    CONSTRAINT fk_device
        FOREIGN KEY (device_uuid)
        REFERENCES smart_home.device(uuid)
        ON DELETE CASCADE
);
