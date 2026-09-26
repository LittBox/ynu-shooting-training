> **当前版本入口（2026-09-26）**：本文保留历史设计或当次验证结果，不代表最新实现与验收状态。当前功能、规则和待办以[版本总览](../docs/当前版本总览-v1.0.0.md)为准；旧规划中的表数、技术栈和待办不自动视为当前事实。

# 云大射击训练预约与成绩管理小程序 — 任务清单（tasks.md）

> 本文档是 `.specify/` 目录下的 `/tasks` 产出，按 US 拆分，每个任务含 ID、所属 US、依赖、产出、估时。
>
> **技术栈**：Spring Boot 3.4 + Maven + Spring Data JPA + PostgreSQL + Redis + Spring Security JWT

---

## 任务总览

| 任务编号 | 所属 US        | 内容                                       | 依赖       | 产出文件                                              | 估时   | 状态 |
|----------|----------------|--------------------------------------------|------------|-------------------------------------------------------|--------|------|
| T-001    | —              | 重构工程结构（Node.js → Spring Boot Maven） | —          | `server/pom.xml`、`server/src/main/`                  | 60 min | ⬜    |
| T-002    | US-全          | 实现数据模型（15 张表 → JPA Entity）       | T-001      | `server/src/main/java/.../entity/*.java`             | 90 min | ⬜    |
| T-003    | US-01          | 用户/角色与微信登录 stub + JWT             | T-002      | `SecurityConfig`、`JwtTokenProvider`、`AuthController` | 120 min | ⬜    |
| T-004    | US-02          | 设备状态接口 + WebSocket 通道              | T-002      | `DeviceController`、`WebSocketConfig`、`ws/`           | 90 min | ⬜    |
| T-005    | US-05.1/5.2    | 预约调度：三交集 + Redis 分布式锁           | T-002/T-003 | `BookingService`、`CacheLockService`、`RedisConfig`   | 150 min | ⬜ |
| T-006    | US-05.3        | 预约规则引擎（BR-01 ~ BR-06）              | T-005      | `RuleEngine`、`BookingController`                     | 90 min | ⬜ |
| T-007    | US-07          | 训练闭环：签到/开始/结束 + 爽约检测        | T-005      | `TrainingController`、`TrainingService`、`NoShowCheckJob` | 120 min | ⬜ |
| T-008    | US-08          | 成绩录入（决赛 24 / 资格赛 60）            | T-007      | `ScoreController`、`ScoreService`                      | 60 min | ⬜    |
| T-009    | US-09          | 个人画像接口（趋势 + 稳定性 + 频率）      | T-008      | `UserProfileController`、`ProfileService`              | 60 min | ⬜ |
| T-010    | US-10          | 三类排行榜（成绩 / PB / 进步之星）         | T-008      | `LeaderboardController`、`LeaderboardService`         | 90 min | ⬜ |
| T-011    | US-03/11       | 管理员端：排班/学员管理/录入               | T-003/T-008 | `admin/*Controller`、`AdminService`                    | 90 min | ⬜    |
| T-012    | US-12          | 备份脚本（每日 + 周备份）                  | T-002      | `BackupController`、`BackupService`、`pg_dump 脚本`    | 30 min | ⬜    |
| T-013    | US-02/04/05/06/07/08/09/10/11 | 小程序 7 个页面 + WS 客户端      | T-003~T-011 | `miniprogram/pages/*`、`miniprogram/utils/wsClient.js` | 180 min | ⬜ |
| T-014    | 质量           | 核心单测（规则引擎、锁、爽约）             | T-006/T-007 | `server/src/test/java/.../service/*Test.java`        | 120 min | ⬜    |
| T-015    | 质量           | 代码自审（code-review-expert + refactor-advisor） | T-014 | `docs/self-review.md`                                 | 30 min | ⬜    |
| T-016    | 质量           | CI workflow（github-actions-gen）           | T-014      | `.github/workflows/ci.yml`                             | 30 min | ⬜    |
| T-017    | 交付           | README（zh-readme）                        | T-013      | `README.md`                                           | 30 min | ⬜    |
| T-018    | 交付           | 全链路自验证脚本 + QAmd 最终化             | T-013/T-014 | `server/scripts/smoke-test.sh`、`docs/QAmd.md`        | 60 min | ⬜ |
| T-019    | 交付           | git commit + 最终交付总结                   | T-018      | 1+ git commit                                         | 10 min | ⬜    |

合计估算：约 18 小时（Spring Boot 迁移额外增加约 4 小时）。

---

## 任务详情

### T-001 重构工程结构（Node.js → Spring Boot Maven）

**依赖**：无

**步骤**：
1. 删除 `server/src/` 下所有 Node.js 文件（`routes/`、`services/`、`middleware/`、`db/`、`ws/`）
2. 创建 Maven 标准结构：
   ```
   server/
   ├── pom.xml
   ├── src/main/java/com/ynu/shotting/
   │   ├── ShottingApplication.java
   │   ├── config/
   │   ├── controller/
   │   ├── service/
   │   ├── repository/
   │   ├── entity/
   │   ├── dto/
   │   ├── security/
   │   ├── exception/
   │   └── scheduler/
   └── src/main/resources/
       ├── application.yml
       └── db/migration/
           └── V1__init_schema.sql
   ```
3. 写入 `pom.xml`（Spring Boot 3.4 Starter、jjwt、postgresql、redis、flyway）
4. 保留 `miniprogram/`、`docs/`、`.specify/`、`server/package.json`（删除，迁移后不再需要）

**验收**：
- `mvn compile` 成功（无任何编译错误）
- `mvn dependency:tree` 包含 spring-boot-starter-web、spring-boot-starter-data-jpa、jjwt

---

### T-002 实现数据模型（JPA Entity）

**依赖**：T-001

**步骤**：
1. 创建 `entity/` 包下的所有实体类（15 张表对应 15 个 Entity）：
   - `User.java`、`Role.java`、`UserRole.java`（用户与角色）
   - `Device.java`、`AdminSchedule.java`、`NoShowRecord.java`（设备与排班）
   - `Booking.java`、`TrainingSession.java`、`Score.java`、`ScoreDetail.java`（预约与训练）
   - `CancellationLog.java`、`AuditLog.java`、`BackupRecord.java`（辅助）
2. 创建 `repository/` 包下的 JPA Repository 接口
3. 创建 `db/migration/V1__init_schema.sql`（Flyway 迁移脚本，与 Entity 一一对应）
4. 配置 `application.yml` 的 datasource、jpa、flyway

**验收**：
- `mvn spring-boot:run` 启动成功，Flyway 执行迁移无报错
- 启动后 `GET /actuator/health` 返回 `{"status":"UP"}`

---

### T-003 用户/角色与微信登录 stub + JWT

**依赖**：T-002

**步骤**：
1. `config/SecurityConfig.java`：配置 JWT 白名单、端点权限
2. `security/JwtTokenProvider.java`：JWT 签发/校验（jjwt 0.12.x，HS256）
3. `security/JwtAuthenticationFilter.java`：Filter 拦截 Bearer token
4. `controller/AuthController.java`：
   - `POST /api/auth/wechat/login` → mock openid → 创建/查找 user → 签发 JWT
   - `GET /api/auth/me` → 返回当前用户
5. `service/UserService.java`：
   - 身份登记（学号唯一性校验）
   - 学员可训练时间登记（availability）

**验收**：
- 未登录调用 `/api/auth/me` 返回 401
- 提交相同学号两次 → 第二次返回 409
- JWT 2 小时后过期（可用环境变量 `jwt.expiration-ms=60000` 缩短测试）

---

### T-004 设备状态接口 + WebSocket 通道

**依赖**：T-002

**步骤**：
1. `repository/DeviceRepository.java`：JPA 查询
2. `service/DeviceService.java`：设备状态管理
3. `controller/DeviceController.java`：`GET /api/devices` → 返回全部设备
4. `config/WebSocketConfig.java`：STOMP over SockJS 配置
5. `service/WebSocketService.java`：广播方法（推送设备状态变更）

**验收**：
- `/api/devices` 返回 4 台设备（步枪2台、手枪2台）
- WebSocket 连接成功，能收到 STOMP PING
- 调用 `webSocketService.broadcast()` 后客户端收到消息

---

### T-005 预约调度：三交集 + Redis 分布式锁

**依赖**：T-002、T-003

**步骤**：
1. `config/RedisConfig.java`：RedisTemplate + RedissonClient 配置
2. `service/CacheLockService.java`：
   - Redis SETNX 分布式锁（Redisson RLock）
   - `@DistLock` AOP 注解
3. `service/BookingService.java`：
   - `findAvailableSlots(userId, deviceId, dateRange)` → 三交集过滤
   - `createBooking()` → 加锁 → 写 DB → 发 WS
4. `controller/BookingController.java`（初版）：
   - `GET /api/bookings/slots`
   - `POST /api/bookings`

**验收**：
- 学员未排班时间不出现在 slots
- 设备在维护中不出现在 slots
- 并发请求同一时间片 → 仅 1 次成功（Redis 锁 + DB 唯一约束双重保证）

---

### T-006 预约规则引擎

**依赖**：T-005

**步骤**：
1. `service/RuleEngine.java`：BR-01 ~ BR-06 全部规则，链式执行
2. `controller/BookingController.java`：在 `POST /api/bookings` 中调用 `RuleEngine.evaluate()`
3. `test/service/RuleEngineTest.java`：覆盖每条规则的正反例

**验收**：
- 单测覆盖率 ≥ 80%
- 触发 BR-01 时返回 `400 Bad Request`，message 包含"今日预约已达上限（3 场）"

---

### T-007 训练闭环 + 爽约检测

**依赖**：T-005

**步骤**：
1. `service/TrainingService.java`：
   - `checkin(bookingId)` → 状态 BOOKED → CHECKED_IN
   - `start(bookingId)` → 状态 CHECKED_IN → IN_USE，更新设备状态
   - `end(bookingId)` → 状态 IN_USE → COMPLETED，计算实际时长
   - 每步广播对应 WS 事件
2. `controller/TrainingController.java`：
   - `POST /api/training/:bookingId/checkin`
   - `POST /api/training/:bookingId/start`
   - `POST /api/training/:bookingId/end`
3. `scheduler/NoShowCheckJob.java`：`@Scheduled(fixedRate = 60000)` 定时扫描爽约

**验收**：
- 签到窗口外签到 → 400 `checkin_window_invalid`
- 结束训练 → 设备状态 → IDLE，预约 → COMPLETED
- 模拟 1 条 BOOKED 已过期 → 1 分钟后自动变 NO_SHOW

---

### T-008 成绩录入（决赛 24 / 资格赛 60）

**依赖**：T-007

**步骤**：
1. `service/ScoreService.java`：
   - `recordScore()` → 校验 shot 数（final=24 / qualifying=60）→ 写入 scores + score_details
   - 计算 total、group avg、x count
2. `controller/ScoreController.java`：
   - `POST /api/scores`（admin 权限）

**验收**：
- 决赛模式提交 23 发 → 400 `invalid_shot_count`
- 录入后 GET `/api/users/me/profile` 包含最新总分

---

### T-009 个人画像接口

**依赖**：T-008

**步骤**：
1. `service/ProfileService.java`：
   - 趋势数据查询（按 event_type 分组）
   - 标准差计算（σ）
   - 训练频率统计
2. `controller/UserProfileController.java`：
   - `GET /api/users/me/profile`

**验收**：
- 仅返回本人或 admin 可访问他人
- SD 计算与手算一致（用例：6 组 [10,9,10,9,10,9] → SD=0.5）

---

### T-010 三类排行榜

**依赖**：T-008

**步骤**：
1. `service/LeaderboardService.java`：
   - 成绩榜（按总分倒序）
   - PB 榜（区分决赛/资格赛）
   - 进步之星（本月 vs 上月提升幅度）
2. `controller/LeaderboardController.java`：
   - `GET /api/leaderboard/score`
   - `GET /api/leaderboard/pb`
   - `GET /api/leaderboard/improvement`

**验收**：
- 三榜均能返回正确排序
- 隐私：仅返回 nickname + 分数 + 日期

---

### T-011 管理员端：排班/学员管理/录入

**依赖**：T-003、T-008

**步骤**：
1. `controller/admin/ScheduleAdminController.java`：
   - `GET /api/admin/schedules` / `POST` / `DELETE`
2. `controller/admin/StudentAdminController.java`：
   - `GET /api/admin/students` / `GET /api/admin/students/:userId`
3. `service/AuditService.java`：学员查看接口写 audit_log

**验收**：
- student 角色调用 `/api/admin/*` 返回 403
- 学员详情含姓名/学号/电话，排行榜中无

---

### T-012 备份脚本

**依赖**：T-002

**步骤**：
1. `scripts/backup.sh`：基于 `pg_dump` 命令（PostgreSQL）
2. `controller/admin/BackupController.java`：
   - `GET /api/admin/backups` → 列出备份文件
   - `POST /api/admin/backup/restore` → 执行 `pg_restore`
3. `crontab.example`：每日 03:00 + 周日 03:00

**验收**：
- `./backup.sh daily` 产生 `shotting_backup_YYYYMMDD.dump` 文件
- `./backup.sh weekly` 标记周备份保留更长

---

### T-013 小程序端：7 个页面 + WS 客户端

**依赖**：T-003~T-011

**步骤**：
1. `miniprogram/app.js` / `app.json` / `app.wxss`
2. `miniprogram/utils/wsClient.js`：WebSocket STOMP 连接 + 重连 + 心跳
3. `miniprogram/utils/api.js`：封装 `wx.request` + JWT 注入
4. 7 个页面实现（与 Node.js 版本相同，仅调整 API 域名）

**验收**：
- 7 个页面四件套齐全
- `app.json` 注册全部页面
- WS 客户端断网后能自动重连

**注意**：小程序端**无需修改**，接口契约保持不变。

---

### T-014 核心单测

**依赖**：T-006、T-007

**步骤**：
1. `test/service/RuleEngineTest.java`：覆盖 BR-01~BR-06
2. `test/service/CacheLockServiceTest.java`：模拟并发请求验证锁
3. `test/service/NoShowCheckJobTest.java`：定时任务幂等性
4. `test/service/ProfileServiceTest.java`：SD 与趋势数据
5. `test/controller/BookingControllerTest.java`：MockMvc 集成测试

**验收**：
- `mvn test` 全部通过
- 覆盖率：RuleEngine、CacheLockService、BookingService（三交集）、NoShowCheckJob ≥ 80%

---

### T-015 代码自审

**依赖**：T-014

**步骤**：
1. 用 code-review-expert 视角审视 `server/src/main/` 与 `miniprogram/`
2. 用 refactor-advisor 视角建议命名/结构优化
3. 把发现项写入 `docs/self-review.md` 并就地修复

---

### T-016 CI workflow

**依赖**：T-014

**步骤**：
1. `.github/workflows/ci.yml`：触发 push/PR → `mvn test` → `mvn verify` → 上传 coverage

---

### T-017 README

**依赖**：T-013

**步骤**：
1. `README.md`：项目简介、目录结构、本地启动、冒烟自检命令、QAmd 链接

---

### T-018 全链路自验证 + QAmd 最终化

**依赖**：T-013、T-014

**步骤**：
1. `server/scripts/smoke-test.sh`：curl 脚本，模拟登录→登记→预约→签到→训练→成绩→排行榜 全流程
2. 启动服务 → 运行 smoke-test → 写入 `docs/smoke-test.log`
3. 最终检查 `docs/QAmd.md`：确认技术栈回退已完成

**验收**：
- smoke-test 全程无错误
- log 中可见每一步响应

---

### T-019 git commit + 最终交付总结

**依赖**：T-018

**步骤**：
1. `git add -A && git commit -m "feat: V1.0 MVP 完整交付（Spring Boot + PostgreSQL + Redis）"`
2. 输出最终交付总结（功能清单、目录、启动命令、自检结果、待审查项）

---

## 风险与备注

- **R-01**：Spring Boot 启动需 PostgreSQL + Redis 环境 → 确认本地/服务器已安装
- **R-02**：JPA Entity 与 Flyway SQL 需保持一致 → 禁止修改已执行的迁移 V1__init_schema.sql
- **R-03**：微信小程序真机调试需 AppID → V1.0 使用 `__test__` mock，登录用 stub（参见 `docs/QAmd.md` Q7）
- **R-04**：小程序端**无需改动**，接口契约保持不变，只需切换 API 域名

---

## 技术栈对照表

| 层级   | Node.js 旧方案（已废弃） | Spring Boot 新方案 |
|--------|-------------------------|-------------------|
| 构建   | npm / package.json      | Maven / pom.xml   |
| 框架   | Express.js              | Spring Boot 3.4   |
| ORM    | better-sqlite3（原生SQL）| Spring Data JPA   |
| 数据库 | SQLite                  | PostgreSQL        |
| 缓存   | ioredis-mock            | Redis + Redisson  |
| 安全   | jsonwebtoken            | Spring Security + jjwt |
| 定时   | node-cron               | @Scheduled        |
| 迁移   | migrate.js              | Flyway            |
| 测试   | Jest + Supertest        | JUnit 5 + Mockito |
