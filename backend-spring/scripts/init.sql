-- PostgreSQL 14+ 本地演示数据；不用于生产环境。
-- 先运行 schema-postgresql.sql，或让 Spring 在同一个数据库中成功建表。
-- 这份脚本仅插入种子数据，不负责建表；重复执行不重复插入。
-- resources/scripts/init.sql 保留为 H2/PostgreSQL 通用种子数据参考。
BEGIN;
SET LOCAL search_path TO "ynu-shooting";

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
INSERT INTO profiles (user_id, real_name, student_no, phone, gender, created_at, updated_at)
SELECT u.id, '张三', '2023101001', '13800000001', 'U', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
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

-- 显式插入设备 ID 不会推进 PostgreSQL 自增序列；避免后续新增设备主键冲突。
-- 仅在本地初始化时、后端停止期间执行；同时保留已经推进到更高位置的序列值。
SELECT setval(
    pg_get_serial_sequence('"ynu-shooting".devices', 'id'),
    GREATEST((SELECT MAX(id) FROM "ynu-shooting".devices),
             nextval(pg_get_serial_sequence('"ynu-shooting".devices', 'id'))),
    true
);

COMMIT;
