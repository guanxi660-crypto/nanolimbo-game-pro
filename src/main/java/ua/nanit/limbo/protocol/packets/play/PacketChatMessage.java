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

package ua.nanit.limbo.protocol.packets.play;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PacketChatMessage implements PacketOut {

    private Component message;
    private PositionLegacy position;
    private UUID sender;

    @Override
    public void encode(@NonNull ByteMessage msg, @NonNull Version version) {
        msg.writeComponent(this.message, version);
        if (version.moreOrEqual(Version.V1_19_1)) {
            msg.writeBoolean(this.position.index == PositionLegacy.ACTION_BAR.index);
        } else if (version.moreOrEqual(Version.V1_19)) {
            msg.writeVarInt(this.position.index);
        } else if (version.moreOrEqual(Version.V1_8)) {
            msg.writeByte(this.position.index);
        }

        if (version.moreOrEqual(Version.V1_16) && version.less(Version.V1_19)) {
            msg.writeUuid(this.sender);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    @AllArgsConstructor
    public enum PositionLegacy {
        CHAT(0),
        SYSTEM_MESSAGE(1),
        ACTION_BAR(2);

        private final int index;
    }
}
