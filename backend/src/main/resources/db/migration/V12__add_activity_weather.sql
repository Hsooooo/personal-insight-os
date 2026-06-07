ALTER TABLE activities
    ADD COLUMN weather_temperature NUMERIC(4,1),
    ADD COLUMN weather_humidity INTEGER,
    ADD COLUMN weather_wind_speed NUMERIC(4,1),
    ADD COLUMN weather_condition VARCHAR(50),
    ADD COLUMN weather_raw JSONB;
