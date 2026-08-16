# DSH Mobile — DeepSeek Harness 移动客户端 (Kotlin Multiplatform)

在手机上原生访问局域网 / Tailscale 中的 [DeepSeek Harness](https://github.com/deepseek-ai/deepseek-harness)，
通过 DSH 官方的 HTTP RPC + WebSocket 事件流协议工作，替代移动浏览器访问 dsh web GUI。

## 功能总览

### 连接
- 服务器地址记忆、最近连接一键重连、启动自动连接
- 自动重连（指数退避 + 抖动），断线状态实时可见
- **403 信任围栏诊断引导**：远程访问被拒时给出 --host 0.0.0.0 / --trusted-host 配置指引

### 会话
- **按工作区分组**的会话列表（组头可折叠 / 展开），未分组与已归档独立成组
- 会话状态徽章：**工作中**（转圈动画）/ **任务完成**（✓），事件流实时更新
- 会话标题（来自 projections）、工作目录、更新时间
- 全文搜索（session.search）、分组标题栏操作：组内新建会话 / 重命名工作区 / 删除工作区
- FAB 弹菜单：选择工作区开新会话 / 直接新建 / 新建工作区（输入 PC 目录路径）

### 聊天（对话视图）
- 流式消息渲染：text / reasoning / tool-call delta 聚合，进入自动定位最新消息（reverseLayout 贴底）
- **思考过程 / 工具调用 / 工具结果全部默认折叠**，点击展开详情（与桌面版一致）
- **Deep diving...** 状态条：open turn 时显示，超过 15 秒带计时
- 轮次/步数指示 + **上下文占用进度条**（contextPressure / contextBreakdown projections）
- **Goal 条**：目标文本 + 轮次进度（goal projection）
- 队列（待发送消息）：点击编辑、立即发送（steer）、删除
- 审批弹窗（允许一次 / 拒绝）、问题弹窗（逐题分步 / 多选 / 自定义输入 / 跳过）
- 图片查看、取消回合、上滑自动分页加载更早消息

### 模型与权限
- **模型选择**（桌面版两级菜单）：提供商分组 → 模型列表 → 思考模式级别（含"提供商默认"）
- 顶部触发器显示 `模型名 · 思考模式`
- **权限预设切换**（🔒）：read-only / workspace-write / danger-full-access，发送 /permission 命令
- 后台任务左滑面板（当前会话任务实时状态）

## 架构（参考 Now in Android 规范）

```
com.dshclient.app/
├── core/
│   ├── model/          # 协议数据模型：RPC 信封、DTO、会话事件、surface fold
│   ├── network/        # DshConnection：HTTP RPC + WebSocket 双事件流 + 重连
│   └── designsystem/   # M3 Expressive 风格主题（柔和色板 + 大圆角）
├── data/               # DshRepository 接口 + DshStore 实现 + ChatState（事件折叠/并发安全）
├── feature/            # 按功能分屏：connect / sessions / chat / settings
└── App.kt / AppViewModel.kt   # 应用级状态与导航（单向数据流）
```

- **单向数据流**：UI 事件 → ViewModel 方法 → Repository → 网络层；UI 状态经 UiState + StateFlow
- **Repository 模式**：UI 只依赖 DshRepository 接口
- **协议精确对应服务端**：与 @deepseek-ai/dsh-host-apiproxy 的 zod schema 逐字段对齐
- **并发安全**：会话折叠状态（ChatState）全部修改经 Mutex 串行化；surface fold 对截断窗口的 replace 做去重，LazyColumn key 永不冲突

## 桌面端配置（Tailscale / 局域网）

DSH 出于安全默认只监听 127.0.0.1，且禁用了 0.0.0.0 绑定。远程访问需要：

```bash
# 1. 允许监听所有网卡（DSH_PKG_ALLOW_LAN=1 显式放行）
# 2. 声明信任的访问来源（--trusted-host 可重复）
DSH_PKG_ALLOW_LAN=1 dsh web --host 0.0.0.0 --port 3080 --trusted-host 100.x.x.x
```

- --trusted-host 填手机访问所用的地址（Tailscale IP 或局域网 IP），不加会被信任围栏拒绝（403），App 内有排查指南
- 特权接口（设置读写、凭据、目录选择、路径打开、模型探测）即使配了 trusted-host 也强制仅本机回环可用 —— DSH 服务端安全边界

## 构建、测试与发布

```bash
# 构建 APK
./gradlew :androidApp:assembleDebug
# 产物: androidApp/build/outputs/apk/debug/androidApp-debug.apk

# 全部测试（协议编解码、surface fold、消息投影 + 真实实例集成）
# 集成测试需本机先启动 dsh web（dsh web --port 3080）
./gradlew :composeApp:testAndroidHostTest

# 发布：构建 + 同步到本地 HTTP 下载服务器（手机经 Tailscale 下载安装）
powershell -ExecutionPolicy Bypass -File scripts/publish.ps1
```

## 技术栈

Kotlin 2.3 · Compose Multiplatform 1.11 (M3 Expressive 风格) · Ktor 3.4 (OkHttp 引擎)
kotlinx.serialization / coroutines / datetime · multiplatform-settings · Android (minSdk 26)

## 协议速览（对接 dsh web 0.1.0-rc.6）

- `POST /api/<method>`：`{type:"client-request", rpcId, method, payload}` → `server-response`，Content-Type 必须 application/json
- `POST /api/respond`：应答审批 / 用户问题（client-response，回显帧的 rpcId）
- `WS /api/events.mux`：会话事件流（打开即推 session/subscribed 基线；session/event / queue / jobs / approval / question / projection 帧）
- `WS /api/events.host`：主机事件流（session 增删与状态、workspace 变更、归档集变化）
- `GET /api/session.export?sessionId=`：会话日志 ZIP 下载
- 会话渲染基于 **surface fold**：user/message、assistant/message、tool/result 三类事件带 surfaceOp（append / replace），与 dsh-session 的 foldSurface 语义一致；超大日志进入时尾部截断 1500 事件保证秒开，上滑自动翻页补齐

## 已知限制

- 服务端特权接口（settings / credentials / 目录选择等）仅回环可用，移动端访问返回 403（服务端安全设计）
- iOS 目标已预留（iosMain 源集），需要 macOS 构建
