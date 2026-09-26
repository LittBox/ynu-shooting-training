> **当前版本入口（2026-09-26）**：本文保留历史设计或当次验证结果，不代表最新实现与验收状态。当前功能、规则和待办以[版本总览](../docs/当前版本总览-v1.0.0.md)为准；旧规划中的表数、技术栈和待办不自动视为当前事实。

> 2026-09-25 联调更新：用户已明确决赛为 24 发（10＋10＋4），以下早期 3×8 草案已失效；现行接口与验证以 backend-spring/README.md 和 frontend/docs/integration-validation.md 为准。

# leaderboard-redesign.md — 排行榜数据 & 成绩构成重设计

> 适用对象：US-10 龙虎榜、US-08 成绩录入、US-09 个人画像
> 重构对象：后端 `Score` / `TrainingSession` / `ScoreRequest` 实体 + 前端 `leaderboard.vue` 演示数据
> 改动基线：当前 `feature/leaderboard` 分支，`leaderboard.vue` 仍用本地 mock 渲染

---

## 0. 现状诊断（基于 2026-09-24 代码扫描）

| 维度      | 当前实现                                                                   | 问题                                                                                          |
|-----------|--------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| 枪种      | 仅在 `Device.type`（`rifle` / `pistol`）上区分                              | `TrainingSession`、`Score`、`Booking` 都没有 `weapon` 字段，**无法按枪种筛榜**                            |
| 赛事模式    | `TrainingSession.mode`、`Score.mode` 用枚举 `final_` / `qualifying`（驼峰+下划线） | 命名不规范，序列化时是 `"final_"`，前端读取要小心；与服务端 `event_type=FINAL/QUALIFICATION` 不一致      |
| 成绩粒度    | `shotScores` JSON 数组、`groupScores` JSON 数组                          | 没有显式 `groupSize / groupCount / shotCount` 字段，前端不知道每组是 3 发还是 10 发                       |
| 排名指标    | 前端 `metricOptions`: `best` / `avg5`                                      | "近五日" 算法是取**最近 5 条成绩**（不分日期），与"近 5 个训练日"含义模糊；后端无此口径                              |
| 性别      | 写在 `Profile`，但 `Score` 没有冗余                                          | 排行榜 SQL `JOIN profiles` 每次都拖一张大表；首屏排序慢                                                  |
| 资格赛/决赛构成 | 后端 `calcGroupScores` 写死 `groupSize=3`，qualifying=4 组、final=2 组        | **和 spec.md US-08.1/8.2 不一致**：spec 规定决赛 24 发=3×8，资格赛 60 发=6×10。后端逻辑 bug                  |
| 实名 / 脱敏 | `Profile` 含真实姓名+学号；`LeaderboardService` 不存在                                | 暂无排行榜后端实现，目前所有 `leaderboard.vue` 都是写死 demo                                            |

> **结论**：当前端排行榜是"装饰"，但后端模型已经埋了"分组固定 3 发"这种错误逻辑。一旦接真接口，立刻暴露。

---

## 1. 设计目标

1. **一份成绩记录必须能独立回答所有问题**：谁（脱敏 id）、哪天、用的什么枪、哪种赛事、每发多少、每组多少、总分。
2. **前端不再本地 mock**：接入真实 REST 接口 `GET /api/leaderboard`。
3. **指标语义可声明、可扩展**：今天支持 `最佳 / 近五日均 / 进步之星 / 近 30 天最高`，未来加 `σ 稳定性 / 命中率` 不动数据。
4. **schema 迁移兼容**：V1 字段 `mode`（`final_`/`qualifying`）+ `weapon`（新增）+ `gender` 冗余；不破坏现有 demo 数据。

---

## 2. 数据结构重设计

### 2.1 `Score` 实体（重构后）

```java
@Entity
@Table(name = "scores",
       indexes = {
         @Index(name = "idx_score_recorded_at", columnList = "recorded_at"),
         @Index(name = "idx_score_user_event_weapon", columnList = "user_id,event_type,weapon")
       })
public class Score {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "session_id", unique = true, nullable = false)
    private TrainingSession session;

    /** 冗余字段：避免每次 JOIN profiles/users */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** FINAL | QUALIFICATION（去掉下划线，对齐 spec.md 命名） */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private EventType eventType;             // 替换原 mode（保留 mode 字段做 V1 兼容，见 2.4）

    /** RIFLE | PISTOL —— 关键新增字段 */
    @Enumerated(EnumType.STRING)
    @Column(name = "weapon", nullable = false, length = 10)
    private WeaponType weapon;

    /** M | F | U（unknown） —— 脱敏冗余 */
    @Column(name = "gender", nullable = false, length = 1)
    private String gender;

    @Column(name = "shot_count", nullable = false) private Integer shotCount;        // 24 / 60
    @Column(name = "group_count", nullable = false) private Integer groupCount;       // 3 / 6
    @Column(name = "group_size", nullable = false) private Integer groupSize;          // 8 / 10
    @Column(name = "total_score", nullable = false) private Double totalScore;
    @Column(name = "group_scores", nullable = false, columnDefinition = "TEXT") private String groupScores;   // JSON [g1, g2, g3]
    @Column(name = "shot_scores", nullable = false, columnDefinition = "TEXT") private String shotScores;     // JSON [s1..sN]
    @Column(name = "x_count") private Integer xCount = 0;
    @Column(name = "recorded_by") private Long recordedBy;
    @Column(name = "recorded_at", nullable = false) private LocalDateTime recordedAt = LocalDateTime.now();

    public enum EventType  { FINAL, QUALIFICATION }
    public enum WeaponType { RIFLE, PISTOL }
}
```

> 同名字段 `mode` 改名 `eventType`：保留列 `mode`（旧值 `final_`/`qualifying`），加迁移脚本做一次性映射。

### 2.2 `TrainingSession` 实体（新增字段）

```java
@Enumerated(EnumType.STRING)
@Column(name = "weapon", nullable = false, length = 10)
private WeaponType weapon;                   // 取 booking.device.type 同步

@Column(name = "event_type", nullable = false, length = 20)
@Enumerated(EnumType.STRING)
private TrainingSession.EventType eventType; // 训练开始时由前端选定（决赛/资格赛）
```

> **业务规则**：训练开始前，学员必须在 `start()` 时选定赛事模式 + 确认枪种（设备已经决定了枪种）。后端写入 `TrainingSession`，结束训练时给成绩表使用。

### 2.3 `Booking` 实体（最小改动）

不新增字段，因为 `Booking` 通过 `device.type` 间接持有枪种。但要求 `BookingServiceImpl.start()` 之前，前端在「开始训练」弹窗里强校验选定 `event_type`，避免结束后录成绩时缺数据。

### 2.4 迁移脚本 `V2__leaderboard_redesign.sql`

```sql
-- 1) scores 新增字段
ALTER TABLE scores ADD COLUMN event_type VARCHAR(20);                -- FINAL/QUALIFICATION
ALTER TABLE scores ADD COLUMN weapon     VARCHAR(10);                -- RIFLE/PISTOL
ALTER TABLE scores ADD COLUMN gender     VARCHAR(1)  NOT NULL DEFAULT 'U';
ALTER TABLE scores ADD COLUMN shot_count INT;
ALTER TABLE scores ADD COLUMN group_count INT;
ALTER TABLE scores ADD COLUMN group_size  INT;

-- 2) 历史数据一次性映射
UPDATE scores
SET event_type = CASE WHEN mode = 'final_' THEN 'FINAL' ELSE 'QUALIFICATION' END
WHERE event_type IS NULL;

UPDATE scores s
SET weapon = d.type
FROM training_sessions ts
JOIN devices d ON d.id = ts.device_id
WHERE ts.id = s.session_id AND s.weapon IS NULL;

UPDATE scores s
SET gender = COALESCE((SELECT SUBSTRING(p.real_name,1,1) FROM profiles p WHERE p.user_id = s.user_id), 'U')
WHERE gender = 'U';

-- 3) 回填 shot/group 元数据
UPDATE scores SET shot_count=24, group_count=3, group_size=8  WHERE event_type='FINAL';
UPDATE scores SET shot_count=60, group_count=6, group_size=10 WHERE event_type='QUALIFICATION';

-- 4) NOT NULL 收紧
ALTER TABLE scores ALTER COLUMN event_type SET NOT NULL;
ALTER TABLE scores ALTER COLUMN weapon     SET NOT NULL;
ALTER TABLE scores ALTER COLUMN shot_count SET NOT NULL;
ALTER TABLE scores ALTER COLUMN group_count SET NOT NULL;
ALTER TABLE scores ALTER COLUMN group_size  SET NOT NULL;

-- 5) 索引
CREATE INDEX idx_score_user_event_weapon_time
  ON scores(user_id, event_type, weapon, recorded_at DESC);

CREATE INDEX idx_score_leaderboard
  ON scores(weapon, event_type, total_score DESC);
```

### 2.5 修复 `calcGroupScores` 现有 BUG

原实现：`groupSize = 3`，决赛 2 组、资格赛 4 组 —— **违反 spec**。

重写为：

```java
private Score.WeaponType shotSizeOf(String mode) {
    return "qualifying".equals(mode)
        ? new GroupSpec(60, 6, 10)   // 60发 / 6组 / 10发
        : new GroupSpec(24, 3, 8);   // 24发 / 3组 / 8发
}
```

并补一条 `BusinessException("invalid_shot_count")` 校验：`shots.size() != shotCount`。

---

## 3. 排行榜接口契约

### 3.1 `GET /api/leaderboard`

请求：

| 参数      | 取值                                  | 必填 | 说明                          |
|---------|-------------------------------------|----|-----------------------------|
| weapon  | `RIFLE` / `PISTOL`                  | ✅  | 按枪种                         |
| event   | `FINAL` / `QUALIFICATION`           | ✅  | 赛事模式                        |
| metric  | `BEST` / `AVG5` / `PB30D` / `IMPROVE` | ✅  | 排名指标                        |
| gender  | `M` / `F` / `ALL`                   | ❌  | 默认 `ALL`                    |
| limit   | 1-100                                | ❌  | 默认 20                       |

响应（统一包络）：

```jsonc
{
  "code": 0,
  "data": {
    "weapon": "PISTOL",
    "event": "FINAL",
    "metric": "BEST",
    "asOf": "2026-09-24T20:00:00+08:00",
    "rows": [
      {
        "rank": 1,
        "userIdHash": "u_3a8f",           // 脱敏 ID，前端不再接触学号
        "displayName": "李明远",           // 来自 profiles.real_name，仅对 admin 可见；对其他学员显示昵称或化名
        "gender": "M",
        "weapon": "PISTOL",
        "event": "FINAL",
        "score": 96.4,                    // 主排序分数
        "groupScores": [9.7, 9.6, 9.7],   // 仅 metric=BEST 时返回
        "recordedAt": "2026-09-20T14:32:00+08:00",
        "delta": null                     // 仅 metric=IMPROVE 时返回
      }
    ]
  }
}
```

> **隐私**：响应中**无** `studentNo`、`phone`、`idCard`。`displayName` 走"昵称优先 / 真实姓名仅本人可见"逻辑，对其他学员降级为 `学员#3a8f`。

### 3.2 `metric` 语义定义

| metric      | 算法                                                                 | 取值范围              |
|-------------|--------------------------------------------------------------------|-------------------|
| `BEST`      | 取该选手所有成绩中**单场最高 `totalScore`**                                    | 0 ~ `shotCount*10` |
| `AVG5`      | 取**最近 5 次有效成绩**算术平均，保留 1 位小数                                       | 同上                |
| `PB30D`     | 近 30 天内单场最高分                                                          | 同上                |
| `IMPROVE`   | `PB30D - 上一个 30 天窗口单场最高分`；可负                                    | -shotCount*10 ~ + |

`AVG5` 当前前端文案"近五日平均成绩"会保留但**附 toast**：「近 5 次有效比赛算术平均，非自然日」。

### 3.3 `LeaderboardService` 关键 SQL

```sql
-- BEST
SELECT s.user_id, s.gender, MAX(s.total_score) AS score,
       (SELECT shot_scores FROM scores s2
         WHERE s2.user_id=s.user_id AND s2.weapon=:w AND s2.event_type=:e
         ORDER BY s2.total_score DESC LIMIT 1) AS shot_scores,
       (SELECT recorded_at FROM scores s3
         WHERE s3.user_id=s.user_id AND s3.weapon=:w AND s3.event_type=:e
         ORDER BY s3.total_score DESC LIMIT 1) AS recorded_at
FROM scores s
WHERE s.weapon=:w AND s.event_type=:e
  AND (:gender='ALL' OR s.gender=:gender)
GROUP BY s.user_id, s.gender
ORDER BY score DESC
LIMIT :limit;
```

`AVG5` / `PB30D` / `IMPROVE` 走 `score_user_event_weapon_time` 索引逐项算。

---

## 4. 前端数据模型与状态机

### 4.1 状态变量重写

```ts
data() {
  return {
    activeWeapon: 'pistol',        // WeaponType.PISTOL
    activeEvent:   'FINAL',        // EventType.FINAL —— 新增
    activeMetric:  'BEST',         // MetricType.BEST —— 新增
    activeGender:  'ALL',          // 保留

    metricOptions: [
      { value: 'BEST',     label: '最佳成绩',   hint: '单场最高' },
      { value: 'AVG5',     label: '近五次平均', hint: '最近 5 场均值（非自然日）' },
      { value: 'IMPROVE',  label: '进步之星',   hint: '近 30 天 vs 上 30 天' }
    ],

    rows: [],                      // 服务端返回，删除 athletes 演示数据
    loading: false
  }
}
```

### 4.2 视图结构

```
[ 枪种 Tab：手枪 | 步枪 ]
[ 赛事 Tab：决赛 | 资格赛 ]      ← 新增
[ 指标 Tab：最佳 | 近五次 | 进步之星 ]
[ 性别：全部 | 男 | 女 ]

──── 决赛 TOP 20 ────
🥇 用户#a1  97.2环   (9.7, 9.6, 9.9)
🥈 用户#b7  96.8环   ...
🥉 ...

──── 资格赛 TOP 20 ────
（同上）
```

> **关键变化**：去掉"资格赛 / 决赛"两个并列大区，改为**两个独立赛事 Tab**，避免一眼看出"两种赛事并发存在"导致的歧义。当前截图里 "资格赛" 区直接显示 "决赛 / 资格赛" 双榜，会让人误以为同一场成绩可同时进两个榜。

### 4.3 联调流程

```ts
watch: {
  activeWeapon() { this.fetch() },
  activeEvent()  { this.fetch() },
  activeMetric() { this.fetch() },
  activeGender() { this.fetch() }
},
methods: {
  async fetch() {
    this.loading = true
    const { data } = await uni.request({
      url: `/api/leaderboard`,
      method: 'GET',
      header: { Authorization: `Bearer ${uni.getStorageSync('token')}` },
      data: {
        weapon:  this.activeWeapon.toUpperCase(),
        event:   this.activeEvent,
        metric:  this.activeMetric,
        gender:  this.activeGender,
        limit:   20
      }
    })
    this.rows = data.data.rows
    this.loading = false
  }
}
```

---

## 5. 资格赛 / 决赛的成绩构成规则（公开文档）

| 维度       | 资格赛                                  | 决赛                                       |
|----------|---------------------------------------|-------------------------------------------|
| 枪种       | 步枪 / 手枪均可，按设备决定                     | 同左                                        |
| 总发数      | **60 发**                               | **24 发**                                   |
| 组数 × 每组 | **6 组 × 10 发**                         | **3 组 × 8 发**                              |
| 计分组      | 取每组总和，6 组写满                         | 取每组总和，3 组写满                              |
| 总分计算    | `Σ shot`（满分 600）                       | `Σ shot`（满分 240）                           |
| X 环      | 命中 10.9 环及以上记为 1 个 X，统计入成绩卡         | 同左                                        |
| 录入入口    | 管理员后台 → 训练记录 → 「录入资格赛」             | 管理员后台 → 训练记录 → 「录入决赛」                |
| 录入窗口    | 训练结束后 **7 天** 内（超出需 admin 解锁）        | 同左                                        |
| 同一选手同一天 | 不限场次（每天上限仍按预约规则 3 场）              | 同左                                        |

> 写入 `docs/QAmd.md` 新增 **Q11：资格赛/决赛成绩构成**，用户确认后纳入宪法附录。

---

## 6. 实施拆分（建议 PR 顺序）

| PR      | 内容                                            | 影响范围                          | 估时   |
|---------|-----------------------------------------------|-------------------------------|------|
| PR-1    | V2 SQL 迁移脚本 + `Score` 实体新增字段                  | 后端 entity / Flyway           | 30 m |
| PR-2    | `ScoreRequest` 接收 `eventType / weapon / gender` | dto / `TrainingServiceImpl`    | 30 m |
| PR-3    | 修复 `calcGroupScores` 按 spec 实现 + 单测            | service + test                 | 30 m |
| PR-4    | `LeaderboardService` 4 个 metric 实现 + 单测          | service + test                 | 90 m |
| PR-5    | `LeaderboardController` + 缓存（Redis 60s）         | controller                     | 30 m |
| PR-6    | 前端 `leaderboard.vue` 接真实接口、去 mock           | frontend                       | 60 m |
| PR-7    | 演示数据脚本：`scripts/seed/leaderboard_demo.sql`     | 运维                            | 20 m |

合计约 **4.5 小时**，可一次性 ship 或按 PR 拆分评审。

---

## 7. 验证用例（写进 `LeaderboardServiceTest`）

1. **同分处理**：用户 A、B 都拿 96.4 环 → 按 `recordedAt ASC` 排先出场者在前。
2. **跨赛事隔离**：A 的决赛 97 不计入资格赛榜。
3. **跨枪种隔离**：A 用步枪打的 96 不出现在手枪榜。
4. **AVG5 不足 5 场**：取 3 场均值，hint 文案提示"样本量 3"。
5. **脱敏**：响应中 grep 不到 `studentNo`、`phone`、`idCard` 字段。
6. **空榜**：返回 `{ rows: [], asOf: now }`，不抛错。
7. **缓存**：相同参数 60s 内命中 Redis，不查 DB。

---

## 8. 待确认事项（须写入 QAmd）

- **Q11**：资格赛/决赛成绩构成（60/24 发、6/3 组、10/8 发）是否最终敲定？spec 已写，但需要在用户文档中重申。
- **Q12**：枪种是否允许同一学员跨枪种上榜？默认是（每人每枪种独立榜），如果改为"统一榜"会改变 UX。
- **Q13**：脱敏规则——其他学员看排行榜时是否显示"化名"（`学员#3a8f`）还是"昵称"？当前 `User.nickname` 已存在但未在初始化时填，需要决定展示策略。
