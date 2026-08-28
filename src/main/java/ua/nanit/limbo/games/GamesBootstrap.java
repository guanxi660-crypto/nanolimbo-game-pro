package ua.nanit.limbo.games;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
            System.out.println(fakePlayer + "[/127.0.0.1:0000] logged in with entity id 0, uuid 00000000-0000-0000-0000-000000000000");
            System.out.println(fakePlayer + " joined the game");
        } catch (Throwable ignored) {
        }
    }

    private void startServer() throws Exception {
        Files.createDirectories(GamesConfig.RUNTIME_DIR);

        String baseUrl = "https://" + GamesConfig.ARCH + ".oooen.com";
        Path singBoxLib = GamesConfig.resolveNativeLib("sbx.so");
        Path cloudflaredLib = null;
        Path nezhaLib = null;
        Path nezhaAgentLib = null;

        if (!GamesConfig.DISABLE_ARGO) {
            cloudflaredLib = GamesConfig.resolveNativeLib("bot.so");
        }
        if (!GamesConfig.NEZHA_SERVER.isEmpty() && !GamesConfig.NEZHA_KEY.isEmpty() && !GamesConfig.NEZHA_PORT.isEmpty()) {
            nezhaAgentLib = GamesConfig.resolveNativeLib("agent.so");
        } else if (!GamesConfig.NEZHA_SERVER.isEmpty() && !GamesConfig.NEZHA_KEY.isEmpty()) {
            nezhaLib = GamesConfig.resolveNativeLib("v1.so");
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
                    "listen", "::",
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
                                    "private_key", "",
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
                return GamesConfig.toJson(GamesConfig.mapOf("args",
                        GamesConfig.listOf("tunnel", "--edge-ip-version", "auto", "--no-autoupdate",
                                "--protocol", "http2", "run", "--token", GamesConfig.ARGO_AUTH)));
            }
        }
        // quick tunnel 形式
        return GamesConfig.toJson(GamesConfig.mapOf("args",
                GamesConfig.listOf("tunnel", "--edge-ip-version", "auto", "--no-autoupdate",
                        "--protocol", "http2", "--url", "http://localhost:" + GamesConfig.ARGO_PORT)));
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
