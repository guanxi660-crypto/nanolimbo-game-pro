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

package ua.nanit.limbo.server.data;

import lombok.Data;
import lombok.NonNull;

import java.util.Locale;

@Data
public class NamespacedKey {

    private static final String MINECRAFT_NAMESPACE = "minecraft";

    private String namespace;
    private String key;

    @NonNull
    public static NamespacedKey minecraft(@NonNull String key) {
        NamespacedKey namespacedKey = new NamespacedKey();
        namespacedKey.setNamespace(MINECRAFT_NAMESPACE);
        namespacedKey.setKey(key.toLowerCase(Locale.ROOT));
        return namespacedKey;
    }

    @Override
    public String toString() {
        return this.namespace + ":" + this.key;
    }
}
