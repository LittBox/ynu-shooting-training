-- PostgreSQL 14+：设备排班迁移为单教室排班，保留预约、训练和成绩。
-- 停止旧后端并备份后执行整个文件；完成后启动新版后端。
-- 同一教员同一天同一时段的旧设备排班合并，保留最小 ID；不会把排班自动标为到岗。
BEGIN;
SET LOCAL search_path TO "ynu-shooting";
LOCK TABLE admin_schedules IN ACCESS EXCLUSIVE MODE;

ALTER TABLE admin_schedules ADD COLUMN IF NOT EXISTS arrived_at TIMESTAMP(6);
ALTER TABLE admin_schedules ADD COLUMN IF NOT EXISTS departed_at TIMESTAMP(6);

DO $$
DECLARE
    admin_column TEXT;
BEGIN
    -- 兼容外键命名规范化前后的数据库，可在最新 schema 上重复执行。
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema='ynu-shooting' AND table_name='admin_schedules' AND column_name='admin_user_id') THEN
        admin_column := 'admin_user_id';
    ELSE
        admin_column := 'admin_id';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = 'ynu-shooting' AND table_name = 'admin_schedules' AND column_name = 'device_id') THEN
        EXECUTE format('DELETE FROM admin_schedules redundant USING admin_schedules kept
                        WHERE redundant.%I = kept.%I AND redundant.slot_date = kept.slot_date
                          AND redundant.slot_id = kept.slot_id AND redundant.id > kept.id', admin_column, admin_column);
        ALTER TABLE admin_schedules DROP COLUMN device_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = '"ynu-shooting".admin_schedules'::regclass
                   AND conname = 'uk_admin_schedule_slot') THEN
        EXECUTE format('ALTER TABLE admin_schedules ADD CONSTRAINT uk_admin_schedule_slot UNIQUE (%I, slot_date, slot_id)', admin_column);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = '"ynu-shooting".admin_schedules'::regclass
                   AND conname = 'ck_admin_schedule_attendance') THEN
        ALTER TABLE admin_schedules ADD CONSTRAINT ck_admin_schedule_attendance CHECK (
            departed_at IS NULL OR (arrived_at IS NOT NULL AND departed_at >= arrived_at)
        );
    END IF;
END $$;

COMMIT;
