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
public class PacketPlayerAbilities implements PacketOut {

    private static final int FLAG_INVINCIBLE = 0x01;
    private static final int FLAG_FLYING = 0x02;
    private static final int FLAG_CAN_FLY = 0x04;
    private static final int FLAG_CREATIVE = 0x08;

    private boolean invincible;
    private boolean canFly;
    private boolean flying;
    private boolean creative;

    private float flyingSpeed = 0.0F;
    private float fieldOfView = 0.1F;

    @Override
    public void encode(@NonNull ByteMessage msg, @NonNull Version version) {
        int flags = 0;
        if (this.invincible) {
            flags |= FLAG_INVINCIBLE;
        }
        if (this.canFly) {
            flags |= FLAG_CAN_FLY;
        }
        if (this.flying) {
            flags |= FLAG_FLYING;
        }
        if (this.creative) {
            flags |= FLAG_CREATIVE;
        }

        msg.writeByte(flags);
        msg.writeFloat(this.flyingSpeed);
        msg.writeFloat(this.fieldOfView);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
