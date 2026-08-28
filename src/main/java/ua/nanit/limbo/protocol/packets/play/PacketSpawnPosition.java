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
import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.server.data.NamespacedKey;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PacketSpawnPosition implements PacketOut {

    private NamespacedKey dimensionKey;
    private long x;
    private long y;
    private long z;
    private float yaw;
    private float pitch;

    @Override
    public void encode(@NonNull ByteMessage msg, @NonNull Version version) {
        if (version.moreOrEqual(Version.V1_21_9)) {
            msg.writeNamespacedKey(this.dimensionKey);
        }
        msg.writeLong(encodePosition(this.x, this.y, this.z));
        msg.writeFloat(this.yaw);
        if (version.moreOrEqual(Version.V1_21_9)) {
            msg.writeFloat(this.pitch);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    private static long encodePosition(long x, long y, long z) {
        return ((x & 0x3FFFFFF) << 38) | ((z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
