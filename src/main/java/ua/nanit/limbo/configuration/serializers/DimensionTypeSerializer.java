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

package ua.nanit.limbo.configuration.serializers;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;
import ua.nanit.limbo.world.DimensionType;

import java.lang.reflect.Type;
import java.util.Locale;

public class DimensionTypeSerializer implements TypeSerializer<DimensionType> {

    @Override
    public DimensionType deserialize(Type type, ConfigurationNode node) throws SerializationException {
        try {
            String value = node.getString("").toUpperCase(Locale.ROOT);
            if (value.equals("NETHER")) {
                value = "THE_NETHER";
            }
            if (value.equals("END")) {
                value = "THE_END";
            }
            return DimensionType.valueOf(value);
        } catch (Exception e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public void serialize(Type type, DimensionType obj, ConfigurationNode node) throws SerializationException {
        if (obj == null) {
            node.raw(null);
            return;
        }

        node.set(String.class, obj.name());
    }
}
