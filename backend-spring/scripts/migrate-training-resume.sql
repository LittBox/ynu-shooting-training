BEGIN;
SET LOCAL search_path TO "ynu-shooting";
ALTER TABLE training_sessions ADD COLUMN IF NOT EXISTS resume_started_at TIMESTAMP(6);
COMMIT;
