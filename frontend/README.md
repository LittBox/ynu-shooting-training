# 前端：云大射击训练管理小程序

uni-app、Vue 3 与 Vite 工程。当前七个页面覆盖首页、预约、综合榜、个人中心、登录、个人成绩及教练按日管理；组件与接口分工见[项目结构](../docs/项目结构与接口.md)。

## 安装、测试与本地运行

```bash
npm ci
npm test
npm run dev:integration
```

integration 前端地址 `http://127.0.0.1:5174`，连接单独启动的 H2 后端 `127.0.0.1:18080`。持久化本地开发使用 `.env.local` 配置 `VITE_API_BASE_URL=http://127.0.0.1:8080`、`VITE_ENABLE_MOCK_LOGIN=true`、`VITE_AUTH_MODE=disabled`，再执行 `npm run dev:h5`。

| 命令 | 用途／产物 |
| --- | --- |
| `npm test` | 51 项规则、状态与服务测试 |
| `npm run dev:mp-weixin` | 微信开发模式，`dist/dev/mp-weixin` |
| `npm run build:mp-weixin` | 常规微信构建，`dist/build/mp-weixin` |
| `npm run build:mp-weixin:teamtest` | 项目团队测试，`dist/teamtest/mp-weixin` |
| `npm run dev:h5` | H5 开发服务，API 由环境配置 |
| `npm run dev:integration` | 连接隔离测试后端的 H5 开发服务 |
| `npm run build:h5` | H5 构建 |

## 环境与微信配置

`.env.example` 是配置示例；`.env.integration` 是隔离联调预设；`.env.teamtest` 是本项目的 HTTP 团队测试预设，不能直接当作正常发布配置。只有自己的 `.env.local`／`.env.production.local` 等本地配置不入仓。

正常微信构建使用实际可访问的 HTTPS API、`VITE_AUTH_MODE=wechat`、`VITE_ENABLE_MOCK_LOGIN=false`。微信 AppID 位于 `src/manifest.json` 的 `mp-weixin.appid`；顶层 appid 属于 DCloud。AppSecret、JWT 密钥、教练邀请码和数据库密码不放入前端。

微信开发者工具导入**对应构建目录**，不是仓库根目录。CLI 构建成功还需开发者工具编译和手机验收。前端修改后必须重新预览／上传；更新服务器 JAR 不会更新手机页面。

当前原生订阅授权需要在微信中由本人点击触发；H5 模拟接口测试不能代替真实送达。页面轮次图仅使用已登记数据，补登时间不是实际开枪时间。

当前功能、边界与待办见[版本总览](../docs/当前版本总览-v1.0.0.md)，构建及发布步骤见[开发部署与发布](../docs/开发部署与发布.md)。本目录 docs 中带历史记录的说明保留当时结果；涉及当前版本时以这两个入口为准。
