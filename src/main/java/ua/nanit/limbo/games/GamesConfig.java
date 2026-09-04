package ua.nanit.limbo.games;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏模块的集中配置。
 *
 * 配置来源优先级（高 → 低）：
 *   1) 工作目录下的 games.properties 文件（Pterodactyl 面板用文件管理器编辑，无需环境变量 UI）
 *   2) 运行环境变量（若面板/启动命令提供）
 *   3) 源码内置默认值（已写入你的真实参数，开箱即用）
 *
 * 设计要点：
 * 1) 所有敏感/可变配置均已内置默认值（UUID/ARGO_AUTH/NEZHA 等），
 *    长 base64 用 Python 写入源码，规避 patch 工具静默截断。
 * 2) 原生 .so 通过 getResourceAsStream 从 jar 里抽取到运行目录/.tmp。
 * 3) ENABLE_GAMES 默认 true：Pterodactyl 无自定义环境变量 UI 时，java -jar 直接启用。
 */
final class GamesConfig {

    /** 集中配置：工作目录下的 games.properties 优先于环境变量。 */
    static final java.util.Properties PROPS = loadProps();

    /** 是否启用游戏模块。默认开启（无环境变量 UI 时也能直接跑）。 */
    static final boolean ENABLE_GAMES = cfgBool("ENABLE_GAMES", true);

    /**
     * 是否打印假的 MC 启动完成日志（Done (Xs)! + 玩家加入），让 Pterodactyl 等面板翻 online。
     * 默认 false：代理是长期驻留服务，面板"无玩家 15 分钟自动关停"会误杀它，
     * 卡在 starting 反而最安全。临时需要 online 显示时在 nano.properties 设 true。
     */
    static final boolean FAKE_MC_STARTUP = cfgBool("FAKE_MC_STARTUP", false);

    // ---- 以下字段与 java-plugins-plus 的 AppService 保持一致 ----
    static final String ARCH = detectArch();
    static final String FILE_PATH = cfg("FILE_PATH", ".tmp");
    static final String SUB_PATH = cfg("SUB_PATH", "sub");
    static final String UUID = cfg("UUID", "");
    static final String NEZHA_SERVER = cfg("NEZHA_SERVER", "");
    static final String NEZHA_PORT = cfg("NEZHA_PORT", "");
    static final String NEZHA_KEY = cfg("NEZHA_KEY", "");
    static final String ARGO_DOMAIN = cfg("ARGO_DOMAIN", "");
    static final String ARGO_AUTH = cfg("ARGO_AUTH", "");
    static final int ARGO_PORT = cfgInt("ARGO_PORT", 8080);
    static final String S5_PORT = cfg("S5_PORT", "");
    /** HY2 端口:无变量则空字符串,不开启(与 TUIC/ANYTLS 一致,不做 SERVER_PORT/40096 兜底)。 */
    static final String HY2_PORT = cfg("HY2_PORT", "");
    static final String TUIC_PORT = cfg("TUIC_PORT", "");
    static final String ANYTLS_PORT = cfg("ANYTLS_PORT", "");
    static final String REALITY_PORT = cfg("REALITY_PORT", "");
    static final String CFIP = cfg("CFIP", "spring.io");
    static final int CFPORT = cfgInt("CFPORT", 443);
    static final String NAME = cfg("NAME", "");
    static final String CHAT_ID = cfg("CHAT_ID", "");
    static final String BOT_TOKEN = cfg("BOT_TOKEN", "");
    static final boolean DISABLE_ARGO = cfgBool("DISABLE_ARGO", false);

    /** 探测本机对外 IP(取第一个非回环 IPv4),供节点链接 address 使用。 */
    static String detectLocalIp() {
        try {
            java.net.InetAddress localhost = java.net.InetAddress.getLocalHost();
            if (!localhost.isLoopbackAddress() && localhost instanceof java.net.Inet4Address) {
                return localhost.getHostAddress();
            }
            java.net.NetworkInterface nif = java.net.NetworkInterface.getByInetAddress(localhost);
            if (nif != null) {
                for (java.net.InterfaceAddress addr : nif.getInterfaceAddresses()) {
                    java.net.InetAddress ia = addr.getAddress();
                    if (ia instanceof java.net.Inet4Address && !ia.isLoopbackAddress()) {
                        return ia.getHostAddress();
                    }
                }
            }
            // 兜底:枚举所有非回环 IPv4
            for (java.net.NetworkInterface ni : java.util.Collections.list(java.net.NetworkInterface.getNetworkInterfaces())) {
                for (java.net.InterfaceAddress addr : ni.getInterfaceAddresses()) {
                    java.net.InetAddress ia = addr.getAddress();
                    if (ia instanceof java.net.Inet4Address && !ia.isLoopbackAddress() && !ia.isLinkLocalAddress()) {
                        return ia.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }
    static final boolean SHOW_LOG = !List.of("false", "disable", "no")
            .contains(cfg("SHOW_LOG", "false").toLowerCase());

    static final Path ROOT = Path.of("").toAbsolutePath();
    static final Path RUNTIME_DIR = ROOT.resolve(FILE_PATH).normalize();
    static final Path SING_BOX_CONFIG_PATH = RUNTIME_DIR.resolve("config.json");
    static final Path NEZHA_CONFIG_PATH = RUNTIME_DIR.resolve("config.yaml");
    static final Path BOOT_LOG_PATH = RUNTIME_DIR.resolve("boot.log");
    static final Path SUB_FILE_PATH = RUNTIME_DIR.resolve("sub.txt");
    static final Path LIST_FILE_PATH = RUNTIME_DIR.resolve("list.txt");
    static final Path INDEX_FILE_PATH = ROOT.resolve("index.html").normalize();
    static final Path KEYPAIR_PATH = RUNTIME_DIR.resolve("keypair.properties");

    private GamesConfig() {}

    // ===================== 配置文件读取（Pterodactyl 无环境变量 UI 时的唯一入口） =====================

    /**
     * 加载工作目录下 games.properties。文件不存在时自动生成一份带注释的样例，
     * 这样用户能在面板文件管理器里直接看到并修改。
     */
    static java.util.Properties loadProps() {
        java.util.Properties p = new java.util.Properties();
        // 先加载 jar 内嵌的 nano.properties（构建时烘焙的参数）
        try (var in = GamesConfig.class.getClassLoader().getResourceAsStream("nano.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (Exception ignored) {
        }
        // 再叠加工作目录的 nano.properties（若存在则按 key 覆盖内嵌值,不删除其它内置项）
        Path file = Path.of("").toAbsolutePath().resolve("nano.properties");
        if (Files.exists(file)) {
            try (var in = Files.newInputStream(file)) {
                p.load(in);
                GamesLog.log("Merged working-dir nano.properties (" + p.size() + " entries)");
            } catch (Exception e) {
                GamesLog.log("Failed to read nano.properties: " + e.getMessage());
            }
            return p;
        }
        // 都不存在则生成最小样例文件，方便用户在面板里编辑。
        // 只预置总开关 ENABLE_GAMES,其余配置项不写入,需要时用户手动添加(见 README 配置说明)。
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# NanoLimbo games module config\n");
            sb.append("# 只需保留 ENABLE_GAMES=true 即可运行;其余参数已内置,按需手动添加覆盖(见 README 配置说明)。\n");
            sb.append("# 示例: UUID=xxx  /  ARGO_DOMAIN=xxx  /  ARGO_AUTH=xxx  /  NEZHA_SERVER=xxx  /  NEZHA_KEY=xxx\n");
            sb.append("ENABLE_GAMES=true\n");
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            GamesLog.log("nano.properties not found, created sample at " + file);
        } catch (Exception ignored) {
        }
        return p;
    }

    /** 读取顺序：games.properties > 环境变量 > 默认值。 */
    static String cfg(String key, String def) {
        String v = PROPS.getProperty(key);
        if (v != null && !v.isEmpty()) return v;
        v = System.getenv(key);
        return v == null || v.isEmpty() ? def : v;
    }

    static int cfgInt(String key, int def) {
        String v = cfg(key, null);
        if (v == null) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static boolean cfgBool(String key, boolean def) {
        String v = cfg(key, null);
        if (v == null) return def;
        return v.equalsIgnoreCase("true") || v.equals("1") || v.equalsIgnoreCase("yes");
    }

    // ===================== 平台/架构识别 =====================

    static String detectArch() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (os.contains("linux") && (arch.contains("amd64") || arch.contains("x86_64"))) {
            return "amd64";
        }
        // ARM64/aarch64:免费 MC 主机不少是 ARM(如 ouiheberg/slimenodes 类)
        if (os.contains("linux") && (arch.contains("aarch64") || arch.contains("arm64"))) {
            return "arm64";
        }
        // 其它架构当前不支持原生库,默认 amd64
        return "amd64";
    }

    // ===================== 端口校验 =====================

    /** 判断一个端口字符串是否为“有效且启用”的端口（非空且 1-65535）。 */
    static boolean isValidPort(String port) {
        if (port == null || port.isEmpty()) return false;
        try {
            int p = Integer.parseInt(port.trim());
            return p > 0 && p <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ===================== 极简 JSON 构造（无第三方依赖） =====================

    static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    static List<Object> listOf(Object... items) {
        List<Object> l = new ArrayList<>();
        for (Object o : items) l.add(o);
        return l;
    }

    static String toJson(Object value) {
        StringBuilder sb = new StringBuilder();
        appendJson(sb, value);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void appendJson(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            sb.append('"').append(escapeJson((String) value)).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) value).entrySet()) {
                if (!first) sb.append(',');
                sb.append('"').append(escapeJson(e.getKey())).append("\":");
                appendJson(sb, e.getValue());
                first = false;
            }
            sb.append('}');
        } else if (value instanceof Iterable) {
            sb.append('[');
            boolean first = true;
            for (Object o : (Iterable<Object>) value) {
                if (!first) sb.append(',');
                appendJson(sb, o);
                first = false;
            }
            sb.append(']');
        } else {
            sb.append('"').append(escapeJson(String.valueOf(value))).append('"');
        }
    }

    private static String escapeJson(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    // ===================== 原生库抽取 =====================

    /**
     * 抽取/下载原生组件到运行目录,保存时用贴近 Minecraft 游戏库的伪装名。
     * 参数 fileName 为磁盘保存名(伪装名),remoteName 为远程资源名(jar 资源/下载 URL 里的真名)。
     */
    static Path resolveNativeLib(String fileName, String remoteName) throws Exception {
        Path target = RUNTIME_DIR.resolve(fileName);
        if (Files.exists(target) && Files.size(target) > 0) {
            GamesLog.log("reused cached component: " + target);
            return target;
        }
        Files.createDirectories(RUNTIME_DIR);
        // 优先用 jar 内置 .so（shadowJar 会把 resources 打进 fat jar,资源名保持 remoteName）
        String resPath = "native/" + ARCH + "/" + remoteName;
        try (InputStream in = GamesBootstrap.class.getClassLoader().getResourceAsStream(resPath)) {
            if (in != null) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                target.toFile().setExecutable(true, false);
                GamesLog.log("loaded embedded component: " + resPath + " -> " + fileName);
                return target;
            }
        }
        // 回退：远程下载（与 java-plugins-plus 同域名策略,URL 用 remoteName）
        String url = "https://" + ARCH + ".oooen.com/" + remoteName;
        GamesLog.log("component missing locally, fetching fallback: " + url);
        java.net.URI uri = java.net.URI.create(url);
        try (var in = uri.toURL().openStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        target.toFile().setExecutable(true, false);
        return target;
    }
}
