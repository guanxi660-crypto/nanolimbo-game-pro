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

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PacketPlayerPositionAndLook implements PacketOut {

    private double x;
    private double y;
    private double z;
    private float yaw;
    private float pitch;
    private int teleportId;

    @Override
    public void encode(@NonNull ByteMessage msg, @NonNull Version version) {
        if (version.moreOrEqual(Version.V1_21_2)) {
            encodeModern(msg);
            return;
        }

        encodeLegacy(msg, version);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    private void encodeLegacy(@NonNull ByteMessage msg, @NonNull Version version) {
        msg.writeDouble(this.x);
        msg.writeDouble(this.y + (version.less(Version.V1_8) ? 1.62F : 0));
        msg.writeDouble(this.z);
        msg.writeFloat(this.yaw);
        msg.writeFloat(this.pitch);

        if (version.moreOrEqual(Version.V1_8)) {
            msg.writeByte(0x08);
        } else {
            msg.writeBoolean(true);
        }

        if (version.moreOrEqual(Version.V1_9)) {
            msg.writeVarInt(this.teleportId);
        }

        if (version.fromTo(Version.V1_17, Version.V1_19_3)) {
            msg.writeBoolean(false); // Dismount vehicle
        }
    }

    private void encodeModern(@NonNull ByteMessage msg) {
        msg.writeVarInt(this.teleportId);

        msg.writeDouble(this.x);
        msg.writeDouble(this.y);
        msg.writeDouble(this.z);

        msg.writeDouble(0);
        msg.writeDouble(0);
        msg.writeDouble(0);

        msg.writeFloat(this.yaw);
        msg.writeFloat(this.pitch);

        msg.writeInt(0x08);
    }
}
