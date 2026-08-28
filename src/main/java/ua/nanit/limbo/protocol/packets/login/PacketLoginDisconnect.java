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

package ua.nanit.limbo.protocol.packets.login;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.util.ComponentUtils;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PacketLoginDisconnect implements PacketOut {

    private Component reason;

    @Override
    public void encode(@NonNull ByteMessage msg, @NonNull Version version) {
        GsonComponentSerializer gsonComponentSerializer = ComponentUtils.getJsonChatSerializer(version);
        msg.writeString(gsonComponentSerializer.serialize(this.reason));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
