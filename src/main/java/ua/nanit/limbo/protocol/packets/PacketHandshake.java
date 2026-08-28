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

package ua.nanit.limbo.protocol.packets;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import ua.nanit.limbo.connection.ClientConnection;
import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketIn;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.server.LimboServer;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PacketHandshake implements PacketIn {

    private Version version;
    private String host;
    private int port;
    private Intent intent;

    @Override
    public void decode(@NonNull ByteMessage msg, @NonNull Version version) {
        try {
            this.version = Version.of(msg.readVarInt());
        } catch (IllegalArgumentException e) {
            this.version = Version.UNDEFINED;
        }
        this.host = msg.readString();
        this.port = msg.readUnsignedShort();
        try {
            this.intent = Intent.values()[msg.readVarInt()];
        } catch (Exception e) {
            this.intent = Intent.UNDEFINED;
        }
    }

    @Override
    public void handle(@NonNull ClientConnection conn, @NonNull LimboServer server) {
        server.getPacketHandler().handle(conn, this);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    public enum Intent {
        UNDEFINED,
        STATUS,
        LOGIN,
        TRANSFER
    }
}
