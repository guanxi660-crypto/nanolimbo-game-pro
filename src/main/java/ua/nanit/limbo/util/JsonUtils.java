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

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.TypeAdapter;
import lombok.experimental.UtilityClass;

@UtilityClass
public class JsonUtils {

    private static final TypeAdapter<JsonElement> STRICT_ADAPTER = new Gson().getAdapter(JsonElement.class);

    public static boolean isValidJson(String json) {
        try {
            STRICT_ADAPTER.fromJson(json);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

}
