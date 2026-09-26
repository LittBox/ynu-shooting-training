> 当前首次安装步骤已按 v1.0.0 核对。已有服务器不要重复覆盖配置；优先阅读[当前部署说明](../../docs/开发部署与发布.md)。

# 腾讯云 Ubuntu 24.04 后端上传包

本包用于 `123.207.43.214` 的首次部署。已知服务器安装 Java 21、PostgreSQL 16，并已验证 `ynu_app` 能连接数据库 `ynu_shooting`，默认 schema 为 `ynu-shooting`。建表尚未确认完成。

包内包含后端程序、建表脚本、配置模板、systemd 服务文件及校验清单。没有打包本地数据库、备份、真实密钥或前端，也没有单独提供演示用户初始化脚本。JAR 保留项目原有的 integration 配置和历史种子 SQL 资源，部署服务固定使用 server profile，不启用 integration，也不执行历史种子 SQL。前端需在配置可访问的 HTTPS 接口后，另外构建并上传到微信平台。

## 文件清单

| 文件 | 用途 |
| --- | --- |
| `backend.jar` | Java 21 运行的 Spring Boot 后端 |
| `sql/schema-postgresql.sql` | 创建 11 张业务表，使用统一外键命名 |
| `config/application-server.yml` | 云端数据库校验、连接池和端口配置 |
| `config/backend.env.example` | 数据库密码、微信密钥、JWT 密钥填写模板 |
| `systemd/ynu-shooting.service` | 开机启动、重启及日志管理 |
| `scripts/check-config.sh` | 启动前检查必需环境变量，不输出密钥 |
| `SHA256SUMS` | 包内文件完整性校验 |
| `BUILD-INFO.json` | 打包时间、JAR 哈希、数据库和 AppID 标识 |

## 1. 上传、解压、校验

将 `.tar.gz` 文件上传到服务器 `/home/ubuntu/ynu-deploy/`。在该目录用实际文件名执行 `tar -xzf 文件名.tar.gz`，进入解压出的同名目录，然后执行：

```bash
sha256sum -c SHA256SUMS
```

应全部显示 `OK`。以下命令均从解压目录执行，未说明时不是在 Mac 上执行。

## 2. 建表

新数据库执行下面的脚本。已使用同版本脚本建表的数据库可重复执行；已有旧版结构需先核对迁移方案。

```bash
psql -h 127.0.0.1 -p 5432 -U ynu_app -d ynu_shooting -W \
  -v ON_ERROR_STOP=1 -f sql/schema-postgresql.sql
```

输入已设置的数据库密码，预期最后出现 `COMMIT`。检查：

```bash
psql -h 127.0.0.1 -p 5432 -U ynu_app -d ynu_shooting -W \
  -c "SELECT tablename, tableowner FROM pg_tables WHERE schemaname = 'ynu-shooting' ORDER BY tablename;"
```

应返回 13 张表，所有者都是 `ynu_app`。此处不导入本地的演示账号和预约数据。

## 3. 安装应用文件（首次部署）

创建独立 Linux 运行账号。它与数据库账号 `ynu_app` 是两个不同的账号。

```bash
if ! id ynu-backend >/dev/null 2>&1; then
  sudo useradd --system --user-group --home-dir /opt/ynu-shooting --no-create-home --shell /usr/sbin/nologin ynu-backend
fi
sudo install -d -o root -g root -m 755 /opt/ynu-shooting
sudo install -d -o root -g ynu-backend -m 750 /etc/ynu-shooting
sudo install -o root -g root -m 644 backend.jar /opt/ynu-shooting/backend.jar
sudo install -o root -g root -m 755 scripts/check-config.sh /opt/ynu-shooting/check-config.sh
sudo install -o root -g ynu-backend -m 640 config/application-server.yml /etc/ynu-shooting/application-server.yml
if ! sudo test -e /etc/ynu-shooting/backend.env; then
  sudo install -o root -g root -m 600 config/backend.env.example /etc/ynu-shooting/backend.env
fi
sudo install -o root -g root -m 644 systemd/ynu-shooting.service /etc/systemd/system/ynu-shooting.service
```

此段不会启动程序，不覆盖已存在的密钥文件。以后升级时先备份当前 JAR、配置和数据库，并停止服务再更换 JAR；不要直接套用首次部署命令覆盖运行中的程序。

## 4. 在服务器填写配置

生成 JWT 密钥：

```bash
openssl rand -hex 32
```

将输出保存到自己的密码管理器，并填写到配置的 `JWT_SECRET`。保持该值稳定，不能每次重启重新生成。

打开配置文件：

```bash
sudo nano /etc/ynu-shooting/backend.env
```

填写以下三个空值，其他参数已经设置好：

- `DB_PASSWORD`：之前为 `ynu_app` 设置的密码。
- `WECHAT_APP_SECRET`：正式 AppID `wxdeef956f530d27c6` 对应的微信小程序密钥。
- `JWT_SECRET`：刚生成的随机密钥。

文件由 systemd 读取，不使用 shell 的 `export`、变量引用或命令替换。一般值可用单引号包围，例如 `DB_PASSWORD='自行填写'`；如果密码本身含单引号，需要按 systemd EnvironmentFile 的转义规则填写。不要把密钥提交到仓库或粘贴到聊天中。nano 保存按 Ctrl+O、回车，退出按 Ctrl+X。

没有微信 AppSecret 时先停在配置步骤；启动检查会拒绝空配置。

## 5. 验证服务配置并启动

```bash
sudo systemd-analyze verify /etc/systemd/system/ynu-shooting.service
sudo systemctl daemon-reload
sudo systemctl enable --now ynu-shooting
sudo systemctl status ynu-shooting --no-pager -l
sudo journalctl -u ynu-shooting -n 80 --no-pager
```

预期服务为 `active (running)`，日志中出现 Spring Boot 的 `Started`。当前配置仅监听 `127.0.0.1:8080`，由后续 Nginx 提供对外访问。

Java 堆上限设置为 512 MB、元空间上限 192 MB，数据库连接池最大 5 个连接。JVM 实际进程内存还包括线程栈等，不等于堆上限；部署后结合服务器监控调整。

首次启动如果设备表为空，现有后端会建立手枪、步枪各一台设备；不会创建固定模拟管理员。真实用户需通过微信登录建立，教员授权后续单独操作。

## 6. 本机 HTTP 检查

```bash
curl --fail-with-body --get \
  --data-urlencode "date=$(TZ=Asia/Shanghai date +%F)" \
  http://127.0.0.1:8080/api/bookings/slots
```

预期返回成功 JSON（`code: 0`）。初次未安排教员排班时，预约不可用是正常业务状态。这只能验证服务器本机接口，真实微信登录仍需微信端 code、正确 AppSecret 及后续网络配置。

常用命令：

```bash
sudo systemctl restart ynu-shooting
sudo systemctl stop ynu-shooting
sudo journalctl -u ynu-shooting -f
```

## 7. 网络接入与后续升级

首次安装成功后，根据实际站点配置 Nginx、HTTPS 与微信合法请求域名。仓库已有历史站点修复脚本，不应未经核对直接覆盖其他业务的 Nginx 配置。

已有环境的升级、当前提醒配置与微信构建以[开发部署与发布](../../docs/开发部署与发布.md)为准。当前团队测试预设仍是 HTTP；域名、备案、关闭调试及真实消息送达是否完成，应以最新服务器／真机验收记录为准。

以下保留的是 2026-09-25 当次安装包验证结果，当时 11 表不能作为当前 13 表检查标准。

## 本地打包验证（2026-09-25）

- JDK 21 执行 Maven verify：36 项后端测试通过。
- 解压上传包后，在独立 PostgreSQL 18 临时实例执行包内建表脚本，并使用包内 JAR 和 server 配置启动成功；校验模式为 validate。
- HTTP 预约查询返回成功，模拟登录请求返回 403；数据库有 11 张表、15 个外键、0 个用户、2 台首次启动自动初始化的设备。
- 配置检查脚本对完整环境变量通过，对缺少微信密钥的情况拒绝启动；脚本通过 Bash 语法检查。
- 校验包内文件哈希，未连接或修改用户的本地业务数据库，测试后端和临时数据库均已停止。
- 测试使用随机临时密钥，未调用真实微信登录。systemd 单元仍需在目标 Ubuntu 服务器执行上述 verify 和启动检查；本地 macOS 验证不等同于云端验收。

参考：[Spring Boot 外部配置](https://docs.spring.io/spring-boot/3.3/reference/features/external-config.html)、[systemd 环境配置](https://www.freedesktop.org/software/systemd/man/latest/systemd.exec.html#EnvironmentFile=)。
