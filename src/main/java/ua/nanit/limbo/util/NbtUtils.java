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

package ua.nanit.limbo.util;

import com.google.gson.*;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.nbt.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@UtilityClass
public class NbtUtils {

    @NonNull
    public static BinaryTag fromJson(@NonNull JsonElement jsonElement) {
        if (!jsonElement.isJsonObject()) {
            JsonObject jsonObject = new JsonObject();
            jsonObject.add("text", jsonElement);
            jsonElement = jsonObject;
        }
        return fromJson0(jsonElement);
    }

    @NonNull
    private static BinaryTagType<?> nbtTypeOfJson(@NonNull JsonElement json) {
        if (json instanceof JsonPrimitive jsonPrimitive) {
            if (jsonPrimitive.isNumber()) {
                Number number = json.getAsNumber();

                if (number instanceof Byte) {
                    return BinaryTagTypes.BYTE;
                } else if (number instanceof Short) {
                    return BinaryTagTypes.SHORT;
                } else if (number instanceof Integer) {
                    return BinaryTagTypes.INT;
                } else if (number instanceof Long) {
                    return BinaryTagTypes.LONG;
                } else if (number instanceof Float) {
                    return BinaryTagTypes.FLOAT;
                } else if (number instanceof Double) {
                    return BinaryTagTypes.DOUBLE;
                }
            } else if (jsonPrimitive.isString()) {
                return BinaryTagTypes.STRING;
            } else if (jsonPrimitive.isBoolean()) {
                return BinaryTagTypes.BYTE;
            }
            throw new IllegalArgumentException("Unknown JSON primitive: " + jsonPrimitive);
        } else if (json instanceof JsonObject) {
            return BinaryTagTypes.COMPOUND;
        } else if (json instanceof JsonArray) {
            JsonArray array = json.getAsJsonArray();

            BinaryTagType<?> listType = null;
            for (JsonElement jsonEl : array) {
                BinaryTagType<?> type = nbtTypeOfJson(jsonEl);
                if (listType == null) {
                    listType = type;
                } else if (listType != type) {
                    listType = BinaryTagTypes.COMPOUND;
                    break;
                }
            }

            if (listType == null) {
                return BinaryTagTypes.LIST;
            }

            if (listType == BinaryTagTypes.BYTE) {
                return BinaryTagTypes.BYTE_ARRAY;
            } else if (listType == BinaryTagTypes.INT) {
                return BinaryTagTypes.INT_ARRAY;
            } else if (listType == BinaryTagTypes.LONG) {
                return BinaryTagTypes.LONG_ARRAY;
            }
            return BinaryTagTypes.LIST;
        } else if (json instanceof JsonNull) {
            return BinaryTagTypes.END;
        }

        throw new IllegalArgumentException("Unknown JSON element: " + json);
    }

    public static BinaryTag fromJson0(JsonElement json) {
        if (json instanceof JsonPrimitive jsonPrimitive) {
            if (jsonPrimitive.isNumber()) {
                Number number = json.getAsNumber();

                if (number instanceof Byte b) {
                    return ByteBinaryTag.byteBinaryTag(b);
                } else if (number instanceof Short s) {
                    return ShortBinaryTag.shortBinaryTag(s);
                } else if (number instanceof Integer i) {
                    return IntBinaryTag.intBinaryTag(i);
                } else if (number instanceof Long l) {
                    return LongBinaryTag.longBinaryTag(l);
                } else if (number instanceof Float f) {
                    return FloatBinaryTag.floatBinaryTag(f);
                } else if (number instanceof Double d) {
                    return DoubleBinaryTag.doubleBinaryTag(d);
                }
            } else if (jsonPrimitive.isString()) {
                return StringBinaryTag.stringBinaryTag(jsonPrimitive.getAsString());
            } else if (jsonPrimitive.isBoolean()) {
                return ByteBinaryTag.byteBinaryTag(jsonPrimitive.getAsBoolean() ? (byte) 1 : (byte) 0);
            }
            throw new IllegalArgumentException("Unknown JSON primitive: " + jsonPrimitive);
        } else if (json instanceof JsonObject jsonObject) {
            CompoundBinaryTag.Builder builder = CompoundBinaryTag.builder();
            for (Map.Entry<String, JsonElement> property : jsonObject.entrySet()) {
                builder.put(property.getKey(), fromJson0(property.getValue()));
            }

            return builder.build();
        } else if (json instanceof JsonArray jsonElements) {
            List<JsonElement> jsonArray = jsonElements.asList();

            BinaryTagType<?> listType = null;
            for (JsonElement jsonEl : jsonArray) {
                BinaryTagType<?> type = nbtTypeOfJson(jsonEl);
                if (listType == null) {
                    listType = type;
                } else if (listType != type) {
                    listType = BinaryTagTypes.COMPOUND;
                    break;
                }
            }

            if (listType == null || listType == BinaryTagTypes.END) {
                if (!jsonArray.isEmpty()) {
                    throw new IllegalArgumentException("Invalid end tag in json array: " + json);
                }

                return ListBinaryTag.listBinaryTag(BinaryTagTypes.END, Collections.emptyList());
            }

            BinaryTag listTag;
            if (listType == BinaryTagTypes.BYTE) {
                byte[] bytes = new byte[jsonArray.size()];
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] = (Byte) (jsonArray.get(i)).getAsNumber();
                }

                listTag = ByteArrayBinaryTag.byteArrayBinaryTag(bytes);
            } else if (listType == BinaryTagTypes.INT) {
                int[] ints = new int[jsonArray.size()];
                for (int i = 0; i < ints.length; i++) {
                    ints[i] = (Integer) (jsonArray.get(i)).getAsNumber();
                }

                listTag = IntArrayBinaryTag.intArrayBinaryTag(ints);
            } else if (listType == BinaryTagTypes.LONG) {
                long[] longs = new long[jsonArray.size()];
                for (int i = 0; i < longs.length; i++) {
                    longs[i] = (Long) (jsonArray.get(i)).getAsNumber();
                }

                listTag = LongArrayBinaryTag.longArrayBinaryTag(longs);
            } else {
                List<BinaryTag> tagItems = new ArrayList<>(jsonArray.size());

                for (JsonElement jsonEl : jsonArray) {
                    BinaryTag subTag = fromJson0(jsonEl);
                    if (listType == BinaryTagTypes.COMPOUND && !(subTag instanceof CompoundBinaryTag)) {
                        subTag = CompoundBinaryTag.builder().put("", subTag)
                                .build();
                    }

                    tagItems.add(subTag);
                }

                listTag = ListBinaryTag.listBinaryTag(listType, tagItems);
            }

            return listTag;
        } else if (json instanceof JsonNull) {
            return EndBinaryTag.endBinaryTag();
        }

        throw new IllegalArgumentException("Unknown JSON element: " + json);
    }
}
