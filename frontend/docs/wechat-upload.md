> **当前版本入口（2026-09-26）**：本文保留历史设计或当次验证结果，不代表最新实现与验收状态。当前功能、规则和待办以[版本总览](../../docs/当前版本总览-v1.0.0.md)为准；旧规划中的表数、技术栈和待办不自动视为当前事实。

# 微信小程序打包与体验版上传

## 2026-09-26 团队内部真机测试

当前最新版为 `dist/ynu-mixed-training-20260926.tar.gz`，执行 `sudo bash upgrade-mixed-training.sh` 后重新编译 / 预览 `frontend/dist/teamtest/mp-weixin`。同一训练每轮独立选择资格赛或决赛，结束时分别取各模式最高分；补登和恢复继续兼容。脚本先停止服务并备份数据库及 JAR，补齐旧成绩迁移，再迁移混合模式结构。不能使用旧通用脚本替代，也不能在新结构上直接运行旧单模式 JAR。真机验证交替模式录入、两项最终成绩和“1 次训练 · 2 项成绩”。完整总结见 `docs/本次部署与功能修复总结-2026-09-26.md`。

上一更新包为 `dist/ynu-training-recovery-20260926.tar.gz`。执行 `sudo bash upgrade-training-recovery.sh`，先备份数据库、补齐轮次表和恢复时间列，再更新后端。重新编译 / 预览 `frontend/dist/teamtest/mp-weixin`。本轮新增误结束后继续原时段训练、结束后补登成绩、独立成绩页中的未登记记录入口，以及首页当前使用状态每 15 秒刷新。真机检查补登更新最高分和历史最佳、恢复不重复预约、他人占用时不可恢复；邀请码保留。

上一成绩更新包为 `dist/ynu-training-results-20260926.tar.gz`，须执行包内 `sudo bash upgrade-training-results.sh`（备份数据库、新增轮次表、更新 JAR），再重新编译预览。训练中在“我的预约 → 登记成绩”按平板数据填写分组总分：资格赛 6×10 发，决赛 10+10+4 发；每轮自动合计，结束后选最高轮并同步“我的 → 我的成绩与历史最佳”。已有邀请码继续有效。

用户已确认姓名、教练开通、值班保存及队员预约正常。后续新增“立即训练”和同一时段教练同行展示，使用 `dist/ynu-walk-in-20260926.tar.gz` 更新后端，再重编译本目录对应的团队测试前端；已有邀请码保持有效。立即训练入口在预约日程的当前时段，需空闲设备和教练实际到岗；确认后直接训练，结束操作仍在我的预约中。

关闭手机调试后登录失败，与本轮 HTTP IP 测试配置一致。正常使用必须完成有效 HTTPS 域名，并在微信后台配置 request 合法域名，随后将 API 基址改为该 HTTPS 域名及 `/ynu-shooting` 路径重新构建上传。当前 `shooting.testgo.icu` 的 HTTPS 尚未连通，不能只取消调试设置便视为正式接入完成。

本轮已补齐角色与资料交互：“我的”显示实名、角色及账号 ID，提供刷新身份和编辑资料；教练员可直接选择日期和六个值班时段。性别为男/女必填，旧账号未知性别需补填；排行榜使用登记的真实姓名，并同步更新个人信息说明。须同时升级后端与重编译前端。

教练员是承担额外职责的学生，学号不自动决定资格。登录页和“我的 → 资料与身份”提供学员 / 教练员选择；首次开通教练员需输入负责人发放的邀请码，服务器验证后授予权限，保留普通预约与训练功能。已有教练权限无需重复输入。

使用 `dist/ynu-coach-invite-20260926.tar.gz` 更新后端：上传、解压，先执行 `sudo bash configure-coach-invite.sh`，将服务器终端生成的邀请码妥善保存，再执行 `sudo bash upgrade-backend.sh`。邀请码只存于服务器环境文件，不进入前端；不要将邀请码发送到排查记录中。配置脚本重复执行会保留已有邀请码。若需轮换，由负责人修改服务器 `COACH_INVITE_CODE` 后重启服务，已开通的教练身份保留。

若“我的”显示登记完成却没有姓名，应先核对前后端版本。2026-09-26 用户提供的服务器 JAR 哈希与本地角色修复版不同；只更新前端不会让旧后端返回新增的实名字段。升级后先检查已有资料回填，不必清空账号。

当前优先目标是让团队先测试登录和业务流程。公网 IP 接口已通过 HTTP 验证，域名备案仍在审核，HTTPS 尚未完成。

执行 `npm run build:mp-weixin:teamtest`，在微信开发者工具导入 **`frontend/dist/teamtest/mp-weixin`**。此专用构建读取 `.env.teamtest`，连接 `http://123.207.43.214/ynu-shooting`，启用真实微信登录，禁用模拟登录；正式 AppID 为 `wxdeef956f530d27c6`。

先由管理员真机验证登录，再上传开发版本，在微信公众平台设为体验版并添加体验成员。版本备注应说明“团队内部 HTTP 调试测试”。开发者工具需允许开发环境跳过域名校验，每位成员在手机端进入小程序右上角菜单开启调试（具体入口随微信版本可能不同），重新进入后测试。仅在电脑开发工具允许跳过校验，不等于所有成员手机自动具备同样设置。

此阶段通过 HTTP 传输，资料登记使用虚构测试姓名、学号与手机号。真实微信 code 兑换与服务器 AppSecret 的有效性必须由真机登录验证，构建成功不等于登录已验收。

首次登录后还需由指定教员使用邀请码开通身份、安排排班并到岗，学员才能完成预约、签到及训练闭环。普通微信登录不会自动成为教员。

后续正常体验及发布前，完成备案、HTTPS、微信 request 合法域名配置，改用 HTTPS 重新构建，并关闭调试验证。当前测试包不能直接作为完成正常上线的证据。

依据：[微信网络文档“跳过域名校验”](https://developers.weixin.qq.com/miniprogram/dev/framework/ability/network.html)。

## 本次包体积优化

2026-09-25：发布目录由 2,418,541 字节降至 499,100 字节（约 0.50 MB），这是本地构建目录统计，最终上传包体积以微信开发者工具为准。

- 首页手枪图：PNG 1,125,062 字节 → JPEG 172,190 字节。
- 首页步枪图：PNG 1,091,760 字节 → JPEG 131,296 字节。
- 保留原始 1448 × 1086 尺寸，没有裁剪；JPEG 编码质量 80，为有损压缩。
- PNG 原图完整备份在 `docs/assets-originals/`，不进入小程序代码包。不要重新放回 `src/static`。
- `src/manifest.json` 的 `mp-weixin.lazyCodeLoading` 已设为 `requiredComponents`，启用按需注入。
- 构建的 `project.config.json` 中 `setting.minified=true`；没有配置插件；已核对所有声明组件均有 WXML 引用。
- 清理了静态资源目录中的 `.DS_Store`。

原图可在 macOS 用系统工具重新编码，例如在 frontend 目录执行：

```bash
sips -s format jpeg -s formatOptions 80 docs/assets-originals/shouqiang.png --out src/static/images/shouqiang.jpg
sips -s format jpeg -s formatOptions 80 docs/assets-originals/buqiang.png --out src/static/images/buqiang.jpg
```

目前无需为包体积拆分页面。按用户提供的微信规范，1.5 MB 是优化建议阈值，单包限制为 2 MB；四个 tabBar 页面本身须留在主包中。

## 获取、核对 AppID

1. 进入 https://mp.weixin.qq.com/ 。如果还没有小程序账号，选择注册，账号类型选“小程序”，按实际归属填写主体并完成平台要求的信息。
2. 已有正式小程序账号则直接登录，在“开发管理 → 开发设置 → 开发者 ID”查看 `AppID（小程序 ID）`；具体菜单以当前后台为准。
3. 将该值填入 `src/manifest.json` 的 `mp-weixin.appid`。顶层 `appid` 是 DCloud 应用标识，不是微信 AppID。
4. 当前配置值为用户提供的正式 AppID `wxdeef956f530d27c6`（2026-09-25 更新）。构建后还应在开发者工具的项目详情中核对，避免打开另一份旧的开发构建目录。
5. 登录微信开发者工具的微信账号，应是该小程序的管理员或已添加的开发者。

AppID 可以用于前端配置；AppSecret 只配置到后端环境变量 `WECHAT_APP_SECRET`，不要放入前端或提交到仓库。

## 构建与上传

在 frontend 目录执行：

```bash
npm run build:mp-weixin
```

在微信开发者工具中导入 `frontend/dist/build/mp-weixin`，检查项目详情的 AppID，并确认本地设置中的上传 JS 压缩已开启。这里是发布构建目录，`dist/dev/mp-weixin` 是另一份开发构建，可能仍保留旧资源；不要误用旧目录判断优化是否生效。

上传后，在微信公众平台版本管理中将对应开发版本设为体验版，添加体验成员，发送体验版二维码。注册账号、上传、设为体验版、提交审核和正式发布是不同步骤；本次仅在本地构建，没有代替用户注册或上传。

## 真机登录仍需完成的配置

- `VITE_API_BASE_URL` 必须是手机可访问的后端地址；当前 `.env.local` 的 `127.0.0.1:8080` 仅适合电脑本机联调。
- 前端设置 `VITE_AUTH_MODE=wechat` 并重新构建；后端设置 `WECHAT_APP_ID=wxdeef956f530d27c6`，并在本机环境中配置该小程序的 `WECHAT_APP_SECRET`，启用微信登录。
- 发布构建不启用开发模拟登录。团队测试构建已启用真实微信登录，用户已确认旧登录修复包登录成功；最新身份功能仍需升级后真机验收。

参考：[uni-app manifest 配置](https://uniapp.dcloud.net.cn/collocation/manifest.html)、[微信分包规则](https://developers.weixin.qq.com/miniprogram/dev/framework/subpackages/basic.html)。
