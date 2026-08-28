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

package ua.nanit.limbo.server;

import lombok.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import ua.nanit.limbo.server.commands.*;

import java.util.*;

public final class CommandManager extends Thread {

    private final Map<String, Command> commands = new HashMap<>();

    @NonNull
    public Map<String, Command> getCommands() {
        return Collections.unmodifiableMap(commands);
    }

    @Nullable
    public Command getCommand(@NonNull String name) {
        return commands.get(name.toLowerCase(Locale.ROOT));
    }

    public void register(@NonNull Command cmd, @NonNull String... aliases) {
        for (String alias : aliases) {
            commands.put(alias.toLowerCase(Locale.ROOT), cmd);
        }
    }

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        String command;

        while (true) {
            try {
                command = scanner.nextLine().trim();
            } catch (NoSuchElementException e) {
                break;
            }

            Command handler = getCommand(command);

            if (handler != null) {
                try {
                    handler.execute();
                } catch (Throwable t) {
                    Log.error("Cannot execute command:", t);
                }
                continue;
            }

            Log.info("Unknown command. Type \"help\" to get commands list");
        }
    }

    public void registerAll(LimboServer server) {
        register(new CmdHelp(server), "help");
        register(new CmdConn(server), "conn");
        register(new CmdMem(), "mem");
        register(new CmdStop(), "stop");
        register(new CmdVersion(), "version", "ver");
    }
}
