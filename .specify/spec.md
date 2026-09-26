> **当前版本入口（2026-09-26）**：本文保留历史设计或当次验证结果，不代表最新实现与验收状态。当前功能、规则和待办以[版本总览](../docs/当前版本总览-v1.0.0.md)为准；旧规划中的表数、技术栈和待办不自动视为当前事实。

# spec.md — 功能需求规格

> 本文件是 AI 可理解的、面向实现的完整需求规格。
> 每一节对应需求文档一章，遵循「用户故事 + Given/When/Then 验收」结构。
> 如与 `memory/constitution.md` 冲突，以宪法为准并登记 `docs/QAmd.md`。
>
> **技术栈**：Spring Boot 3.4 + Maven + Spring Data JPA + PostgreSQL + Redis + Spring Security JWT

---

## US-01 用户与角色

### US-01.1 微信登录

**用户故事**：作为学员，我希望用微信一键登录，以便免注册直接开始使用。

**验收标准**：

- **Given** 用户首次打开小程序，**When** 点击"微信登录"按钮，**Then** 小程序通过 `wx.login()` 获取 code，并调用 `POST /api/auth/wechat/login` 换取 JWT，返回昵称/头像占位页。
- **Given** 用户已登录且 token 未过期，**When** 重新打开小程序，**Then** 直接进入首页，不重复登录。
- **Given** JWT 已过期，**When** 调用任意业务接口，**Then** 返回 401，客户端跳转登录页。
- **Given** 微信返回错误码，**When** 调用登录接口，**Then** 返回友好错误提示，不崩溃。

### US-01.2 身份登记

**用户故事**：作为学员，我需要登记真实身份信息，以便管理员确认我有训练资格。

**验收标准**：

- **Given** 首次登录用户，**When** 进入身份登记页并填写学号/姓名/联系电话/身份证号/院系/专业，**Then** 提交后后端校验学号唯一性（参见 `docs/QAmd.md` Q3），成功后跳转首页。
- **Given** 学号已被注册，**When** 提交登记表单，**Then** 返回 409 并提示"该学号已注册"。
- **Given** 已登记用户，**When** 进入登记页，**Then** 展示已填信息，可编辑修改（学号不可改）。
- **Given** 表单必填字段为空，**When** 提交，**Then** 前端阻断并高亮缺失字段。

### US-01.3 角色管理

**用户故事**：作为超级管理员，我需要授予/撤销用户角色，以便管理训练场运营人员。

**验收标准**：

- **Given** 超级管理员，**When** 调用 `POST /api/admin/users/{id}/roles` 并传入角色数组，**Then** 覆盖用户角色（支持 N:M），返回更新后角色列表。
- **Given** 普通学员，**When** 调用角色管理接口，**Then** 返回 403。
- **Given** 超级管理员，**When** 授予自己 superadmin 角色，**Then** 返回 400（禁止自我提升）。

---

## US-02 设备状态

### US-02.1 设备卡片

**用户故事**：作为学员，我希望看到所有设备的实时状态，以便选择空闲设备预约。

**验收标准**：

- **Given** 学员登录，**When** 进入首页，**Then** 展示所有设备卡片，每张卡片含：设备名称（步枪位/手枪位）、设备状态（IDLE/BOOKED/IN_USE/MAINTENANCE/DISABLED）、当前时间片信息。
- **Given** 设备状态变更，**When** WebSocket 推送新状态，**Then** 卡片在 3 秒内更新，无需刷新页面。
- **Given** 设备状态为 MAINTENANCE/DISABLED，**When** 点击预约，**Then** 按钮禁用并提示"设备不可用"。

### US-02.2 状态枚举与推送

**设备状态枚举**：
- `IDLE`：空闲，可预约
- `BOOKED`：已被预约，设备锁定
- `IN_USE`：训练中
- `MAINTENANCE`：维护中
- `DISABLED`：停用

**验收标准**：

- **Given** 管理员，**When** 调用 `PATCH /api/admin/devices/{id}/status`，**Then** 设备状态更新，WebSocket 广播 `/topic/devices` 所有在线用户。
- **Given** 学员，**When** 建立 WebSocket 连接，**Then** 自动订阅 `/topic/devices`，收到消息后更新本地状态。

---

## US-03 管理员排班

### US-03.1 管理员值班时间维护

**用户故事**：作为管理员，我希望维护自己可值班的时间段，以便系统知道何时可以开放预约。

**验收标准**：

- **Given** 管理员，**When** 调用 `POST /api/admin/schedules`，传入日期 + 时间片编号数组，**Then** 创建排班记录，支持同一时段排多个管理员（Q4）。
- **Given** 管理员，**When** 调用 `DELETE /api/admin/schedules/{id}`，**Then** 删除排班，若有已确认预约则提示冲突。
- **Given** 学员，**When** 查询管理员排班，**Then** 返回 403。

---

## US-04 学员可训练时间

### US-04.1 学员时间段登记

**用户故事**：作为学员，我希望登记自己可训练的时间，以便系统做三交集匹配。

**验收标准**：

- **Given** 学员，**When** 调用 `PUT /api/users/me/availability`，传入日期 + 时间片编号数组，**Then** 覆盖更新可训练时间。
- **Given** 学员，**When** 调用 `GET /api/users/me/availability`，**Then** 返回本人已登记的时间段列表。

---

## US-05 预约调度

### US-05.1 三交集匹配

**用户故事**：作为学员，我提交预约请求后，系统自动校验学员可用时间、管理员值班时间、设备可用时间三者交集，非空才允许预约。

**验收标准**：

- **Given** 学员提交预约（设备ID + 日期 + 时间片），**When** 三交集存在，**Then** 预约成功，返回 BOOKED 状态及队列位置。
- **Given** 三交集为空（例：无管理员值班），**When** 提交预约，**Then** 返回 409 并提示"该时段无可用管理员/设备"。

### US-05.2 资源锁

**用户故事**：作为系统，我需要在预约时加锁，防止同一设备同一时间片被并发重复预约。

**验收标准**：

- **Given** 两个并发请求同时预约同一设备同一时间片，**When** 两请求同时到达，**Then** 只有一个成功，另一个返回 409。
- **Given** 预约成功，**When** Redis SETNX 获得锁，**Then** 锁在训练开始后自动释放；若 30 秒内未完成 DB 写入，锁自动过期。

### US-05.3 规则引擎

**用户故事**：作为系统，我需要校验预约是否违反业务规则，违反则拒绝。

**验收标准**（宪法第六条）：

- **Given** 学员当天已有 3 场预约（BOOKED/CHECKED_IN/IN_USE/COMPLETED），**When** 再提交预约，**Then** 返回 429 并提示"每日预约上限 3 场"。
- **Given** 学员本周已有 10 场预约，**When** 再提交预约，**Then** 返回 429 并提示"每周预约上限 10 场"。
- **Given** 学员预约 8 天后的时间片，**When** 提交预约，**Then** 返回 400 并提示"最多提前 7 天"。
- **Given** 学员已预约某时间片，**When** 再预约同一时间片，**Then** 返回 409 并提示"同一时段不得重复预约"。
- **Given** 设备状态为 IN_USE/MAINTENANCE/DISABLED，**When** 预约该设备，**Then** 返回 409 并提示"设备当前不可用"。

---

## US-06 实时队列与 WebSocket

### US-06.1 队列展示

**用户故事**：作为学员，我希望能实时看到当前排队人数和预计等待时间。

**验收标准**：

- **Given** 学员，**When** 进入队列页，**Then** 展示本人所有 BOOKED 预约，含：设备名、日期、时间片、队列位置、预计等待时间（= 前序用户数 × 平均训练时长）。
- **Given** 有其他用户取消预约，**When** 队列位置变化，**Then** WebSocket 推送更新，本页自动刷新队列位置。

### US-06.2 提前开始通知

**用户故事**：作为学员，我希望在前序用户爽约后收到通知，以便提前开始训练。

**验收标准**：

- **Given** 前序用户爽约，**When** 队列推进，**Then** WebSocket 推送 `/topic/user/{userId}` 告知"您可以提前开始"，客户端弹出通知。

### US-06.3 状态实时推送

**验收标准**：

- **Given** 管理员标记设备状态变更，**When** 操作完成，**Then** `/topic/devices` 推送更新，所有在线用户设备卡刷新。
- **Given** WebSocket 连接超过 30 分钟无心跳，**Then** 服务器主动断开连接，客户端需重连。

---

## US-07 训练闭环

### US-07.1 签到

**用户故事**：作为学员，我需要在预约时间窗口内签到，否则被标记爽约。

**验收标准**：

- **Given** 学员的预约状态为 BOOKED，时间在预约开始前 30 分钟至开始后 10 分钟内，**When** 调用 `POST /api/bookings/{id}/checkin`，**Then** 状态变为 CHECKED_IN，记录签到时间。
- **Given** 学员在预约开始后超过 10 分钟，**When** 调用签到接口，**Then** 返回 410 并提示"签到已超时，您已被标记爽约"，状态变为 NO_SHOW。
- **Given** 后台定时任务检测到超时未签到预约，**When** 超时事件触发，**Then** 状态变为 NO_SHOW，记录爽约时间。

### US-07.2 开始训练

**用户故事**：作为学员，签到后我想随时开始训练，并记录实际开始时间。

**验收标准**：

- **Given** 预约状态为 CHECKED_IN，**When** 调用 `POST /api/training/{bookingId}/start`，**Then** 状态变为 IN_USE，记录开始时间，设备状态更新为 IN_USE。
- **Given** 预约状态不是 CHECKED_IN，**When** 调用开始训练，**Then** 返回 409 并提示"请先签到"。

### US-07.3 结束训练

**用户故事**：作为学员，训练完成后我想主动结束训练，记录实际时长。

**验收标准**：

- **Given** 预约状态为 IN_USE，**When** 调用 `POST /api/training/{bookingId}/end`（学员自操作，参见 `docs/QAmd.md` Q6），**Then** 状态变为 COMPLETED，记录结束时间，实际时长 = max(结束−开始, 1分钟)，设备状态恢复 IDLE。
- **Given** 预约状态不是 IN_USE，**When** 调用结束训练，**Then** 返回 409。
- **Given** 训练时长超过时间片结束时间，**When** 结束训练，**Then** 正常结束，超出时间由管理员协调，不影响本次记录。

---

## US-08 成绩录入

### US-08.1 决赛 24 发 / 三组

**用户故事**：作为管理员，我需要录入学员决赛成绩（24发，3组×8发）。

**验收标准**：

- **Given** 预约状态为 COMPLETED，**When** 管理员调用 `POST /api/scores` 传入 `event_type=FINAL`、3 组每组 8 个整数（0-10）成绩，**Then** 保存成绩，计算总分，记录到 `scores` 表。
- **Given** 成绩数组长度不为 24，**When** 提交，**Then** 返回 400 并提示"决赛成绩必须为 24 发"。

### US-08.2 资格赛 60 发 / 六组

**用户故事**：作为管理员，我需要录入学员资格赛成绩（60发，6组×10发）。

**验收标准**：

- **Given** 预约状态为 COMPLETED，**When** 管理员调用 `POST /api/scores` 传入 `event_type=QUALIFICATION`、6 组每组 10 个整数（0-10）成绩，**Then** 保存成绩，计算总分。
- **Given** 成绩数组长度不为 60，**When** 提交，**Then** 返回 400 并提示"资格赛成绩必须为 60 发"。

---

## US-09 个人画像

### US-09.1 趋势按模式分图

**用户故事**：作为学员，我想看到自己的成绩趋势图，以便了解进步情况。

**验收标准**：

- **Given** 学员有多条成绩记录，**When** 调用 `GET /api/users/me/profile`，**Then** 返回：决赛总分趋势（折线图数据）、资格赛总分趋势（折线图数据），X 轴为日期，Y 轴为总分。
- **Given** 学员无成绩记录，**When** 查看画像，**Then** 展示空状态引导页。

### US-09.2 六组稳定性

**用户故事**：作为学员，我想看到各组成绩的标准差，以便了解发挥稳定性。

**验收标准**：

- **Given** 学员有至少一场完整记录，**When** 查看画像，**Then** 返回每组成绩的标准差（σ），标注高稳定性（σ≤1.0）和低稳定性（σ>2.0）组。

### US-09.3 频率

**验收标准**：

- **Given** 学员，**When** 查看画像，**Then** 返回：本月训练场次、近3月训练场次趋势、场均时长。

---

## US-10 龙虎榜

### US-10.1 成绩榜

**用户故事**：作为用户，我想按项目+模式看成绩排名。

**验收标准**：

- **Given** 任意用户，**When** 调用 `GET /api/leaderboard?type=score&event=FINAL&period=ALL`，**Then** 返回所有决赛成绩从高到低排名（仅昵称+总分，隐私字段不出现在对普通用户的响应中）。
- **Given** 任意用户，**When** 调用 `GET /api/leaderboard?type=score&event=QUALIFICATION&period=LAST_30_DAYS`，**Then** 返回近30天资格赛成绩排名。

### US-10.2 PB 榜（区分决赛/资格赛）

**用户故事**：作为用户，我想看个人最好成绩榜（区分决赛/资格赛，参见 `docs/QAmd.md` Q8）。

**验收标准**：

- **Given** 任意用户，**When** 调用 `GET /api/leaderboard?type=pb`，**Then** 返回两条子榜：FINAL PB 榜和 QUALIFICATION PB 榜，每榜按个人最高成绩排序。

### US-10.3 进步之星

**用户故事**：作为用户，我想看到最近进步最快的学员。

**验收标准**：

- **Given** 任意用户，**When** 调用 `GET /api/leaderboard?type=improvement`，**Then** 返回近30天成绩提升幅度最大的用户（环比上月），计算方式：本期最高分−上期最高分。

---

## US-11 管理员学员管理

### US-11.1 学员列表

**验收标准**：

- **Given** 管理员，**When** 调用 `GET /api/admin/students?page=1&size=20`，**Then** 返回分页学员列表，含：昵称、姓名（脱敏：仅显示姓氏+*）、学号（脱敏：前3后1位）、注册日期、总训练场次。
- **Given** 普通学员，**When** 访问学员列表，**Then** 返回 403。

### US-11.2 学员详情

**验收标准**：

- **Given** 管理员，**When** 调用 `GET /api/admin/students/{id}`，**Then** 返回完整字段（姓名/学号/电话/身份证号可见，参见宪法第五条）、历史预约记录、成绩历史。

---

## US-12 数据备份

### US-12.1 每日脚本 + 周备份

**验收标准**：

- **Given** 超级管理员或定时任务，**When** 触发备份，**Then** 执行 `pg_dump -Fc`，生成 `shotting_backup_YYYYMMDD.dump`，保留最近 7 份日备份和最近 4 份周备份。
- **Given** 备份失败，**Then** 发送告警（日志记录 + 可选邮件），不影响主服务。
- **Given** 需要恢复，**When** 超级管理员调用 `POST /api/admin/backup/restore`，传入备份文件名，**Then** 执行 `pg_restore`，完成后返回恢复结果摘要。

---

## 附录：接口前缀与实现技术

所有 API 路径以 `/api` 为前缀，认证通过 `Authorization: Bearer <JWT>` header。

**当前实现技术栈**：Spring Boot 3.4 + Maven + Spring Data JPA + PostgreSQL + Redis + Spring Security JWT

| 模块       | 前缀                  | 实现说明                          |
|-----------|----------------------|----------------------------------|
| 认证       | `/api/auth`          | `AuthController` + `JwtTokenProvider` |
| 设备       | `/api/devices`       | `DeviceController` + STOMP WebSocket |
| 预约       | `/api/bookings`      | `BookingController` + `CacheLockService` |
| 训练       | `/api/training`      | `TrainingController` + `NoShowCheckJob` |
| 成绩       | `/api/scores`        | `ScoreController` + `ScoreService` |
| 排行榜     | `/api/leaderboard`   | `LeaderboardController` + Redis 缓存 |
| 个人画像   | `/api/users/me`      | `UserProfileController` + `ProfileService` |
| 管理员     | `/api/admin/*`      | `admin/*Controller` + `AdminService` |
