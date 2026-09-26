-- Stop the old backend before applying: new writes require per-round mode.
BEGIN;
SET LOCAL search_path TO "ynu-shooting";
ALTER TABLE score_attempts ADD COLUMN IF NOT EXISTS mode VARCHAR(255);
UPDATE score_attempts a SET mode=t.mode FROM training_sessions t
WHERE a.training_session_id=t.id AND a.mode IS NULL;
ALTER TABLE score_attempts ALTER COLUMN mode SET NOT NULL;
DO $$
DECLARE item RECORD; session_column SMALLINT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='score_attempts'::regclass AND conname='ck_score_attempt_mode') THEN
        ALTER TABLE score_attempts ADD CONSTRAINT ck_score_attempt_mode CHECK (mode IN ('final_', 'qualifying'));
    END IF;
    SELECT attnum INTO STRICT session_column FROM pg_attribute WHERE attrelid='scores'::regclass AND attname='training_session_id';
    -- Names can differ between Hibernate-generated and manually-created databases.
    FOR item IN SELECT conname FROM pg_constraint WHERE conrelid='scores'::regclass AND contype='u' AND conkey=ARRAY[session_column] LOOP
        EXECUTE format('ALTER TABLE scores DROP CONSTRAINT %I',item.conname);
    END LOOP;
    -- Also support a standalone unique index on this single column.
    FOR item IN SELECT indexrelid::regclass AS index_name FROM pg_index
        WHERE indrelid='scores'::regclass AND indisunique AND NOT indisprimary
          AND indnkeyatts=1 AND indkey[0]=session_column
          AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conindid=indexrelid)
    LOOP
        EXECUTE format('DROP INDEX %s',item.index_name);
    END LOOP;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='scores'::regclass AND conname='uk_score_session_mode') THEN
        ALTER TABLE scores ADD CONSTRAINT uk_score_session_mode UNIQUE (training_session_id,mode);
    END IF;
END $$;
COMMIT;
