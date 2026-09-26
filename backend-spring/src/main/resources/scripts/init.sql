-- ============================================================
--  YNU 射击训练预约系统  数据初始化脚本
--  兼容：PostgreSQL 14+  /  H2 (MODE=PostgreSQL)
--  执行时机：项目首次启动后或在 DataSourceInitializer 中加载
--  说明：JPA ddl-auto=update 会建表；本脚本只灌入种子数据
-- ============================================================

-- 历史通用种子脚本；须先建表。SET SCHEMA 同时适用于 PostgreSQL 和 H2。
-- DataGrip 初始化 PostgreSQL 请优先使用 backend-spring/scripts/init.sql（含序列同步）。
SET SCHEMA 'ynu-shooting';

-- ---------- 1. 基础管理员账号 ----------
-- 默认超管：openid=admin-openid-001，姓名"系统管理员"
INSERT INTO users (openid, nickname, avatar_url, role, profile_status, created_at, updated_at)
SELECT 'admin-openid-001', '系统管理员', '', 'superadmin', 'completed', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE openid = 'admin-openid-001');

-- 普通管理员：openid=admin-openid-002
INSERT INTO users (openid, nickname, avatar_url, role, profile_status, created_at, updated_at)
SELECT 'admin-openid-002', '管理员甲', '', 'admin', 'completed', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE openid = 'admin-openid-002');

-- 示范学生：openid=demo-student-001
INSERT INTO users (openid, nickname, avatar_url, role, profile_status, created_at, updated_at)
SELECT 'demo-student-001', '示范学生', '', 'student', 'completed', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE openid = 'demo-student-001');

-- ---------- 2. 示范学生实名档案 ----------
INSERT INTO profiles (user_id, real_name, student_no, phone, created_at, updated_at)
SELECT u.id, '张三', '2023101001', '13800000001', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM users u
WHERE u.openid = 'demo-student-001'
  AND NOT EXISTS (SELECT 1 FROM profiles p WHERE p.user_id = u.id);

-- ---------- 3. 设备种子数据 ----------
-- 实际仅有 2 台设备：1 把步枪 + 1 把手枪
-- ID 约定：手枪 id 为奇数，步枪 id 为偶数；前端按 id%2+1 映射显示序号
INSERT INTO devices (id, name, type, status, created_at, updated_at)
SELECT 1, '手枪靶位 1', 'pistol', 'IDLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM devices WHERE id = 1);

INSERT INTO devices (id, name, type, status, created_at, updated_at)
SELECT 2, '步枪靶位 1', 'rifle', 'IDLE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM devices WHERE id = 2);

-- ============================================================
--  Done. 时间片标准常量由后端 Slot 枚举提供，无需入库。
-- ============================================================
