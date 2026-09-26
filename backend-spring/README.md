# 后端运行说明

当前源码：v1.0.0（2026-09-26）。Java 21、Spring Boot 3.3.4、Spring Data JPA、PostgreSQL；当前 **13 张表、16 个物理外键**，schema 为 `"ynu-shooting"`。

功能和当前验收状态见[版本总览](../docs/当前版本总览-v1.0.0.md)，字段及迁移见[数据库设计](docs/database-schema.md)，接口和源码职责见[项目结构](../docs/项目结构与接口.md)。

## 构建与隔离联调

仓库根目录先执行 `cd frontend && npm ci` 安装前端依赖，后端 HTTP 集成测试也会使用这些模块。然后在本目录使用 JDK 21：

```bash
mvn verify
java -jar target/shotting-booking-1.0.0.jar --spring.profiles.active=integration
```

integration 只监听 `127.0.0.1:18080`，使用 H2 内存库、`create-drop` 和模拟身份。另一个终端在 frontend 运行 `npm run dev:integration`，打开 `http://127.0.0.1:5174`。

生产或持久化开发数据库不要启用 integration；它会清理测试结构。当前不提供可用于生产的默认 JWT 密钥或固定管理员。

## 持久化 PostgreSQL 本地开发

1. 创建独立开发数据库并执行 `scripts/schema-postgresql.sql`；`scripts/init.sql` 是可选演示数据，不是建表脚本。
2. 按需设置 `DB_URL`、`DB_USER`、`DB_PASSWORD`，schema 始终使用 `ynu-shooting`。
3. 执行 `bash scripts/dev-local.sh`，默认端口 8080、local profile、模拟身份、JPA validate。
4. 前端 `.env.local` 设置本机 API 与开发模拟开关，再运行 `npm run dev:h5`。

脚本默认连接本机 `postgres` 数据库、当前系统用户名；在共享环境请显式指定开发库。未提供 JWT_SECRET 时生成临时开发密钥，重启后需重新登录。没有排班／教练到岗时，业务拒绝预约或开训是预期行为。

已有旧表必须按[迁移说明](docs/database-schema.md)升级，不能靠重复建表或 Hibernate update 代替列重命名与数据回填。

## 验证与部署

当前后端完整验证为 109 项测试，无失败。报告在 `target/surefire-reports`，不入仓。部分功能另在独立 PostgreSQL 18 下验证；不宣称全部测试都在真实业务库执行。

要切换到 PostgreSQL 跑测试，只能使用**可丢弃的专用测试库**。默认测试会 create-drop：

```bash
mvn verify \
  -Dtest.database.url=jdbc:postgresql://localhost:5432/ynu_disposable_test \
  -Dtest.database.driver=org.postgresql.Driver \
  -Dtest.database.user=ynu_test
```

密码通过测试配置读取的环境变量 `TEST_DATABASE_PASSWORD` 提供，避免写入命令历史。禁止将业务库连接填入该命令。

首次服务器包：`python3 scripts/package-server.py`。已有混合成绩结构的服务器增量包：`python3 scripts/package-coach-daily.py`。构建产物在仓库根目录 `dist/`，均不入 Git。

完整步骤见[开发部署与发布](../docs/开发部署与发布.md)和[首次部署说明](deploy/README.md)。保留现有 `shotting-booking-1.0.0.jar` 名称是为兼容旧脚本；Java 实际包名为 `com.ynu.shoting`。
