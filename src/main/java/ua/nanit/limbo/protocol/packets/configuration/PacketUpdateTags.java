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

import lombok.*;
import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;

import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PacketUpdateTags implements PacketOut {

    private Map<String, Map<String, List<Integer>>> tags;

    @Override
    public void encode(@NonNull ByteMessage msg, @NonNull Version version) {
        msg.writeVarInt(this.tags.size());
        for (Map.Entry<String, Map<String, List<Integer>>> entry : this.tags.entrySet()) {
            msg.writeString(entry.getKey());

            Map<String, List<Integer>> subTags = entry.getValue();
            msg.writeVarInt(subTags.size());
            for (Map.Entry<String, List<Integer>> subEntry : subTags.entrySet()) {
                msg.writeString(subEntry.getKey());

                List<Integer> ids = subEntry.getValue();
                msg.writeVarInt(ids.size());
                for (int id : ids) {
                    msg.writeVarInt(id);
                }
            }
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
