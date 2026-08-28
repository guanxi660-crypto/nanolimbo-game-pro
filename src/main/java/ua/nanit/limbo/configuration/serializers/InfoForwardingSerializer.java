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

import lombok.RequiredArgsConstructor;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializer;
import ua.nanit.limbo.server.data.InfoForwarding;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RequiredArgsConstructor
public class InfoForwardingSerializer implements TypeSerializer<InfoForwarding> {

    private static final String EXTERNAL_FILE_PREFIX = "@";

    private final Path root;

    @Override
    public InfoForwarding deserialize(java.lang.reflect.Type type, ConfigurationNode node) throws SerializationException {
        InfoForwarding forwarding = new InfoForwarding();

        try {
            forwarding.setType(InfoForwarding.Type.valueOf(node.node("type").getString("").toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            throw new SerializationException("Undefined info forwarding type");
        }

        if (forwarding.getType() == InfoForwarding.Type.MODERN) {
            String raw = node.node("secret").getString("");
            forwarding.setSecretKey(resolveSecret(node, type, raw));
        }

        if (forwarding.getType() == InfoForwarding.Type.BUNGEE_GUARD) {
            forwarding.setTokens(resolveTokens(node));
        }

        return forwarding;
    }

    @Override
    public void serialize(java.lang.reflect.Type type, InfoForwarding obj, ConfigurationNode node) {
    }

    private byte[] resolveSecret(ConfigurationNode node, java.lang.reflect.Type type, String raw) throws SerializationException {
        if (raw.startsWith(EXTERNAL_FILE_PREFIX)) {
            Path path = root.resolve(raw.substring(EXTERNAL_FILE_PREFIX.length()));
            try {
                String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8).trim();
                return content.getBytes(StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new SerializationException(node, type, "Cannot read external secret file: " + path.toAbsolutePath(), e);
            }
        }
        return raw.getBytes(StandardCharsets.UTF_8);
    }

    private List<String> resolveTokens(ConfigurationNode node) throws SerializationException {
        ConfigurationNode tokensNode = node.node("tokens");

        if (tokensNode.isList()) {
            List<String> direct = tokensNode.getList(String.class);
            if (direct == null || direct.isEmpty()) {
                return direct;
            }
            List<String> resolved = new ArrayList<>(direct.size());
            for (String entry : direct) {
                if (entry.startsWith(EXTERNAL_FILE_PREFIX)) {
                    resolved.addAll(readLines(entry));
                } else {
                    resolved.add(entry);
                }
            }
            return resolved;
        }

        String scalar = tokensNode.getString("");
        if (scalar.startsWith(EXTERNAL_FILE_PREFIX)) {
            return readLines(scalar);
        }
        return new ArrayList<>();
    }

    private List<String> readLines(String tokenEntry) throws SerializationException {
        Path path = root.resolve(tokenEntry.substring(EXTERNAL_FILE_PREFIX.length()));
        try {
            List<String> lines = new ArrayList<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    lines.add(trimmed);
                }
            }
            return lines;
        } catch (IOException e) {
            throw new SerializationException("Cannot read external tokens file: " + path.toAbsolutePath() + " (" + e.getMessage() + ")");
        }
    }
}
