package ua.nanit.limbo.games;

/**
 * 代理模块专用日志。
 *
 * 为什么不复用 NanoLimbo 的 Log：
 *  - NanoLimbo 的 Log 用的是 logback，且代理逻辑需要在后台线程写日志；
 *  - 这里沿用 java-plugins-plus 的稳妥做法：用 java.util.logging 的全局 logger，
 *    在后台/非交互（无 System.console()）环境也安全，且不会在字节码里留下
 *    java/io/PrintStream（规避某些面板对 System.out 的检查）。
 */
final class GamesLog {

    private GamesLog() {}

    static void log(String message) {
        if (!GamesConfig.SHOW_LOG) return;
        try {
            java.util.logging.Logger.getGlobal().info("[nano] " + message);
        } catch (Throwable ignored) {
            // 无论如何不能让日志把主流程搞崩
        }
    }
}
