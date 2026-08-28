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

package ua.nanit.limbo.server.commands;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import ua.nanit.limbo.server.Command;
import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

import java.util.Map;

@AllArgsConstructor
public class CmdHelp implements Command {

    private final LimboServer server;

    @Override
    public void execute() {
        Map<String, Command> commands = server.getCommandManager().getCommands();

        Log.info("Available commands:");

        for (Map.Entry<String, Command> entry : commands.entrySet()) {
            Log.info("%s - %s", entry.getKey(), entry.getValue().description());
        }
    }

    @NonNull
    @Override
    public String description() {
        return "Show this message";
    }
}
