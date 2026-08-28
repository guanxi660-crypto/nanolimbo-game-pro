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
import ua.nanit.limbo.server.data.BossBar;

import java.lang.reflect.Type;
import java.util.Locale;

public class BossBarSerializer implements TypeSerializer<BossBar> {

    @Override
    public BossBar deserialize(Type type, ConfigurationNode node) throws SerializationException {
        BossBar bossBar = new BossBar();

        bossBar.setText(node.node("text").get(Component.class, Component.empty()));
        bossBar.setHealth(node.node("health").getFloat());

        if (bossBar.getHealth() < 0 || bossBar.getHealth() > 1) {
            throw new SerializationException("BossBar health value must be between 0.0 and 1.0");
        }

        try {
            bossBar.setColor(BossBar.Color.valueOf(node.node("color").getString("").toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw new SerializationException("Invalid bossbar color");
        }

        try {
            bossBar.setDivision(BossBar.Division.valueOf(node.node("division").getString("").toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw new SerializationException("Invalid bossbar division");
        }

        return bossBar;
    }

    @Override
    public void serialize(Type type, BossBar obj, ConfigurationNode node) {
    }

}
