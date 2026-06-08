ALTER TABLE garmin_daily_health_metrics
    ADD COLUMN weight_kg DECIMAL(5, 2);

ALTER TABLE sync_logs
    ADD COLUMN weights_count INT DEFAULT 0;
