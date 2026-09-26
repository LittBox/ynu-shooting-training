> **当前版本入口（2026-09-26）**：本文保留历史设计或当次验证结果，不代表最新实现与验收状态。当前功能、规则和待办以[版本总览](../docs/当前版本总览-v1.0.0.md)为准；旧规划中的表数、技术栈和待办不自动视为当前事实。

# plan.md — 技术方案

> 本文件是项目的技术实现路线图，与 `memory/constitution.md` 第八条技术栈一致。

---

## 一、技术选型决策

### 1.1 为什么是 Spring Boot 而非 Node.js

| 维度       | Node.js (原方案)           | Spring Boot 3.4 (选定)          |
|-----------|----------------------------|---------------------------------|
| 类型系统    | 动态 JS，`any` 滥用风险高   | 强类型 Java，编译期错误检测      |
| 生态成熟度  | Express 轻量但监控/链路追踪弱 | Spring Boot Actuator + Micrometer 全套 |
| 并发模型    | 单线程事件循环，不适合 CPU 密集 | JVM 多线程，适合预约锁校验       |
| 团队熟悉度  | 需新学 Express + TypeORM   | Java/Spring 已有积累             |
| 企业级特性  | 需手动集成安全/事务         | Security + @Transactional 开箱即用 |

### 1.2 为什么用 Spring Data JPA 而非 MyBatis

- V1.0 表结构相对稳定，实体关系清晰（JPA 一对多/多对多注解直接建模）
- 减少 XML 配置，开发效率高
- 复杂查询用 `@Query` + Native SQL兜底

### 1.3 为什么用 Redis 而非本地缓存

- 分布式部署时多个后端实例共享锁状态
- WebSocket 集群广播需要 pub/sub
- 锁过期自动释放，不怕进程崩溃

---

## 二、系统架构

```
┌──────────────────────────────────────────────────────────────┐
│                    小程序客户端 (微信原生)                    │
│         首页 │ 预约 │ 队列 │ 画像 │ 排行榜 │ 管理页            │
└──────────────┬──────────────────────────────────────────────┘
               │  HTTPS + JWT
               ▼
┌──────────────────────────────────────────────────────────────┐
│                Spring Boot 3.4 + JDK 21                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐  │
│  │ Controller│ │  Service  │ │ Repository│ │  WebSocket   │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────────┘  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │           Spring Security + JWT (jjwt)              │   │
│  └──────────────────────────────────────────────────────┘   │
└──────────────┬──────────────────────┬───────────────────────┘
               │                      │
     ┌─────────▼──────┐    ┌──────────▼──────────┐
     │   PostgreSQL   │    │       Redis 7        │
     │  localhost:5432│    │   localhost:6379     │
     │   写预约/成绩   │    │  分布式锁 + 会话缓存  │
     └────────────────┘    │  WebSocket 广播      │
                            └─────────────────────┘
```

---

## 三、数据模型（实体关系）

### 3.1 核心实体

```
users ─── N:M ─── roles
  │                    (user_role 中间表)
  ├── profiles (1:1)   身份信息：学号/姓名/电话/身份证/院系
  ├── availability (1:N) 学员可训练时间段
  ├── bookings (1:N)     预约记录
  ├── training_sessions (1:N) 训练会话（含状态机）
  └── scores (1:N)       成绩记录

devices
  ├── device_schedules (1:N) 设备可服务时间（管理员排班）

admin_schedules (N:N) ── admins 管理员值班时间（用户 N:1）

training_sessions (状态机)
  BOOKED → CHECKED_IN → IN_USE → COMPLETED
                ↓            ↓
          CANCELLED    CANCELLED / NO_SHOW
```

### 3.2 主要表设计

| 表名                | 主键        | 关键字段                                                    |
|--------------------|------------|-----------------------------------------------------------|
| `users`            | `id` UUID  | `openid` (UNIQUE), `nickname`, `avatar_url`, `created_at` |
| `user_profiles`    | `id` UUID  | `user_id` FK, `student_no`, `name`, `phone`, `id_card`, `department`, `major` |
| `roles`            | `id` INT   | `name` (STUDENT/ADMIN/SUPERADMIN)                         |
| `user_roles`       | 复合 PK    | `user_id` FK, `role_id` FK                                |
| `devices`          | `id` UUID  | `name`, `status` (枚举), `location`, `created_at`         |
| `admin_schedules`  | `id` UUID  | `admin_id` FK, `date`, `time_slot`                        |
| `student_availability` | `id` UUID | `student_id` FK, `date`, `time_slot`                     |
| `bookings`         | `id` UUID  | `student_id` FK, `device_id` FK, `date`, `time_slot`, `status`, `queue_position` |
| `training_sessions`| `id` UUID | `booking_id` FK, `actual_start`, `actual_end`, `actual_duration_min` |
| `scores`           | `id` UUID  | `training_session_id` FK, `event_type` (FINAL/QUALIFICATION), `group_scores` (JSONB) |
| `no_show_records`  | `id` UUID  | `user_id` FK, `booking_id` FK, `occurred_at`              |

---

## 四、分层架构

```
server/src/main/java/com/ynu/shotting/
├── ShottingApplication.java              # 启动类
├── config/
│   ├── SecurityConfig.java              # Spring Security 配置
│   ├── WebSocketConfig.java             # STOMP + SockJS 配置
│   ├── RedisConfig.java                 # RedisTemplate 配置
│   └── JpaConfig.java                   # JPA/Hibernate 配置
├── controller/                          # REST Controller（接收请求）
│   ├── AuthController.java
│   ├── DeviceController.java
│   ├── BookingController.java
│   ├── TrainingController.java
│   ├── ScoreController.java
│   ├── LeaderboardController.java
│   ├── UserController.java
│   └── admin/
│       ├── AdminUserController.java
│       ├── AdminDeviceController.java
│       ├── AdminScheduleController.java
│       └── AdminBackupController.java
├── service/                            # 业务逻辑
│   ├── BookingService.java              # 预约 + 三交集 + 规则引擎
│   ├── TrainingService.java            # 训练闭环状态机
│   ├── DeviceService.java              # 设备状态管理
│   ├── ScoreService.java               # 成绩计算
│   ├── LeaderboardService.java         # 排行榜计算
│   ├── AvailabilityService.java        # 三交集匹配
│   ├── AuthService.java                # 微信 Auth + JWT
│   └── NoShowScheduler.java            # 爽约检测定时任务
├── repository/                          # Spring Data JPA
│   ├── UserRepository.java
│   ├── BookingRepository.java
│   ├── DeviceRepository.java
│   ├── ScoreRepository.java
│   └── ...
├── domain/
│   ├── entity/                         # JPA 实体
│   ├── enums/                          # 枚举：DeviceStatus, BookingStatus, EventType
│   └── dto/                            # Data Transfer Objects（请求/响应）
├── exception/
│   ├── GlobalExceptionHandler.java     # 统一异常处理
│   └── BusinessException.java          # 业务异常（带错误码）
└── security/
    ├── JwtTokenProvider.java
    └── JwtAuthenticationFilter.java
```

---

## 五、安全设计

### 5.1 JWT 策略

- 算法：HS256（V1.0），密钥存 `application.yml` 环境变量
- Token 内容：`userId + roles + exp(24h) + iat`
- 刷新：V1.0 不做主动刷新，24 小时后重新登录
- 白名单：`/api/auth/**`, `/ws/**`, `/actuator/health`

### 5.2 角色鉴权

```java
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPERADMIN')")
public void updateDeviceStatus(...) { }
```

### 5.3 数据脱敏

- Controller 层对普通用户（student）脱敏：姓名 → `张*三`，学号 → `20201***1`
- 对 admin/superadmin 返回完整字段

---

## 六、WebSocket 推送设计

| 频道                  | 推送时机                          | 接收者          |
|---------------------|---------------------------------|--------------|
| `/topic/devices`    | 设备状态变更                      | 所有在线用户     |
| `/topic/booking/{userId}` | 预约状态变更 / 提前开始通知 | 指定用户        |
| `/topic/queue/{deviceId}` | 队列推进                      | 相关设备排队用户  |

心跳：客户端每 25 分钟发送 `STOMP` CONNECT 帧的 heartbeat 字段，服务端响应。

---

## 七、预约三交集算法

```
输入: studentId, deviceId, date, timeSlot
输出: 是否可预约

1. 查询学员可用时间集合 S(student_id, date, time_slot)
2. 查询管理员值班时间集合 A(date, time_slot)
3. 查询设备可用时间集合 D(device_id, date, time_slot)
   （设备状态为 IDLE/BOOKED 则在时间片内可用）
4. 三交集 = S ∩ A ∩ D
5. 若交集为空 → 拒绝预约（返回具体缺失方）
6. 若交集非空 → 获取 Redis 分布式锁 → 插入 booking → 释放锁
```

---

## 八、状态机

```
预约状态 (BookingStatus):
  BOOKED
    ├─[取消]→ CANCELLED
    ├─[超时未签到]→ NO_SHOW
    ├─[签到]→ CHECKED_IN
    │         ├─[取消]→ CANCELLED
    │         └─[开始训练]→ IN_USE
    │                   ├─[结束训练]→ COMPLETED
    │                   └─[异常中断]→ CANCELLED
    └─[管理员删除]→ CANCELLED
```

---

## 九、备份策略

- **每日备份**：crontab `0 2 * * *` 执行 `pg_dump -Fc shotting`
- **保留策略**：日备份保留 7 份，周备份（周日）保留 4 份
- **存储路径**：`server/backups/shotting_backup_YYYYMMDD_HHMMSS.dump`
- **恢复命令**：`pg_restore -d shotting < backup.dump`

---

## 十、环境变量

| 变量                   | 说明                    | 示例                        |
|----------------------|-----------------------|---------------------------|
| `DB_HOST`             | PostgreSQL 地址         | `localhost`               |
| `DB_PORT`             | PostgreSQL 端口         | `5432`                    |
| `DB_NAME`             | 数据库名                | `shotting`                |
| `DB_USER`             | 数据库用户              | `postgres`                |
| `DB_PASSWORD`         | 数据库密码              | `postgres`                |
| `REDIS_HOST`          | Redis 地址              | `localhost`               |
| `REDIS_PORT`          | Redis 端口              | `6379`                    |
| `JWT_SECRET`          | JWT 密钥（≥32字符）     | `your-secret-key-here`   |
| `WECHAT_APPID`        | 微信小程序 AppID        | `wx1234567890abcdef`      |
| `WECHAT_SECRET`       | 微信小程序 AppSecret    | `abcdef1234567890...`     |
| `SERVER_PORT`         | 服务端口                | `8080`                    |

---

## 十一、目录结构（最终态）

```
server/
├── pom.xml                              # Maven 依赖
├── src/main/java/com/ynu/shotting/      # 源码（含上述分层）
├── src/main/resources/
│   ├── application.yml                  # 主配置
│   └── db/migration/
│       └── V1__init_schema.sql          # Flyway 初始迁移（幂等）
├── src/test/java/                       # 单元测试
├── data/                                # SQLite 开发数据（开发用）
└── backups/                             # pg_dump 输出

miniprogram/                             # 微信小程序源码
├── pages/                               # 页面
├── components/                          # 通用组件
├── utils/                               # 工具（request.js, auth.js）
└── images/                              # 静态图片
```
