-- v1.0.0 -> coach historical entry. Stop the application and back up first.
-- Once historical rows exist, an old JAR is no longer compatible.
BEGIN;
SET LOCAL search_path TO "ynu-shooting";
ALTER TABLE training_sessions ALTER COLUMN booking_id DROP NOT NULL;
ALTER TABLE training_sessions ALTER COLUMN device_id DROP NOT NULL;
ALTER TABLE training_sessions ADD COLUMN IF NOT EXISTS historical_weapon VARCHAR(255);
ALTER TABLE training_sessions ADD COLUMN IF NOT EXISTS recorded_by_user_id BIGINT;
ALTER TABLE training_sessions ADD COLUMN IF NOT EXISTS history_request_key VARCHAR(64);
ALTER TABLE training_sessions ADD COLUMN IF NOT EXISTS history_request_hash VARCHAR(64);
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='training_sessions'::regclass AND conname='fk_training_sessions__recorded_by_user_id__users') THEN
        ALTER TABLE training_sessions ADD CONSTRAINT fk_training_sessions__recorded_by_user_id__users FOREIGN KEY (recorded_by_user_id) REFERENCES users(id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='training_sessions'::regclass AND conname='uk_training_history_request') THEN
        ALTER TABLE training_sessions ADD CONSTRAINT uk_training_history_request UNIQUE (history_request_key);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='training_sessions'::regclass AND conname='ck_training_history_source') THEN
        ALTER TABLE training_sessions ADD CONSTRAINT ck_training_history_source CHECK (
            (historical_weapon IS NULL AND booking_id IS NOT NULL AND device_id IS NOT NULL AND recorded_by_user_id IS NULL AND history_request_key IS NULL AND history_request_hash IS NULL)
            OR (historical_weapon IS NOT NULL AND historical_weapon IN ('pistol','rifle') AND booking_id IS NULL AND device_id IS NULL
                AND recorded_by_user_id IS NOT NULL AND history_request_key IS NOT NULL AND history_request_hash IS NOT NULL
                AND started_at IS NOT NULL AND ended_at IS NOT NULL AND ended_at > started_at AND resume_started_at IS NULL));
    END IF;
END $$;
COMMENT ON COLUMN training_sessions.historical_weapon IS '历史补录枪种；普通预约训练为空';
COMMENT ON COLUMN training_sessions.recorded_by_user_id IS '历史补录教员；外键 -> users.id';
COMMENT ON COLUMN training_sessions.history_request_key IS '历史补录幂等请求键';
COMMENT ON COLUMN training_sessions.history_request_hash IS '原始补录请求摘要，用于安全重试';
COMMIT;
