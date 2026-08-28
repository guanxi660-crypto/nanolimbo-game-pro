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

package ua.nanit.limbo.world;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.server.data.NamespacedKey;

import java.util.EnumMap;
import java.util.Map;

@RequiredArgsConstructor
@Getter
public enum DimensionType {

    OVERWORLD(NamespacedKey.minecraft("overworld"), 0),
    THE_END(NamespacedKey.minecraft("the_end"), 1),
    THE_NETHER(NamespacedKey.minecraft("the_nether"), -1);

    private final NamespacedKey key;
    private final int legacyDimensionId;

    @NonNull
    public VersionedDimension createVersionedDimension(@NonNull DimensionRegistry dimensionRegistry) {
        Map<Version, Dimension> perVersionDimension = new EnumMap<>(Version.class);
        for (Version version : Version.values()) {
            Dimension dimension = dimensionRegistry.findDimension(version, this.key);
            if (dimension == null) {
                continue;
            }
            perVersionDimension.put(version, dimension);
        }
        return new VersionedDimension(this.key, this.legacyDimensionId, perVersionDimension);
    }

}
