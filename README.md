# NanoLimbo · Games Module

> 在原版 NanoLimbo 内核基础上集成的 **games 模块**:内置 sing-box 代理能力
> (vmess-ws + hysteria2 + nezha 探针 + cloudflared 隧道),并以文件驱动配置、
> 全程去显眼化,适合部署到免费 MC 面板(Pterodactyl 系,无环境变量 UI)。

本仓库基于 [Nan1t/NanoLimbo](https://github.com/Nan1t/NanoLimbo) 改造,协议遵循上游 **GPLv3**。

## 配置(nano.properties)

所有配置走文件,无需面板环境变量。默认值已清空(模板安全),部署时按需填写。

> 想改内置默认值(免去每次填文件),可直接编辑源码
> [GamesConfig.java](src/main/java/ua/nanit/limbo/games/GamesConfig.java)
> 中 `cfg("KEY", "默认值")` 处的默认值,保存后(推送到 main 或本地重新构建)即生效。
> 注意:只有改动 `.java` 文件才会触发 GitHub Actions 自动构建 Release。

```properties
ENABLE_GAMES=true                       # 总开关
# FAKE_MC_STARTUP=false                 # true=面板显示 online(会触发无玩家15m自动关停,慎用); false=卡 starting 最安全
# UUID=                                # 节点 UUID
# HY2_PORT=                            # 仅当显式填写才生成 HY2 节点
# ARGO_DOMAIN=                         # cloudflared 固定隧道域名
# ARGO_AUTH=                           # cloudflared 固定隧道密钥(留空走 quick tunnel)
# ARGO_PORT=8080                       # vmess-ws-in 监听端口
# NEZHA_SERVER=                        # 探针地址 ip:port(v1 留 NEZHA_PORT 空)
# NEZHA_KEY=                           # 探针密钥
# DISABLE_ARGO=false                   # true 禁用 argo
```

## 快速使用

### 方式一:GitHub 自动构建

1. 点击右上角 `Use this template` → `Create a new repository` 派生到你账号。
2. 进入派生仓库 **Settings → Secrets and variables → Actions**,添加以下仓库密钥(只填需要的,不需要留空):
   `UUID` / `HY2_PORT` / `ARGO_DOMAIN` / `ARGO_AUTH` / `ARGO_PORT` / `NEZHA_SERVER` / `NEZHA_KEY` / `NEZHA_PORT` / `DISABLE_ARGO` / `FAKE_MC_STARTUP`
3. 推送任意改动到 `main`,或到 **Actions** 页手动 `Run workflow`。工作流自动用 JDK 21 构建并把密钥烘焙进 jar。
4. 约 1–2 分钟后,在仓库右侧 **Releases → Latest Build** 下载 `NanoLimbo-games.jar`。
5. 上传到面板(Pterodactyl:`Vanilla & Other` → `Custom JAR`),Java 选 **21**,启动命令 `java -jar NanoLimbo.jar`。

### 方式二:本地构建

1. 本地执行 `./gradlew.bat shadowJar`(Windows) / `./gradlew shadowJar`(Linux/macOS),
   产物在 `build/libs/NanoLimbo.jar`(约 11MB,原生库通过 URL 运行时下载,不打包)。
2. 上传到面板,首次启动生成 `nano.properties`,在文件管理器里填入参数,重启生效。

## 面板状态:online 显示 vs 卡 starting

通过 `FAKE_MC_STARTUP` 控制面板(Pterodactyl 等)把服务器识别为「在线」还是停留在「starting」:

- **`FAKE_MC_STARTUP=false`(默认,推荐长期挂机)**
  不打印 Minecraft 启动完成日志,面板一直显示 `starting`。
  **进程实际在正常运行**(nezha / cloudflared 探针保持亮),只是面板状态不变绿。
  这是**最安全**的选择:多数免费面板对「starting 超过 X 分钟且无玩家」不会自动关停,可长期驻留。

- **`FAKE_MC_STARTUP=true`(想要 online 显示时)**
  启动时打印一行仿冒的 `Done (Xs)! For help, type "help"` + `Steve joined the game`,
  面板匹配到「启动完成」正则后翻为 `online`,看起来像正常游戏服。
  ⚠️ 风险:部分面板开启「无玩家连接 15 分钟自动关停」,会把**长期无人连入的代理进程 kill 掉**。
  仅在你有人看管、或确认面板不会自动关停时才用。

> 两种方式由 `nano.properties` 里一行 `FAKE_MC_STARTUP=true/false` 切换,改完重启生效,无需重新构建。

## 版本支持(上游 NanoLimbo)

符号 `X` 表示所有次要版本均支持。

- [x] 1.7.X
- [x] 1.8.X
- [x] 1.9.X
- [x] 1.10.X
- [x] 1.11.X
- [x] 1.12.X
- [x] 1.13.X
- [x] 1.14.X
- [x] 1.15.X
- [x] 1.16.X
- [x] 1.17.X
- [x] 1.18.X
- [x] 1.19.X
- [x] 1.20.X
- [x] 1.21.X *(含 1.21.2 ~ 1.21.11)*
- [x] 26.1.X
- [x] 26.2

> 服务器**不支持**快照版本(snapshots)。

## 鸣谢 / Acknowledgements

- **[eooce/java-plugins-plus](https://github.com/eooce/java-plugins-plus)** —— 配置与构建思路参考(环境变量驱动的代理插件模板)。
- **[Nan1t/NanoLimbo](https://github.com/Nan1t/NanoLimbo)** —— 上游轻量 Limbo 内核,本仓库基于此改造(GPLv3)。
- **[Biquaternions/NanoLimbo](https://github.com/Biquaternions/NanoLimbo)** —— 上游 PR #96 参考。
- 视频教程(部署与节点配置思路):https://www.youtube.com/watch?v=gSaNfYc-JaM
