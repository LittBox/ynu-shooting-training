-- PostgreSQL 14+：统一外键列名、约束名与字段注释，不修改业务数据。
-- 先完成 migrate-venue-duty.sql；停止旧后端、备份后执行整个文件，再重启新版后端。
-- 可重复执行；RENAME COLUMN 会保留原值、索引、唯一约束及外键关系。
BEGIN;
SET LOCAL search_path TO "ynu-shooting";
SET LOCAL lock_timeout = '5s';
LOCK TABLE admin_schedules, audit_log, bookings, cancellation_log, no_show_records, profiles, scores, training_sessions, user_availability IN ACCESS EXCLUSIVE MODE;

DO $$
DECLARE
    item RECORD;
    old_exists BOOLEAN;
    new_exists BOOLEAN;
    existing_constraint TEXT;
BEGIN
    FOR item IN SELECT * FROM (VALUES
            ('admin_schedules', 'admin_id', 'admin_user_id'),
            ('audit_log', 'admin_id', 'admin_user_id'),
            ('scores', 'recorded_by', 'recorded_by_user_id'),
            ('scores', 'session_id', 'training_session_id')
    ) AS renames(table_name, old_name, new_name)
    LOOP
        SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='ynu-shooting'
                       AND table_name=item.table_name AND column_name=item.old_name) INTO old_exists;
        SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='ynu-shooting'
                       AND table_name=item.table_name AND column_name=item.new_name) INTO new_exists;
        IF old_exists AND new_exists THEN
            RAISE EXCEPTION '表 % 同时存在旧列 % 和新列 %，请先核对数据', item.table_name, item.old_name, item.new_name;
        ELSIF old_exists THEN
            EXECUTE format('ALTER TABLE %I.%I RENAME COLUMN %I TO %I', 'ynu-shooting', item.table_name, item.old_name, item.new_name);
        ELSIF NOT new_exists THEN
            RAISE EXCEPTION '表 % 缺少预期外键列 %', item.table_name, item.old_name;
        END IF;
    END LOOP;

    FOR item IN SELECT * FROM (VALUES
            ('admin_schedules', 'admin_user_id', 'users', 'fk_admin_schedules__admin_user_id__users'),
            ('audit_log', 'admin_user_id', 'users', 'fk_audit_log__admin_user_id__users'),
            ('bookings', 'device_id', 'devices', 'fk_bookings__device_id__devices'),
            ('bookings', 'user_id', 'users', 'fk_bookings__user_id__users'),
            ('cancellation_log', 'booking_id', 'bookings', 'fk_cancellation_log__booking_id__bookings'),
            ('cancellation_log', 'user_id', 'users', 'fk_cancellation_log__user_id__users'),
            ('no_show_records', 'booking_id', 'bookings', 'fk_no_show_records__booking_id__bookings'),
            ('no_show_records', 'user_id', 'users', 'fk_no_show_records__user_id__users'),
            ('profiles', 'user_id', 'users', 'fk_profiles__user_id__users'),
            ('scores', 'recorded_by_user_id', 'users', 'fk_scores__recorded_by_user_id__users'),
            ('scores', 'training_session_id', 'training_sessions', 'fk_scores__training_session_id__training_sessions'),
            ('training_sessions', 'booking_id', 'bookings', 'fk_training_sessions__booking_id__bookings'),
            ('training_sessions', 'device_id', 'devices', 'fk_training_sessions__device_id__devices'),
            ('training_sessions', 'user_id', 'users', 'fk_training_sessions__user_id__users'),
            ('user_availability', 'user_id', 'users', 'fk_user_availability__user_id__users')
    ) AS links(table_name, column_name, target_table, constraint_name)
    LOOP
        SELECT c.conname INTO STRICT existing_constraint
        FROM pg_constraint c
        JOIN pg_class source ON source.oid=c.conrelid
        JOIN pg_namespace ns ON ns.oid=source.relnamespace
        JOIN pg_attribute a ON a.attrelid=source.oid AND a.attnum=c.conkey[1]
        JOIN pg_class target ON target.oid=c.confrelid
        JOIN pg_attribute pa ON pa.attrelid=target.oid AND pa.attnum=c.confkey[1]
        WHERE c.contype='f' AND ns.nspname='ynu-shooting'
          AND source.relname=item.table_name AND a.attname=item.column_name
          AND target.relnamespace=ns.oid AND target.relname=item.target_table AND pa.attname='id'
          AND cardinality(c.conkey)=1 AND cardinality(c.confkey)=1;
        IF existing_constraint <> item.constraint_name THEN
            EXECUTE format('ALTER TABLE %I.%I RENAME CONSTRAINT %I TO %I',
                           'ynu-shooting', item.table_name, existing_constraint, item.constraint_name);
        END IF;
    END LOOP;
END $$;

COMMENT ON COLUMN admin_schedules.admin_user_id IS '外键 -> users.id；值班教员用户';
COMMENT ON COLUMN audit_log.admin_user_id IS '外键 -> users.id；操作管理员用户';
COMMENT ON COLUMN bookings.device_id IS '外键 -> devices.id；关联记录';
COMMENT ON COLUMN bookings.user_id IS '外键 -> users.id；关联记录';
COMMENT ON COLUMN cancellation_log.booking_id IS '外键 -> bookings.id；关联记录';
COMMENT ON COLUMN cancellation_log.user_id IS '外键 -> users.id；关联记录';
COMMENT ON COLUMN no_show_records.booking_id IS '外键 -> bookings.id；关联记录';
COMMENT ON COLUMN no_show_records.user_id IS '外键 -> users.id；关联记录';
COMMENT ON COLUMN profiles.user_id IS '外键 -> users.id；所属用户；同时为本表主键（一对一档案）';
COMMENT ON COLUMN scores.recorded_by_user_id IS '外键 -> users.id；录分人用户';
COMMENT ON COLUMN scores.training_session_id IS '外键 -> training_sessions.id；对应训练记录';
COMMENT ON COLUMN training_sessions.booking_id IS '外键 -> bookings.id；关联记录';
COMMENT ON COLUMN training_sessions.device_id IS '外键 -> devices.id；关联记录';
COMMENT ON COLUMN training_sessions.user_id IS '外键 -> users.id；关联记录';
COMMENT ON COLUMN user_availability.user_id IS '外键 -> users.id；关联记录';
COMMENT ON COLUMN audit_log.target_user_id IS '逻辑引用 -> users.id；历史操作目标标识，不设数据库外键约束';
COMMENT ON COLUMN admin_schedules.slot_id IS '时间片代码 S1-S6，对应代码中的 Slot 枚举；不是数据库外键';
COMMENT ON COLUMN bookings.slot_id IS '时间片代码 S1-S6，对应代码中的 Slot 枚举；不是数据库外键';
COMMENT ON COLUMN user_availability.slot_id IS '时间片代码 S1-S6，对应代码中的 Slot 枚举；不是数据库外键';

COMMIT;
