# 云大射击训练管理小程序

**YNU Shooting Training** · `v1.0.0` · 团队测试基线（2026-09-26）

面向云南大学射击训练场景的微信小程序，提供设备分时预约、教练排班与到岗、训练签到、多轮成绩登记、个人历史、按日成绩管理与微信订阅提醒。

A WeChat Mini Program for shooting training management at Yunnan University, featuring equipment booking, coach scheduling and attendance, training check-in, multi-format score tracking, daily rankings, score corrections, and subscription reminders. Built with uni-app, Vue 3, Spring Boot, and PostgreSQL.

## 当前版本

学员可以预约气手枪／气步枪、到场签到、开始或立即训练，在同一次训练中交替登记决赛和资格赛成绩，并查询训练历史。教练可以排班、确认到岗与交接，按日期、枪种和赛制查看学员排名及单次训练详情，并核对、更正错误成绩。

**教练当日排名**按训练开始的北京时间日期归属，每位学员取同枪种、同赛制当天最高一轮，同分并列。训练详情保留每轮成绩、登记时间、训练起止时间和分赛制折线图。综合排行榜与教练当日排名采用不同统计口径。

**微信提醒**由本人逐次订阅，训练和值班均提前 15 分钟提醒。已实现并本地验证，真实微信送达仍待验收。此前混合赛制登记和教练更正／训练历史功能已获用户真机确认；最新按日视图与设备未来预约修复尚未取得本轮真机验收回传。源码发布不代表服务器或小程序已更新。

## 文档入口

| 文档 | 内容 |
| --- | --- |
| [当前版本总览](docs/当前版本总览-v1.0.0.md) | 全部功能、业务规则、已验证范围、注意事项与待开发清单 |
| [数据库设计](backend-spring/docs/database-schema.md) | 13 张表、16 个外键、关系图、约束和迁移 |
| [项目结构](docs/项目结构与接口.md) | 前后端职责、页面、核心模块和接口索引 |
| [开发、部署与发布](docs/开发部署与发布.md) | 本地运行、环境配置、升级、排障和版本推送边界 |
| [版本记录](CHANGELOG.md) | 本次版本纳入的实现与验证 |
| [文档索引](docs/README.md) | 当前说明与历史部署记录的阅读顺序 |

## 技术栈

- 前端：uni-app、Vue 3、Vite；微信小程序为业务客户端，H5 用于本地联调。
- 后端：Java 21、Spring Boot **3.3.4**、Spring Data JPA、jjwt。
- 数据库：PostgreSQL，业务 schema 为 `"ynu-shooting"`；H2 用于隔离测试。
- 部署：Ubuntu、systemd、Nginx；SQL 脚本显式迁移。

当前没有引入 Redis、Flyway、独立 Spring Security 过滤器链或 STOMP。权限由 JWT 身份读取及业务层校验，预约并发由数据库事务、行锁和唯一约束保护。`.specify/` 保留早期规划，不能作为已实现功能清单。

## 快速本地联调

准备 JDK 21、Maven、Node.js 与 npm。本次验证使用 Node.js 24。先安装前端依赖（后端 HTTP 集成测试也会调用前端服务模块）：

```bash
cd frontend
npm ci
npm test
cd ../backend-spring
mvn verify
java -jar target/shotting-booking-1.0.0.jar --spring.profiles.active=integration
```

另开终端，从仓库根目录执行：

```bash
cd frontend
npm run dev:integration
```

浏览器访问 `http://127.0.0.1:5174`，后端监听 `127.0.0.1:18080`。该模式使用可重建的 H2 内存库、测试身份和测试排班；教练仍须本人确认到岗。不要将 `integration` 配置指向业务数据库。提醒在模拟身份下不可实际发送。

持久化 PostgreSQL 本地开发见 [后端运行说明](backend-spring/README.md)。

## 微信构建

```bash
cd frontend
npm run build:mp-weixin:teamtest
```

微信开发者工具导入 `frontend/dist/teamtest/mp-weixin`。当前团队测试配置连接项目的 HTTP 测试服务器，不能作为正常 HTTPS 发布配置使用。正常体验／发布需配置自己的 HTTPS API、小程序 AppID 和微信后台合法域名，并关闭调试验证。

## 仓库内容与发布边界

仓库保存源码、依赖锁文件、SQL、配置模板、测试与文档。本地数据库、真实密钥、备份、`node_modules`、`target` 和 `dist` 不入仓；部署包由源码构建生成。AppID、模板 ID 是配置标识，不等于 AppSecret。

后端原有目录 `com/ynu/shotting`、Java 包名 `com.ynu.shoting`、JAR 名 `shotting-booking-1.0.0.jar` 暂时保留兼容；仓库名称统一使用正确拼写 `shooting`。本版本不进行影响部署路径的批量重命名。

当前未选定开源许可证。本次 Git 推送仅发布源码版本，不自动部署服务器，也不代替微信上传、审核或发布。
