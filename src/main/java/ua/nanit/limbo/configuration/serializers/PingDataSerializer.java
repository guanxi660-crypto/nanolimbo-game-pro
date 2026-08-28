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

import net.kyori.adventure.text.Component;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;
import ua.nanit.limbo.server.data.PingData;

import java.lang.reflect.Type;

public class PingDataSerializer implements TypeSerializer<PingData> {

    @Override
    public PingData deserialize(Type type, ConfigurationNode node) throws SerializationException {
        PingData pingData = new PingData();
        pingData.setDescription(node.node("description").get(Component.class, Component.empty()));
        pingData.setVersion(node.node("version").get(Component.class, Component.empty()));
        pingData.setProtocol(node.node("protocol").getInt(-1));
        return pingData;
    }

    @Override
    public void serialize(Type type, PingData obj, ConfigurationNode node) {
    }

}
