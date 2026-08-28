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

package ua.nanit.limbo.protocol.packets.configuration;

import io.netty.handler.codec.DecoderException;
import lombok.*;
import ua.nanit.limbo.connection.ClientConnection;
import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketIn;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.server.LimboServer;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PacketKnownPacks implements PacketIn, PacketOut {

    private List<KnownPack> knownPacks;

    @Override
    public void encode(@NonNull ByteMessage msg, @NonNull Version version) {
        msg.writeVarInt(this.knownPacks.size());
        for (KnownPack knownPack : this.knownPacks) {
            msg.writeString(knownPack.namespace());
            msg.writeString(knownPack.id());
            msg.writeString(knownPack.version());
        }
    }

    @Override
    public void decode(@NonNull ByteMessage msg, @NonNull Version version) {
        int size = msg.readVarInt();
        if (size > 16) {
            throw new DecoderException("Cannot receive known packs larger than 16");
        }
        List<KnownPack> knownPacks = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String nameSpace = msg.readString(256);
            String id = msg.readString(256);
            String knownPackVersion = msg.readString(256);

            knownPacks.add(new KnownPack(nameSpace, id, knownPackVersion));
        }

        this.knownPacks = knownPacks;
    }

    @Override
    public void handle(@NonNull ClientConnection conn, @NonNull LimboServer server) {
        server.getPacketHandler().handle(conn, this);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    public record KnownPack(@NonNull String namespace, @NonNull String id, @NonNull String version) {
    }
}
