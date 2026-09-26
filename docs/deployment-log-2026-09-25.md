> **当前版本入口（2026-09-26）**：本文保留历史设计或当次验证结果，不代表最新实现与验收状态。当前功能、规则和待办以[版本总览](当前版本总览-v1.0.0.md)为准；旧规划中的表数、技术栈和待办不自动视为当前事实。

# 腾讯云部署操作记录（2026-09-25）

## 当前进度与恢复位置

### 2026-09-26 同一时段多模式成绩与本次总结：验证完成，待部署

- 用户明确同一时段可先资格赛、再决赛，要求两类成绩都能录入，并要求总结本次对话的修改位置、现状和待修复问题。
- 本轮新增 `score_attempts.mode`，每轮独立保存模式；取消训练内模式锁。最终成绩唯一约束改为 `(training_session_id, mode)`，JPA 改为训练对成绩一对多。结束、补登、恢复后的再次结束都分别取各模式最高值；个人最佳标记同时匹配训练和模式，避免另一模式误标纪录。历史两项成绩按训练 ID 去重计为一次训练。旧教练录分接口同步支持每模式一项。
- 成绩页每轮可选资格赛六组或决赛三组；切换保留当前页各模式草稿，任一模式存在未保存内容会阻止直接结束并提示。顶部、轮次最高标记、结束反馈与历史表都显示具体模式，不比较不同发数的总环数。前几轮恢复、补登和首页修复一并保留。
- 新迁移 `migrate-mixed-training-modes.sql` 回填原轮次模式，保留旧分数 / 分组 / 时间，移除旧单列唯一约束或独立唯一索引，建立训练 + 模式约束。新版 `upgrade-mixed-training.sh` 校验后停止服务、备份 JAR / 数据库、补齐先前两项迁移并迁移混合模式，再启动新后端。结构不再兼容旧单模式 JAR，迁移开始后的错误分支停止服务并提示备份位置，不盲目回退旧程序或覆盖数据库。
- 后端完整 verify 75 项，前端 41 项全部通过；隔离 PostgreSQL 18 实际 SQL、应用账号、JPA validate 下 16 项成绩 / 恢复测试通过，原两种模式数据回填、重复迁移、旧索引兼容与每模式唯一性验证通过。390px 模拟 API 验证交替录入、草稿、分别结算、补登、历史去重与恢复；截图 `dist/qa/training-mixed-rounds.png`、`dist/qa/training-mixed-history.png`。升级脚本在隔离桩环境验证成功及三类失败分支。
- 包 `dist/ynu-mixed-training-20260926.tar.gz`，46,885,629 字节，SHA-256 `269bf9c864dba761d032d19aa1eef4bc2e2bbf0e86b7ced24b4d854bd5c86c3e`；JAR SHA-256 `2ff929201077e663e68f72459a4cd50712c1930c24894b85c0e5d73b39784677`。内部哈希及归档内容校验通过，团队前端构建成功。
- 已完成 `docs/本次部署与功能修复总结-2026-09-26.md`：列明接续部署基线及本次所有功能修改、最终模式规则、修改位置、验证、部署、待修复 / 待验收 / 待确认事项。明确普通无调试登录仍未闭环；不把本地模拟测试当作真机已验收。计分上限及错分更正作为待确认规则 / 能力单列。
- 下一步：上传并执行 `sudo bash upgrade-mixed-training.sh`，再重新编译 / 预览 `frontend/dist/teamtest/mp-weixin`。无需先逐一部署旧成绩补丁。本次仍未取得服务器升级及新前端真机结果。

### 2026-09-26 误结束恢复、结束后补登和首页当前使用状态：验证完成，待部署

- 用户报告误触结束后无法继续训练或登记成绩，首页只显示可约数量。本轮按此修复，未取得本次云端部署或真机结果。
- 新增本人 `POST /api/training/resume/{sessionId}`：原时段未结束、设备无他人有效预约或训练、教练到岗时恢复原记录，不额外消耗预约次数；保留所有轮次和已发布成绩。新增可空 `training_sessions.resume_started_at`，保留首次开始时间，累计实际训练分钟并排除暂停。恢复与立即训练通过同一场馆锁处理争抢。
- 已结束训练可由本人继续补登分组成绩，更新同一条最高最终成绩，个人历史最佳及排行榜同步；低分不降低原成绩，兼容旧版教练录入成绩。过了原时段仍可补登，补登不占用设备。
- “我的 → 我的成绩与历史最佳”现在包含未登记训练（正在训练及已结束），历史成绩行也可进入补登；已完成预约入口改为“补登成绩 / 继续训练”。成绩页展示恢复按钮或不可恢复原因。
- 首页显示使用中 / 空闲 / 已预约待训练 / 维护 / 停用，可预约数作为辅助信息；按设备 ID 去重，每 15 秒刷新，离开页面停止，网络失败清除旧状态。
- 完整后端 verify 72 项、前端 40 项通过；隔离 PostgreSQL 18 在实际 SQL 迁移、应用权限及 JPA validate 下 13 项成绩恢复测试通过，重复迁移与已有数据保留验证通过。390px 浏览器模拟 API 验证独立成绩入口、结束后补登、恢复训练，以及首页使用中转空闲和网络失败清除状态。截图在 `dist/qa/training-late-entry.png`、`dist/qa/home-current-usage.png`；不视为真机验收。小程序团队构建成功。
- 更新包 `dist/ynu-training-recovery-20260926.tar.gz`，46,881,945 字节，SHA-256 `76545743529a7e77b515f257fd2aabc60041d2286028a36d0e4a13c5a4b3839b`；JAR SHA-256 `0f8dd0a54fbeeccb640d4945b08865e9366cbf673bc8d4ca06aabcc4e49ba2f3`。包内哈希和归档内容已复核，无实际环境文件或密钥。
- 下一步：上传解压，执行 `sudo bash upgrade-training-recovery.sh`（备份数据库、补齐轮次表及恢复时间列，再更新 JAR），不能只运行旧通用升级脚本。随后重新编译预览 `frontend/dist/teamtest/mp-weixin`，真机检查误结束恢复、补登更新历史最佳、首页状态刷新。原邀请码保留。

### 2026-09-26 立即训练已验收；多轮分组成绩与历史最佳待部署

- 用户确认立即训练已完成，提出训练中队员自行多轮登记、结束时取最高总成绩并同步个人历史纪录。用户进一步明确按平板分组总成绩录入：资格赛 6 个 10 发总分；决赛 10、10、4 发共 3 个总分，系统求和。已按此明确要求替换初步总环数输入方案。
- 新增本人轮次成绩接口和 `score_attempts` 表；每轮保留分组原始总分及合计，首次登记前允许选择模式，已有轮次之后保持同一训练模式。只允许本人登记，检查分组数量及各组范围；请求标识用于防止网络重试重复写入，登记与结束锁定同一会话。
- 结束训练在同一事务中选择最高合计的一轮写入现有唯一 `scores` 最终成绩；平分取较早轮次；未录入成绩时允许结束并只记时长。分组总分不能推出逐发明细，因此逐发列表保持空，不虚构数据，分组均值由分组总分计算。
- 新增本人历史成绩表和按器械 / 模式分开的历史最佳，统一读取已结束训练的最终成绩；突破纪录时自动反映新值，平分或低分不覆盖原纪录。排行榜仍每次训练只读取一份最终成绩，兼容原教练录入的历史记录。
- 前端“我的预约”的训练中记录新增登记成绩入口，结束动作进入同一成绩页；实时分组合计、多轮列表、最高轮标识、未保存提示和结束结果已实现。“我的 → 我的成绩与历史最佳”提供个人成绩表，已完成训练可查看本次各轮。
- 完整后端 verify 67 项、前端 36 项通过。使用隔离 PostgreSQL 18 和实际部署 SQL、`ynu_app` 应用权限及 JPA validate 执行 8 项成绩测试全通过；迁移重复执行、已有数据保留和新表 / 序列权限均验证。390px 浏览器模拟 API 完成决赛三组、资格赛六组、多轮保存、结束及历史页流程；小程序构建成功。测试截图位于 `dist/qa/training-rounds.png`、`dist/qa/training-history.png`，不作为云端真机成绩证据。
- 专用更新包 `dist/ynu-training-results-20260926.tar.gz`，46,878,103 字节，SHA-256 `456a5dc120b9b138e942cd2d322c5fa6ab9f9136b1ccfac0f689be278c03cca3`，JAR SHA-256 `cf4c0a212e93dec5cc64da1cb7d41242ba48aa5d9172a7c1aaf916ee0ac6ecb9`。内部全部哈希已复核；无真实环境文件或密钥。
- 下一步：上传解压新包，执行 `sudo bash upgrade-training-results.sh`。脚本先备份数据库，事务新增轮次表，再调用后端升级；本轮不能只执行旧 `upgrade-backend.sh` 跳过新表。重新编译预览 `frontend/dist/teamtest/mp-weixin`，真机确认分组录入、最高总成绩及历史表更新。

### 2026-09-26 既有身份与预约真机通过；立即训练与值班同行已完成，待部署

- 用户确认姓名正常显示、教练身份开通、值班保存成功、成员能预约开放时段。上一轮身份 / 排班 / 预约链路完成真机验收。
- 用户提出关闭调试无法登录。确认 `.env.teamtest` 仍为 `http://123.207.43.214/ynu-shooting`；微信官方网络文档说明调试跳过域名校验，正常请求要求 HTTPS 合法域名。此次复查 `shooting.testgo.icu:443` 连接失败，域名 HTTP 返回空响应；不能根据历史 302 推断本次返回内容。未取得新的 ICP 审核结果，也未配置证书或微信后台。
- 新增 `POST /api/bookings/walk-in`：已登录、资料完整、当前时段、设备可用、教练已到岗时直接进入训练。事务内建立已签到记录并启动训练，沿用结束 / 成绩流程，无新增表列。有效预约 / 已签到受保护；超签到期限未到者依原规则记爽约并释放；取消及提前完成可接续，已完成设备键即时释放且兼容旧数据。本人正在训练、同一时段重复及日 / 周次数限制继续校验。
- 日历当前时段显示可立即训练和空闲设备数；详情页新增按钮，确认页显示剩余分钟、本人到场确认和决赛 / 资格赛模式；成功显示训练已开始。提交前重查状态，服务器以锁和事务处理争抢以及教练离场竞争。
- 值班列表按时段分组，同一时段一行并排展示教练，保留各自到岗状态、本人到岗和取消操作，更多人员横向滑动。
- 后端完整 `mvn verify` 59 项通过；前端 33 项通过，团队小程序构建成功。另在隔离 PostgreSQL 18 上执行值班 / 训练 19 项全部通过，测试实例已停止（首次使用 127.0.0.1 被本机 JVM 代理影响，改用现有代理豁免的 localhost 后通过；未改生产环境）。390px 浏览器模拟接口验收同行几何及完整立即训练 UI 流程，截图在 `dist/qa/`；不将其当作云端真机结果。
- 更新包 `dist/ynu-walk-in-20260926.tar.gz`，46,859,808 字节，SHA-256 `1579b58c41e4f99c859cccd7cb1a2cd881fe6433823492ee6bbfdb169877ca59`；JAR `49e3f128399e98ae81462afd0294493ee9e1b7d950a7a335ccb83617b1bf0b02`。包内校验已全部通过，只含 JAR、升级脚本、README 和校验表。
- 下一步：上传解压执行 `upgrade-backend.sh`，沿用现有邀请码；重新编译预览前端并完成当前时段立即训练及两位教练同行的真机检查。无调试正常使用还需完成 HTTPS 域名、微信 request 合法域名及新构建上传。

### 2026-09-26 10:48 CST 新后端升级成功，等待手机业务验收

- 用户提供邀请码配置脚本后续提示及升级输出：四项包内 SHA256 校验全部 OK，原 JAR 备份至 `/var/backups/ynu-shooting/backend-upgrade.3AgNoH/backend.jar`，升级后本机业务接口返回 code 0。未收集邀请码。
- 助手经公网 Nginx 路径发送无登录凭证、缺少性别的资料校验请求，收到 HTTP 400、`请选择性别`，响应时间 `2026-09-26T02:48:41.801633367Z`。此请求未修改资料；与升级前同类请求返回 401 不同，证实新版性别校验已通过公网生效。
- 尚未取得手机姓名显示、本人邀请码激活或值班操作结果，不能将后端健康检查视为完整业务验收。
- 下一步：开发者工具重新编译、预览 `frontend/dist/teamtest/mp-weixin`；手机进入“我的 → 资料与身份”（资料缺失时为“去完善”），检查已有资料回填、补齐性别、选择教练员并输入邀请码。保存后检查实名及教练值班入口，选择未来时段，再由另一学员账号验证预约。

### 2026-09-26 姓名同步与邀请码开通：验证完成，待服务器升级

- 用户截图显示“已完成身份登记”、账号 ID 1、学员，但头像旁仍为待完善姓名。用户提供线上 JAR SHA-256 为 `7a431e901c809e8b7584ba97e043b516db813526dc14585adf9f207672f7efca`，与上一角色修复包的 JAR 不同；新版前端所需实名字段尚未由旧后端提供。服务器 SQL 粘贴缺少末尾单引号进入 `>` 续行，未取得查询结果，不能推断该账号数据库姓名或性别值；已提示 Ctrl+C 退出。
- 用户确定使用“负责人发放教练邀请码后开通”。登录和资料编辑页已加入学员 / 教练员选择，资料回填后由后端验证邀请码开通当前账号；学号不判断资格，已有教练无需重复开通，保留学员预约训练功能。此流程取代上一段中的逐账号手动授权作为主要入口。
- 增加服务器配置脚本，备份环境文件后生成 24 位随机邀请码，保存在 `COACH_INVITE_CODE`，不写入源码、前端或日志。有效现有配置保留，重复或过短配置拒绝修改。脚本的生成、保留、备份、文件权限及拒绝分支已在隔离临时目录验证。
- 激活要求已登录且资料完整；错误码不提权，每账号 15 分钟限 5 次尝试（单进程内存限流）；成功写入不含邀请码的审计，重复激活幂等，不降级已有管理员。名字同步、必填性别、实名榜单和值班入口修复一并包含。
- 本地后端 `mvn verify`：51 项通过，0 失败；前端 30 项通过，团队小程序构建成功。已核对编译产物包含身份选项、资料入口且不包含服务端邀请码配置。仍需升级后真机验收。
- 新包：`dist/ynu-coach-invite-20260926.tar.gz`（46,858,225 字节），SHA-256 `99d0727111746e1d3412ed23ba3c5c7657e564f441500350c0fa4cdb3005f2aa`；新 JAR SHA-256 `68281993088f27590ed83eb176168eebfa69d7cb866060f074d388a3f244a9b4`。包内只含 JAR、两份操作脚本、README、SHA256SUMS，全部内部哈希已验证。
- 下一步：上传并解压新包，先 `sudo bash configure-coach-invite.sh` 保存邀请码，再 `sudo bash upgrade-backend.sh`。重新预览 `frontend/dist/teamtest/mp-weixin`；进入“我的 → 资料与身份”，核对回填资料、选择教练员并填写邀请码，保存后检查实名、教练角色及值班入口。无需向助手回传邀请码。

### 2026-09-26 真机登录已成功；角色、性别与实名榜单修复待部署

- 用户确认微信登录已成功，但报告未体现教练身份、值班时间入口不可见、性别未必填及榜单使用化名。用户进一步明确：教练员是参与比赛并承担值班职责的学生，学号与资格没有直接关系；用户已提供本人登记学号用于精确定位授权账号，不将其作为自动角色判断规则。
- 代码核对：微信新用户默认 student，已有教练面板仅对 admin/superadmin 显示；生产账号尚无已验证的授权结果。新增“我的”实名、角色、账号 ID、刷新身份与编辑资料入口；教练面板上移并直接展开六个时段，结束时段禁选，保留预约训练能力。
- 性别改为前后端必填 M/F。旧 U/null 性别账号的登录及本人资料响应返回 pending；资料页回填原姓名、学号和手机号供补填，已登记学号锁定；后端预约也检查资料完整性。
- 排行榜改取 profiles.real_name，无登记姓名的记录不生成化名参加榜单；姓名以外的学号、手机号仍不进入榜单。同步更新榜单说明、登记页及个人信息说明；值班人员姓名同样取登记姓名。
- 后端 JDK 21 Maven verify：47 项测试通过；前端 30 项测试通过，团队测试包重建成功。新增验证覆盖必填性别、旧账号补填、已有 JWT 下授权/撤权即时识别、教练保留预约、实名榜单，以及用户自传 role 不得取得权限。
- 新增 `grant-coach.sh`，由服务器操作员针对确认的登记账号授权；事务内记录审计，不修改其他账号，不覆盖已有 superadmin。已用独立 PostgreSQL 18 临时实例验证定向修改、重复执行幂等、不存在账号时回滚，实例已关闭。
- 后端更新包：[ynu-team-roles-20260926.tar.gz](../dist/ynu-team-roles-20260926.tar.gz)，46,851,053 字节，SHA-256 `702dcd5c4d626418b94ea315bae17d06c79ce79fe75f474e44891c3b0768e8a6`。包内含 JAR、升级/授权脚本、说明及校验清单，内部哈希已验证。
- 当前下一步：上传新包并运行升级脚本，再对用户确认的学号执行授权脚本；开发者工具重新预览 `frontend/dist/teamtest/mp-weixin`，用户补齐性别、刷新身份、添加本人值班。尚未取得本轮云端更新、实际账号授权及真机业务验收结果。

### 2026-09-26 登录失败与 NavBar 警告已修复本地代码，待更新云端

- 用户真机报告微信登录服务暂不可用，并提供 `NavBar.wxss` 不允许标签选择器的警告。
- 公网预约查询仍为 HTTP 200。助手使用明确无效的诊断 code 请求真实登录接口，返回 HTTP 502、`微信登录服务暂时不可用，请重试`，没有创建用户。另对微信接口进行不含 AppSecret 的请求，实际响应为 HTTP 200、`Content-Type: text/plain`、JSON 错误体。
- 确认本地后端使用 `RestClient.body(JsonNode.class)` 无法处理上述响应类型。新增回归测试先复现失败，再改为读取 String 并用 ObjectMapper 解析 JSON；同时增加只记录异常类型或数字 errcode 的日志，避免记录含密钥/code 的 URL、异常消息或响应正文。没有改成模拟登录。
- JDK 21 执行 Maven verify，42 项测试通过（原 36 项及新增 6 项微信响应测试）。真实 AppSecret 有效性、服务器出站网络与最终真机登录仍须云端更新后确认。
- `NavBar.vue` 内 `.brand-mark view` 改为 `.brand-mark__inner` 类选择器，重新构建 `frontend/dist/teamtest/mp-weixin` 成功，并确认生成的 WXSS/WXML 使用新类。
- 更新包：[ynu-login-fix-20260926.tar.gz](../dist/ynu-login-fix-20260926.tar.gz)，46,849,372 字节，SHA-256 `b632e5b2c6410ba9c7b3050065d02a06633e6aeb0844469d86b62bc4d59631a6`。已检查压缩包成员和内部哈希；仅含 JAR、升级脚本、说明与校验表，不含真实密钥。
- 升级脚本先校验并备份原 JAR，停止服务后替换，启动并检查本机业务接口；失败时恢复旧 JAR 并尝试启动。不执行建表、迁移或改动密钥/Nginx。脚本已通过 Bash 语法检查，尚未在服务器执行。
- 当前下一步：用户将更新包上传到服务器 `/home/ubuntu/ynu-deploy/`，解压并执行包内 `upgrade-backend.sh`，然后重新预览前端测试包并真机登录。仅重新编译前端无法让服务器获得后端修复。

### 2026-09-26 优先团队内部测试：专用测试包已构建

- 用户明确当前目标是先让团队成员使用，域名、HTTPS 等后续完善；当前转为团队内部真机调试流程。
- 已核对微信官方网络文档“跳过域名校验”：开发者工具临时开启对应选项及手机开启调试模式时可以跳过服务器域名校验。成员需自行在手机端开启调试，不能把此路径视为正常发布验收。
- 新增 `frontend/.env.teamtest` 与 `npm run build:mp-weixin:teamtest`，构建到 `frontend/dist/teamtest/mp-weixin`，连接 `http://123.207.43.214/ynu-shooting`，启用真实微信登录并禁用模拟登录。
- 构建已成功；检查产物 AppID 为 `wxdeef956f530d27c6`、API 为公网 IP、模拟登录已关闭，未使用 localhost。本地 AppSecret 不进入前端。
- 当前尚未上传新包、尚未取得真实微信登录结果。自动操作开发者工具时应用连接失败，需要用户导入上述目录，先手机预览登录，再上传并设为体验版、添加成员。内部 HTTP 测试应使用虚构资料；后续还需指定教员授权、排班与到岗以完成预约闭环。
- 同轮域名进度：用户已添加 `shooting` A 记录；08:53 CST 递归 DNS 与 DNSPod 权威 DNS 均返回 `shooting.testgo.icu → 123.207.43.214`。HTTP 域名接口返回 302，指向腾讯云 `webblock.html` 备案提示页；公网 HTTPS 443 连接失败。证书与备案尚未完成，暂不继续以此阻塞内部测试包准备。
- 操作说明已同步到 [微信上传说明](../frontend/docs/wechat-upload.md)。

### 2026-09-26 08:47 CST 转发恢复，公网复验成功

- 用户服务器本机通过 Nginx 请求 `/ynu-shooting/api/bookings/slots?date=2026-09-26`，返回 HTTP 200、`Server: nginx/1.24.0 (Ubuntu)`、`code: 0` 和 12 条设备时段；业务时间戳 `2026-09-26T00:47:00.568982868Z`。
- 助手从 Mac 显式绕过代理请求公网 `123.207.43.214` 同一接口，返回 HTTP 200、`code: 0` 和 12 条设备时段，时间戳 `2026-09-26T00:47:33.741610806Z`。本次公网 HTTP → Nginx → 后端链路已复验成功。
- 用户未贴出修复脚本执行及备份路径输出，因此可以确认转发恢复，但未独立核实备份文件和其他路径行为。
- 同次 DNS 查询 `shooting.testgo.icu` 仍为 `NXDOMAIN`。下一步在 `testgo.icu` 的 DNSPod 记录中添加 `shooting` 的 A 记录，值为 `123.207.43.214`；随后准备证书及 HTTPS。备案审核尚未取得通过结果，微信合法域名与真实登录未验收。

### 2026-09-26 修复方案已准备，待服务器执行

- 用户提供站点完整内容，仅包含 `server { listen 80; server_name _; return 444; }`。
- 准备 [Nginx 修复脚本](../backend-spring/deploy/repair-nginx-shooting.sh)：仅接受上述已核对的配置，备份实际配置文件到 `/var/backups/ynu-shooting/`；将 `return 444` 移到默认 `location /`，新增 `/ynu-shooting/` 到 `127.0.0.1:8080/` 的代理。
- 此方案仅恢复射击接口路径，原站点其余路径继续返回 444；与 9 月 25 日代理原 8888 服务的留档方案不同。
- 脚本使用 `nginx -t` 后重载；校验或重载失败时恢复备份。尚未在服务器执行，尚未取得修复后的接口结果；HTTPS、真实微信登录与备案仍待完成。

### 2026-09-26 已定位空响应：Nginx 站点直接返回 444

- 用户提供显式 `--noproxy '*'` 请求结果，服务器本机 80 端口仍返回空响应，排除了该次 curl 使用代理的影响。
- `nginx -T` 配置检查通过，加载 `/etc/nginx/sites-enabled/patern-library`；配置摘要显示 `listen 80;`、`server_name _;`、`return 444;`，未显示射击项目的 location 或 proxy_pass。
- `ss` 输出确认 `0.0.0.0:80` 由 nginx 监听。结合后端 8080 返回成功，当前空响应与站点主动断连配置一致。
- 服务器当前配置与本地留档的路径共存配置不同，变更原因和时间尚未确认。下一步读取该站点完整内容，再准备最小范围修复；尚未覆盖配置、恢复原 8888 站点访问或重载 Nginx。

### 2026-09-26 08:40 CST 接口复查：后端成功，80 端口空响应

- 用户在服务器直接请求 `127.0.0.1:8080/api/bookings/slots?date=2026-09-26`，返回 HTTP 200、`code: 0`、12 条设备时段，响应时间戳为 `2026-09-26T00:40:27.628413969Z`。后端预约查询已复验成功；无排班、无教员到岗，各时段可预约数为 0。
- 同机请求 `127.0.0.1/ynu-shooting/api/bookings/slots`，携带 `Host: 123.207.43.214`，返回 `curl: (52) Empty reply from server`。问题排查聚焦 80 端口的访问路径、监听进程和 Nginx 站点配置，不能归因于公网安全组或认定为备案拦截。
- 下一步：显式绕过 curl 代理复查，读取 Nginx 配置摘要并确认 80 端口监听进程。默认站点或 `return 444` 仅为待验证假设，尚未修改或重启服务。

### 2026-09-26 用户补充：服务运行，备案审核中

- 用户粘贴 `systemctl is-active ynu-shooting nginx` 输出，包含两个 `active`，表明这两项服务在此次检查时运行。
- 同一消息中的 curl 地址混入 Markdown 链接格式，且没有 HTTP 状态行或业务 JSON。尚未取得此次服务器本机后端与 Nginx 接口验收结果，需使用纯文本单行命令重新检查。
- 用户提供腾讯云备案页面说明：预计 1–2 个工作日内电话审核，第一次未接通后 1 小时内再次拨打，两次未接通将导致驳回。这是审核中状态，不代表备案已通过；所贴片段未显示域名，仍应在订单中核对是否为 `testgo.icu`。
- 页面所示“公安联网备案授权已完成”不等于公安联网备案完成；页面提示管局通过后取得数据码，再到公安联网备案平台办理。此处仅记录页面状态，不保存联系人、手机号或授权链接。
- 下一步：核对备案订单域名并等待审核，同时完成两级服务器本机 HTTP 检查；后续继续子域名解析、HTTPS 和微信体验版验证。

### 2026-09-26 08:35 CST 恢复检查

- 已读取本记录与前后端部署配置；下方 9 月 25 日成功结果为历史证据。
- 本机 DNS 查询：`testgo.icu` 仍指向 `123.207.43.214`；`shooting.testgo.icu` 返回 `NXDOMAIN`。
- 从 Mac 两次请求公网预约接口均返回 `curl: (52) Empty reply from server`，未收到 HTTP 状态码或业务 JSON；详细输出显示 TCP 80 连接建立。根域名 HTTP 请求也返回空响应。尚不能据此认定后端停止或存在备案拦截，需结合服务器本机接口、Nginx 和网络路径检查。
- 用户确认“不确定，需要先查询”域名备案状态。尝试打开工信部查询网站超时，未取得备案结果，不能写作“未备案”。
- 前端 `.env.local` 仍指向 `http://127.0.0.1:8080`，尚未配置真实微信登录。本次没有修改云端配置或重新构建上传。
- 当前下一步：查询 `testgo.icu` 的网站 ICP 备案状态，并在腾讯云服务器检查服务及两级本机 HTTP 接口；随后继续子域名、HTTPS、微信合法域名及体验版流程。

### 2026-09-25 历史进度

**当前进度：后端、Nginx 和公网 HTTP 均已验证。用户提供已有域名 testgo.icu，根域名已解析到本服务器；建议使用 shooting.testgo.icu，等待确认备案状态并新增子域名解析，之后配置 HTTPS。**

本记录依据本地文件检查及用户粘贴的服务器输出整理。云端命令由用户在腾讯云网页终端执行，助手没有直接连接服务器。用户曾要求暂停存档，随后已继续上传和建表。

| 阶段 | 状态 | 证据或说明 |
| --- | --- | --- |
| 正式 AppID 替换 | 已完成 | 源码与发布产物均为 `wxdeef956f530d27c6` |
| 小程序包体积优化 | 已完成 | 发布目录约 0.50 MB，图片压缩、按需注入、JS 压缩 |
| 服务器环境检查 | 已完成 | Ubuntu 24.04、x86_64、已有 Nginx |
| Java 21 安装 | 已验证 | 用户返回版本信息 |
| PostgreSQL 安装与运行 | 已验证 | 16.15，`16/main` 集群 `online` |
| 项目数据库、账号 | 已验证 | `ynu_app` 能通过密码连接 `ynu_shooting` |
| 默认 schema | 已验证 | 新连接返回 `ynu-shooting` |
| 服务器上传包 | 已完成本地打包与验证 | JAR、SQL、配置模板、systemd 文件与校验清单；见下方记录 |
| 上传与解压部署包 | 已验证 | 用户提供包内全部 8 个文件的 SHA-256 校验 OK |
| 创建 11 张业务表 | 已验证 | 用户查询返回 11 行，所有者均为 `ynu_app` |
| 安装应用文件与配置模板 | 已验证 | 用户列出 `/opt/ynu-shooting/` 和 `/etc/ynu-shooting/` 文件及权限 |
| 启动配置与后端服务 | 已验证启动 | 配置预检查成功，server profile，PostgreSQL 连接成功；微信密钥有效性尚未验证 |
| 服务器本机 HTTP 接口 | 已验证 | `/api/bookings/slots` 返回 HTTP 200、code 0，含两台设备共 12 个设备时段 |
| Nginx 项目反向代理 | 已验证 | `/ynu-shooting/` 转发到 8080，服务器查询返回 HTTP 200、code 0 |
| 公网 HTTP 接口 | 已验证 | 助手从 Mac 请求公网 IP，返回 HTTP 200、code 0、12 条设备时段 |
| 域名、HTTPS | 待配置 | 用户找到已有域名 testgo.icu；根域名 A 记录已确认，子域名及证书未配置，备案状态待确认 |
| 微信体验版上传、团队真实登录 | 待完成 | 尚未取得成功结果 |

下一步使用用户已有域名，确认备案状态，新增项目子域名解析并配置 HTTPS。现有后端与代理无需重复安装或重启。

### Nginx 检查结果

用户执行 `sudo nginx -t` 返回 `syntax is ok` 和 `test is successful`。生效主配置为 `/etc/nginx/nginx.conf`，包含 `/etc/nginx/conf.d/*.conf` 与 `/etc/nginx/sites-enabled/*`。目前看到站点 `/etc/nginx/sites-enabled/patern-library`，监听 `80`，`server_name _`。

检查初期未修改 Nginx，先读取站点完整路由。直接添加以公网 IP 为 server_name 的新站点可能改变现有 IP 请求的归属，因此没有采用此方案。

随后用户提供完整站点：唯一 `location /` 转发到 `http://127.0.0.1:8888`，保留 Host、X-Real-IP、X-Forwarded-For、X-Forwarded-Proto 和 WebSocket Upgrade 头。用户粘贴内容中的 Markdown 围栏不是 Nginx 配置本身。

随后采用路径共存方案，现已验证生效：

- 保留原有 `location /` 及其设置。
- 新增 `location ^~ /ynu-shooting/`，使用 `proxy_pass http://127.0.0.1:8080/;`。末尾斜杠使前缀被替换为 `/`，例如 `/ynu-shooting/api/bookings/slots` 转发为 `/api/bookings/slots`。
- 此段提供当前前端所用 HTTP 接口；没有为射击项目额外启用 WebSocket 代理，原 8888 服务的 WebSocket 设置保留。
- 本地配置留档：[合并后的 Nginx 站点](../backend-spring/deploy/nginx-patern-library-with-shooting.conf)。它不在已上传的旧压缩包中，本次按聊天命令写入服务器，不能同时作为第二个站点启用。
- 已提供将原配置备份到 `/var/backups/ynu-shooting/` 的命令，但用户最新附件未包含备份执行结果；不能独立确认备份文件存在。
- 用户贴出的配置检查显示 `nginx -t` 成功；其终端粘贴文本有重复字符，实际代理接口响应已证明新配置生效。
- 2026-09-25 20:10:22 CST，服务器请求 `http://127.0.0.1/ynu-shooting/api/bookings/slots`（Host 为公网 IP），返回 HTTP 200、`Server: nginx/1.24.0 (Ubuntu)`、`code: 0` 和 12 条设备时段。
- 20:11:23 CST，助手从本地 Mac 执行公网只读查询 `http://123.207.43.214/ynu-shooting/api/bookings/slots?date=2026-09-25`，使用 `curl --noproxy '*'`，返回 HTTP 200、code 0、12 条记录，响应时间戳为 `2026-09-25T12:11:23.413194884Z`。验证了当次请求的公网 TCP 80 → Nginx → Spring Boot 链路；原 8888 站点功能未另行验收。
- 当前公网地址为 HTTP，仅用于接口连通性验证；前端正式接口尚未切换，域名、HTTPS、真实微信登录均未完成。

域名准备阶段已向用户询问：自行注册域名，或使用学校/项目方授权的子域名。广州服务器的域名接入还需结合腾讯云备案要求办理；尚未代为注册、购买域名或提交备案。

用户随后询问域名是否必须注册，尚未选定方案。需区分开发调试与正常体验版：调试阶段可继续用现有公网 IP 验证，正常体验版的当前 uni.request 接入方案需合法 HTTPS 域名；可以使用获得授权的现有域名/子域名，不一定新购域名。目前未因该询问修改前端接口或开启真实登录构建。

### 已有域名 testgo.icu

用户随后找到此前项目注册的 `testgo.icu`，提供域名管理截图，显示服务状态正常、DNSPod 管理解析。截图不证明已有 HTTPS 证书或 ICP 备案。

助手只读 DNS 查询结果：

```text
testgo.icu.             A    123.207.43.214
testgo.icu.             NS   cosecant.dnspod.net.
testgo.icu.             NS   ernest.dnspod.net.
shooting.testgo.icu.    A    NXDOMAIN（当次查询）
```

建议保留根域名的现有项目，为射击项目新增 `shooting.testgo.icu`：在 `testgo.icu` 的 DNSPod 记录管理中添加主机记录 `shooting`、类型 `A`、值 `123.207.43.214`，线路默认，TTL 默认。尚未操作 DNS 控制台或修改服务器域名站点。

已询问用户该域名是否完成 ICP 备案，尚待回答。后续需检查或申请覆盖 `shooting.testgo.icu` 的证书，并配置 Nginx HTTPS、微信 request 合法域名和前端接口。域名解析、证书和备案是不同事项，域名不自动附带证书或备案。

### 最新云端验证结果

用户当前目录为 `/home/ubuntu/ynu-deploy/ynu-server-20260925-192556`，已确认 `BUILD-INFO.json`、`README.md`、`backend.jar`、两份配置文件、配置检查脚本、建表 SQL、systemd 单元均校验 `OK`。

用户随后查询 `pg_tables`，在 `ynu-shooting` 下返回 `admin_schedules`、`audit_log`、`bookings`、`cancellation_log`、`devices`、`no_show_records`、`profiles`、`scores`、`training_sessions`、`user_availability`、`users` 共 11 张表，所有者全部为 `ynu_app`。

用户进一步确认应用安装结果（服务器显示 Sep 25 19:39）：

```text
/etc/ynu-shooting/application-server.yml  root:ynu-backend  640  660 bytes
/etc/ynu-shooting/backend.env             root:root         600  349 bytes
/opt/ynu-shooting/backend.jar             root:root         644  约 50 MB
/opt/ynu-shooting/check-config.sh         root:root         755  820 bytes
```

执行 `systemd-analyze verify /etc/systemd/system/ynu-shooting.service` 后，仅提供了腾讯云 `tat_agent.service` 的兼容性提示：PIDFile 使用旧目录 `/var/run/`，systemd 将其对应到 `/run/`。未显示本项目单元配置错误，未因此修改腾讯云代理服务；最终运行状态仍需启动后确认。

### 云端启动已确认（2026-09-25 19:48 CST）

用户提供 `systemctl status` 与 `journalctl` 输出，确认：

- `ynu-shooting.service` 为 `enabled`，自 19:48:25 起 `active (running)`。
- `check-config.sh` 返回 `0/SUCCESS`；三个必需密钥已通过非空等基础检查，未取得或记录具体值。
- Java 主进程 PID 为 `4095321`，以 `ynu-backend` 运行，使用 `server` profile。
- 状态采样时内存约 `304.8M`，峰值 `310.3M`，是启动时观测值，不代表压力测试结论。
- HikariPool 成功添加 PostgreSQL 连接并完成启动，JPA EntityManagerFactory 初始化成功。
- 19:48:34 日志显示 `Tomcat started on port 8080 (http)` 和 `Started ShottingBookingApplication in 8.799 seconds`。
- 日志没有显示启动错误。真实微信 code 兑换、手机访问、HTTPS 均未验证，不能由启动成功推断 AppSecret 有效。

用户已执行以下服务器检查命令：

```bash
curl -i --get \
  --data-urlencode "date=$(TZ=Asia/Shanghai date +%F)" \
  http://127.0.0.1:8080/api/bookings/slots
```

2026-09-25 19:52 CST，用户返回 HTTP 200、JSON `code: 0`、`message: ok`，响应时间戳 `2026-09-25T11:52:09.813790610Z`。包含手枪靶位 1（deviceId 1）和步枪靶位 1（deviceId 2），每台有 S1–S6 共六个时段，总计 12 条。设备状态均为 IDLE，`staffed=false`、`coachPresent=false`、`available=0`、`bookedCount=0`。尚无教员排班或到岗，预约未开放符合当前规则。此结果仅验证服务器本机 HTTP，尚未验证公网或微信访问。

## 新增记录：服务器上传包已准备

用户在存档后要求准备上传文件包，已完成本地打包。以下记录为打包阶段情况；后续云端上传与建表结果见上方最新进度。

- 上传文件：[ynu-server-20260925-192556.tar.gz](../dist/ynu-server-20260925-192556.tar.gz)，46,855,622 字节，约 46.9 MB。
- 整包校验：[SHA-256 文件](../dist/ynu-server-20260925-192556.tar.gz.sha256)。
- SHA-256：`c3d2a249fbd25214b499cc2a13dc22d0984268c300ffe81414dc06d00dde9d3a`。
- 维护中的[部署说明](../backend-spring/deploy/README.md)和[打包脚本](../backend-spring/scripts/package-server.py)可用于后续重新生成。
- Maven verify 的 36 项测试通过；解压后的 JAR 配合部署配置，在独立 PostgreSQL 18 实例启动成功，HTTP 预约查询成功，模拟登录返回 403，测试进程已关闭。
- 本地验证之后，云端建表与 systemd 启动均已验证；真实微信登录和 HTTPS 仍未验收。
- 包内未放入真实密钥、业务数据库备份或前端产物；JAR 的原有 integration 配置和历史种子资源在 server profile 下不启用或执行。

部署包已上传到 `/home/ubuntu/ynu-deploy/`，解压并校验通过。原单文件上传步骤由整包上传替代，实际执行的建表脚本为解压目录中的 `sql/schema-postgresql.sql`。

## 环境和名称

| 项目 | 值 |
| --- | --- |
| 腾讯云产品 | 轻量应用服务器，广州三区 |
| 实例 ID | `lhins-avddn5cx` |
| 实例名称 | `宝塔Linux面板-AFp8` |
| 公网 IPv4 | `123.207.43.214` |
| 规格 | 2 核、2 GB 内存、40 GB 系统盘、3 Mbps 带宽 |
| 系统 | Ubuntu 24.04 LTS，x86_64 |
| Linux 用户 | `ubuntu` |
| 终端提示符 | `ubuntu@VM-0-2-ubuntu:~$` |
| 操作方式 | 用户在腾讯云网页终端执行命令 |
| 正式微信 AppID | `wxdeef956f530d27c6`，由用户提供 |
| 云端数据库 | `ynu_shooting` |
| 数据库账号 | `ynu_app` |
| 业务 schema | `ynu-shooting` |
| 数据库地址 | 服务器本机 `127.0.0.1:5432` |

数据库密码由用户交互式设置，本记录不保存密码或任何密钥。启动预检查已确认数据库密码、AppSecret、JWT 密钥等必填值存在；真实微信 AppSecret 有效性尚未验证。

注意：Mac 本地开发使用 `localhost:5432/postgres`，云端使用 `127.0.0.1:5432/ynu_shooting`；两边的 schema 都是 `ynu-shooting`。

## 已完成：环境检查

服务器执行的检查命令：

```bash
cat /etc/os-release
uname -m
free -h
df -h /
java -version
psql --version
if command -v nginx >/dev/null 2>&1; then
  nginx -v
elif [ -x /www/server/nginx/sbin/nginx ]; then
  /www/server/nginx/sbin/nginx -v
fi
ss -lnt
```

安装前结果：

- 系统为 `Ubuntu 24.04 LTS (Noble Numbat)`，架构 `x86_64`。
- 内存总计约 1.9 GiB、可用约 1.2 GiB；Swap 约 1.9 GiB。
- 系统盘 40 GB，已用约 7.4 GB、可用约 31 GB。
- `java` 和 `psql` 命令未找到。
- Nginx 已安装：`nginx/1.24.0 (Ubuntu)`。
- 监听端口包含 80、22、8888，以及回环 DNS 的 53；当时未显示 5432。

另外已准备 [只读服务器检查脚本](../backend-spring/scripts/check-server.sh)，通过 Bash 语法检查，未确认在服务器运行过该文件。

## 已完成：安装 Java 和 PostgreSQL

本阶段提供的命令：

```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless postgresql
sudo systemctl enable --now postgresql
```

选择 JRE 是因为后端在 Mac 打成 JAR，服务器负责运行。本次未要求安装 Maven，也未重新安装 Nginx。

用户返回了以下验证命令的结果：

```bash
java -version
psql --version
pg_lsclusters
sudo -u postgres psql -c "SELECT version();"
```

关键输出原样摘录：

```text
openjdk version "21.0.12.1" 2026-08-18
OpenJDK Runtime Environment (build 21.0.12.1+1-1-24.04.4-Ubuntu)
OpenJDK 64-Bit Server VM (build 21.0.12.1+1-1-24.04.4-Ubuntu, mixed mode, sharing)

psql (PostgreSQL) 16.15 (Ubuntu 16.15-0ubuntu0.24.04.1)

Ver Cluster Port Status Owner    Data directory              Log file
16  main    5432 online postgres /var/lib/postgresql/16/main /var/log/postgresql/postgresql-16-main.log
```

数据库查询也返回 PostgreSQL 16.15、x86_64、64-bit。已确认在线；开机自启命令已提供，但未单独取得 `systemctl is-enabled` 的输出。

## 已完成：创建数据库和应用账号

以下是当时提供的流程，仅供存档；数据库和账号已存在，不要整段重复执行。

在 Linux 终端进入数据库管理界面：

```bash
sudo -u postgres psql
```

在 `postgres=#` 下执行：

```sql
CREATE ROLE ynu_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE;
\password ynu_app
```

按提示输入两遍密码，输入时不显示字符。完成后再执行：

```sql
CREATE DATABASE ynu_shooting OWNER ynu_app ENCODING 'UTF8';
\connect ynu_shooting

CREATE SCHEMA "ynu-shooting" AUTHORIZATION ynu_app;

ALTER ROLE ynu_app IN DATABASE ynu_shooting
SET search_path TO "ynu-shooting";

\q
```

### 默认 schema 问题与修正

首次验证账号能够连接，但用户返回：

```text
 current_database | current_user | current_schema
------------------+--------------+----------------
 ynu_shooting     | ynu_app      | public
```

说明连接正常，但尚未使用目标 schema；对话未进一步区分是之前 schema 未成功创建，还是搜索路径未成功设置。

随后提供以下修正命令，确保 schema 存在并设置默认搜索路径：

```bash
sudo -u postgres psql -d ynu_shooting -v ON_ERROR_STOP=1 \
  -c 'CREATE SCHEMA IF NOT EXISTS "ynu-shooting" AUTHORIZATION ynu_app;' \
  -c 'ALTER ROLE ynu_app IN DATABASE ynu_shooting SET search_path TO "ynu-shooting";'
```

搜索路径对新连接生效，因此重新连接：

```bash
psql -h 127.0.0.1 -p 5432 -U ynu_app -d ynu_shooting -W \
  -c "SELECT current_database(), current_user, current_schema();"
```

用户最后确认的成功结果：

```text
 current_database | current_user | current_schema
------------------+--------------+----------------
 ynu_shooting     | ynu_app      | ynu-shooting
(1 row)
```

## 已完成：上传脚本并建表（原操作步骤留档）

下方保留最初单文件上传方案。实际采用上述整包上传，用户已验证校验通过及 11 张表创建成功。

### 1. 创建上传目录并上传文件

在服务器执行：

```bash
mkdir -p /home/ubuntu/ynu-deploy
```

使用网页终端的文件管理，将 [schema-postgresql.sql](../backend-spring/scripts/schema-postgresql.sql) 上传到 `/home/ubuntu/ynu-deploy/`。

本地文件完整路径：

```text
/Users/admin/Desktop/云大射击小程序/YNU-Shotting-Booking/backend-spring/scripts/schema-postgresql.sql
```

已读取并核对本地脚本：包含 11 张业务表、单教室排班、到离岗字段和统一外键命名。适用于 PostgreSQL 14+，使用事务，只创建缺失表，不代替已有旧表的结构迁移。

存档时脚本 SHA-256：

```text
01fc2da7b4c2c78799aa6f226dd16f8de3fc7d567ec77a27b4d8bc5283e2da40
```

此次不导入 `init.sql`，它含本地演示用户数据。

### 2. 确认文件并建表

```bash
ls -lh /home/ubuntu/ynu-deploy/schema-postgresql.sql
```

```bash
psql -h 127.0.0.1 -p 5432 -U ynu_app -d ynu_shooting -W \
  -v ON_ERROR_STOP=1 \
  -f /home/ubuntu/ynu-deploy/schema-postgresql.sql
```

使用 `ynu_app` 执行，让新表归该账号所有。预期出现 `CREATE TABLE`、`COMMENT`，最后是 `COMMIT`。schema 已存在的提示正常；如果出现 `ERROR`，先排查再继续。

### 3. 验证 11 张表

```bash
psql -h 127.0.0.1 -p 5432 -U ynu_app -d ynu_shooting -W \
  -c "SELECT tablename, tableowner FROM pg_tables WHERE schemaname = 'ynu-shooting' ORDER BY tablename;"
```

预期以下 11 张表均归 `ynu_app` 所有：

```text
admin_schedules
audit_log
bookings
cancellation_log
devices
no_show_records
profiles
scores
training_sessions
user_availability
users
```

## 部署后续阶段与完成情况

1. 已完成：本地打包、上传后端，建立应用运行目录、账号和配置。
2. 已完成启动所需配置：数据库、JWT 密钥、微信参数及 `validate` 模式；真实微信登录待验证。
3. 已设置 JVM 内存上限、服务管理和日志，启动已验证；重启及负载表现尚未单独验证。
4. 配置现有 Nginx，准备域名、HTTPS 和微信合法请求域名。
5. 前端改用手机可访问的接口地址，启用真实微信登录，重新构建上传体验版。
6. 添加体验成员，验证登录、资料登记、教员权限、排班、到岗及训练流程。
7. 配置数据库备份并验证恢复方式。

云端部署模板使用以下非敏感参数，服务已按该部署流程启动：

```text
DB_URL=jdbc:postgresql://127.0.0.1:5432/ynu_shooting?currentSchema=%22ynu-shooting%22
DB_USER=ynu_app
DB_DDL_AUTO=validate
WECHAT_APP_ID=wxdeef956f530d27c6
AUTH_MODE=wechat
```

`DB_PASSWORD`、`JWT_SECRET`、`WECHAT_APP_SECRET` 已在服务器本地配置并通过启动预检查，值不在此记录。云端业务服务使用 `server` profile；`dev-local.sh` 用于 Mac 本地模拟登录。

前端仍有 `127.0.0.1:8080` 本机接口配置，真实微信登录尚未完成。用户曾选择“已有 HTTPS 地址”，随后明确表示“还没有域名”，以最后说明为准，目前未提供可用测试接口地址。

## 关联文件与参考

- [后端运行说明](../backend-spring/README.md)
- [数据库关联规范](../backend-spring/docs/database-schema.md)
- [小程序上传与包体积优化记录](../frontend/docs/wechat-upload.md)
- [Ubuntu PostgreSQL 安装文档](https://ubuntu.com/server/docs/how-to/databases/install-postgresql/)
- [PostgreSQL 16 psql 文档](https://www.postgresql.org/docs/16/app-psql.html)
- [PostgreSQL 16 ALTER ROLE 文档](https://www.postgresql.org/docs/16/sql-alterrole.html)
