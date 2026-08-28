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

package ua.nanit.limbo.util;

import lombok.NonNull;
import lombok.experimental.UtilityClass;
import ua.nanit.limbo.protocol.Packet;
import ua.nanit.limbo.protocol.registry.State;
import ua.nanit.limbo.protocol.registry.Version;

import java.util.Locale;

@UtilityClass
public class PacketUtils {

    @NonNull
    public static String toPacketId(int packetId) {
        return "0x" + Integer.toHexString(packetId).toUpperCase(Locale.ROOT);
    }

    @NonNull
    public static String toDetailedInfo(@NonNull Packet packet,
                                        int packetId,
                                        @NonNull Version version,
                                        @NonNull State state) {
        return packet + "(" + toPacketId(packetId) + ") [" + version + "|" + state + "]";
    }
}
