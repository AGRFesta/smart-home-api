ALTER TABLE smart_home.device
    ADD CONSTRAINT uq_device_provider_identity UNIQUE (provider, provider_id);
