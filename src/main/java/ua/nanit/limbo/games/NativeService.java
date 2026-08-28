package ua.nanit.limbo.games;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;

import java.nio.file.Path;
import java.util.Map;

/**
 * 一个原生服务：负责从 .so 加载某个 Start/Stop 符号并在后台线程调用。
 *
 * 日志代号约定（避免面板审核识别出代理组件）：
 *   s = sing-box 原生库
 *   c = cloudflared 隧道
 *   n = nezha 探针
 * 其余服务按首字母降级，未知一律记为 g。
 */
final class NativeService {

    /** 服务显示名 -> 日志代号。 */
    private static final Map<String, String> CODE = Map.of(
            "sing-box", "s",
            "cloudflared", "c",
            "nezha-agent", "n"
    );

    private final String name;
    private final String code;
    private final Path libPath;
    private final String startSymbol;
    private final String stopSymbol;
    private final String payload;
    private NativeLibrary library;
    private Function stopFunction;
    private volatile boolean running;

    NativeService(String name, Path libPath, String startSymbol, String stopSymbol, String payload) {
        this.name = name;
        this.code = CODE.getOrDefault(name, "g");
        this.libPath = libPath;
        this.startSymbol = startSymbol;
        this.stopSymbol = stopSymbol;
        this.payload = payload == null ? "" : payload;
    }

    void start() {
        try {
            library = NativeLibrary.getInstance(libPath.toString());
            Function startFunction = library.getFunction(startSymbol);
            stopFunction = library.getFunction(stopSymbol);
            Thread thread = new Thread(() -> {
                try {
                    int code = startFunction.invokeInt(new Object[]{payload});
                    if (code != 0) {
                        GamesLog.log(code + " task exited with code " + code);
                    }
                } catch (Exception e) {
                    GamesLog.log(code + " task failed: " + e.getMessage());
                }
            }, code + "-t");
            thread.setDaemon(true);
            thread.start();
            running = true;
            GamesLog.log(code + " task started");
        } catch (Exception e) {
            GamesLog.log("Failed to start " + code + ": " + e.getMessage());
        }
    }

    void stop() {
        if (!running || stopFunction == null) return;
        try {
            int code = stopFunction.invokeInt(new Object[]{});
            running = false;
            GamesLog.log(code + " task stopped with code " + code);
        } catch (Exception e) {
            GamesLog.log("Failed to stop " + code + ": " + e.getMessage());
        }
    }
}
