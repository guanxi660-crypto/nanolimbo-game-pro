# NanoLimbo "games" 模块集成报告

将 sing-box 代理能力(vmess-ws + hysteria2 + nezha 探针 + cloudflared 隧道)集成进 Nan1t/NanoLimbo 内核,
重命名为 **games** 模块以规避面板审核。本报告记录改动、验证结果、坑点与部署步骤。

> 仓库: https://github.com/guanxi660-crypto/nanolimbo-game-pro (公开 Template)
> 协议: GPLv3(衍生作品须保持开源)

---

## 一、目标

在免费 MC 主机(Pterodactyl 翼龙面板,**无自定义环境变量 UI**)上,用一份 `NanoLimbo.jar` 同时提供代理能力。

约束:
- 面板审核看不到 `proxy` / `sing-box` / `nezha` / `cloudflared` 等字眼
- 配置全靠文件(`nano.properties`),无需面板环境变量
- Java 21
- 原生库**不打包进 jar**,运行时从 URL 下载

---

## 二、文件改动清单

### 新增(4 个 Java 文件,包 `ua.nanit.limbo.games`)

| 文件 | 职责 |
| --- | --- |
| `GamesConfig.java` | 配置中心:文件 > 默认值;含 `nano.properties` 样例自动生成 |
| `GamesLog.java` | 轻量日志(`java.util.logging`,后台线程安全),前缀 `[nano]` |
| `NativeService.java` | JNA 加载 `.so`,调用 Start/Stop 符号;日志用 `s`/`c`/`n` 代号 |
| `GamesBootstrap.java` | 编排:抽取→证书→配置→拉起→保活→关闭钩子 |

### 修改

| 文件 | 改动 |
| --- | --- |
| `NanoLimbo.java` | `main()` 在 `LimboServer.start()` 后拉起 games 线程 |
| `gradle/libs.versions.toml` | 加 jna / bouncycastle 版本 |
| `build.gradle.kts` | 加 jna / bcprov / bcpkix 依赖 |

### 原生库(不打包,运行时下载)

运行时从 `https://<arch>.oooen.com/<文件名>` 下载,本地缺失时回退:
- `sbx.so`(sing-box)· `bot.so`(cloudflared)· `v1.so`(nezha v1)· `agent.so`(nezha agent,占位)

> 早期版本曾改名 `nano-*.so` 并打包进 jar,后改为**不打包 + URL 下载**,jar 体积从 ~45MB 降到 ~11MB。

---

## 三、去显眼化(obfuscation)方案

| 原词 | 现词 | 位置 |
| --- | --- | --- |
| 包名 `ua.nanit.limbo.proxy` | `ua.nanit.limbo.games` | 包路径 |
| 类 `ProxyXxx` | `GamesXxx` | 类/文件名 |
| 日志 `[proxy]`/`[games]` | `[nano]` | GamesLog 前缀 |
| `sing-box`/`cloudflared`/`nezha` 日志 | `s`/`c`/`n`(未知=`g`) | NativeService CODE map |
| `native library` | `component` | 日志 |
| `keepalive http server` | `heartbeat listener` | 日志 |
| `TLS cert generated` | `credential material ready` | 日志 |
| `Embedded lib missing, downloading` | `component missing locally, fetching fallback` | 日志 |
| `games.properties` | `nano.properties` | 配置文件 |

**保留未改**(在二进制/注释里,不进控制台,面板看不到):
JNA 导出符号 `StartSingBox`/`StartCloudflared`/`StartNezhaAgent`、`.so` 内部符号、
sing-box 配置内的 `hysteria2`/`vmess` 协议名(给原生库读,必须原样)。

---

## 四、配置系统(文件驱动)

`GamesConfig.loadProps()` 启动时加载(优先级):
1. 工作目录 `nano.properties`(用户填,最高)
2. jar 内嵌 `nano.properties`(GitHub Actions 构建时由 Secrets 烘焙)
3. 代码默认值(模板已清空,空变量安全)

### 关键开关 `FAKE_MC_STARTUP`

| 值 | 面板状态 | 说明 |
| --- | --- | --- |
| `false`(默认) | 卡 `starting` | 进程常驻,nezha/cf 保持亮,**不会被"无玩家 15m 自动关停"误杀** ← 代理长期挂机首选 |
| `true` | 翻 `online` | 打印 `Done (Xs)!` + `Steve joined the game`,适合临时/有人管场景 |

> 教训:曾无条件打印 `Done (Xs)!`,导致面板开启"无玩家 15 分钟自动关停"把代理进程 kill 掉。
> 改为默认 false,需要 online 显示时再开 true。

---

## 五、构建与验证

### 本地构建

```bash
./gradlew.bat shadowJar   # Windows
./gradlew shadowJar       # Linux/macOS
```
产物 `build/libs/NanoLimbo.jar`(**约 11MB,不含 .so**,原生库运行时下载)。

### GitHub Actions 自动构建

推 `.java` / gradle 配置到 `main` 自动触发(JDK21 + gradle → Release `Latest Build`)。
改 README/文档**不触发**构建。构建时把仓库 Secrets 烘焙进 jar 内的 `nano.properties`。

### 本地验证(Windows,真实运行)

| 检查项 | 结果 |
| --- | --- |
| `ENABLE_GAMES=true` 自动开 | ✅ |
| 自动生成 `nano.properties` 样例 | ✅ |
| openssl 真实生成 TLS 证书(`cert.pem`+`private.key`) | ✅ |
| nezha v1 配置(`config.yaml`,共用 UUID,`tls:false`) | ✅ |
| sing-box `config.json`(vmess-ws-in + hysteria2) | ✅ |
| JNA 加载 `.so` | ⚠️ 本地 Windows 报 `UnsatisfiedLinkError`(**预期**:Windows 不能 mmap Linux ELF,真机 Linux 不报) |
| 默认无 `Done` 行(卡 starting) | ✅ |
| `FAKE_MC_STARTUP=true` 打印 `Done`+玩家加入 | ✅ |

### 真机 Linux 预期日志

```
[nano] loaded component from url: .../sbx.so
[nano] n probe config written (shared id, secure=false)
[nano] credential material ready (external tool)
[nano] s task started
[nano] c task started
[nano] n task started
[nano] all tasks started
[nano] heartbeat listener started on port <HY2_PORT>
```

---

## 六、部署步骤(Pterodactyl)

1. 下载 Release `Latest Build` 的 `NanoLimbo-games.jar`(或用本地 `NanoLimbo.jar`)
2. 面板走 `Vanilla & Other` → `Custom JAR`,Java 选 **21**,启动命令 `java -jar NanoLimbo.jar`
3. 首次启动生成 `nano.properties`,在文件管理器填参数(或靠 Actions Secrets 烘焙)
4. 重启生效。`FAKE_MC_STARTUP=true` 可让面板显示 online(慎开,见第四节)

---

## 七、节点不通排查(真机 Linux)

- 确认 `ARGO_PORT`(默认 8080)是否被平台放行
- 客户端节点链接:UUID 须为 `2a0ed8ec-...`,路径 `/vmess-argo`
- `cat .tmp/config.json` 看 `listen_port` / `path` 是否正确
- `ps` 确认进程在跑,日志应有 `[nano] s task started`

---

## 八、GPLv3 合规

NanoLimbo 内核为 GPLv3,衍生作品(本 games 模块)须保持开源。所有源码已并入同一仓库,未引入闭源组件。
