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

package ua.nanit.limbo.protocol;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import ua.nanit.limbo.protocol.registry.Version;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * PacketSnapshot encodes a packet to byte array for each MC version.
 * Some versions have the same snapshot, so there are mappings to avoid data copying
 */
@AllArgsConstructor
public class PacketSnapshot implements PacketOut {

    private final Class<? extends PacketOut> packetClazz;
    private final Map<Version, byte[]> versionMessages = new EnumMap<>(Version.class);
    private final Map<Version, Version> mappings = new EnumMap<>(Version.class);

    @NonNull
    public Class<? extends PacketOut> getPacketClass() {
        return this.packetClazz;
    }

    public void encode(@NonNull Function<Version, PacketOut> packetComputeFunction) {
        encode(packetComputeFunction, List.of(Version.values()));
    }

    public void encode(@NonNull Function<Version, PacketOut> packetComputeFunction,
                       @NonNull List<Version> versions) {
        Map<Integer, Version> hashes = new HashMap<>();

        for (Version version : versions) {
            if (version.equals(Version.UNDEFINED)) {
                continue;
            }

            ByteMessage encodedMessage = ByteMessage.create();
            packetComputeFunction.apply(version).encode(encodedMessage, version);

            int hash = encodedMessage.hashCode();
            Version hashed = hashes.get(hash);

            if (hashed != null) {
                this.mappings.put(version, hashed);
            } else {
                hashes.put(hash, version);
                this.mappings.put(version, version);
                this.versionMessages.put(version, encodedMessage.toByteArray());
            }

            encodedMessage.release();
        }
    }

    @Override
    public void encode(@NonNull ByteMessage msg, @NonNull Version version) {
        Version mapped = this.mappings.get(version);
        byte[] message = this.versionMessages.get(mapped);

        if (message != null) {
            msg.writeBytes(message);
            return;
        }

        throw new IllegalArgumentException("No mappings for version " + version);
    }

    @Override
    public String toString() {
        return this.packetClazz.getSimpleName();
    }

    @NonNull
    public static PacketSnapshot of(@NonNull PacketOut packet) {
        return of(packet.getClass(), version -> packet);
    }

    @NonNull
    public static PacketSnapshot of(@NonNull PacketOut packet, Version version) {
        return of(packet.getClass(), version2 -> packet, List.of(version));
    }

    @NonNull
    public static PacketSnapshot of(@NonNull Class<? extends PacketOut> packetClazz,
                                    @NonNull Function<Version, PacketOut> packetComputeFunction,
                                    @NonNull List<Version> versions) {
        PacketSnapshot snapshot = new PacketSnapshot(packetClazz);
        snapshot.encode(packetComputeFunction, versions);
        return snapshot;
    }

    @NonNull
    public static PacketSnapshot of(@NonNull Class<? extends PacketOut> packetClazz,
                                    @NonNull Function<Version, PacketOut> packetComputeFunction) {
        PacketSnapshot snapshot = new PacketSnapshot(packetClazz);
        snapshot.encode(packetComputeFunction);
        return snapshot;
    }
}
