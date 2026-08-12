/*
 * HoloUI is a holographic user interface for Minecraft Bukkit Servers
 * Copyright (c) 2025 Arcane Arts (Volmit Software)
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
package art.arcane.holoui.config.icon;

import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

@JsonAdapter(IconBillboard.Adapter.class)
public enum IconBillboard {
  @SerializedName("fixed")
  FIXED("fixed", (byte) 0),
  @SerializedName("vertical")
  VERTICAL("vertical", (byte) 1),
  @SerializedName("horizontal")
  HORIZONTAL("horizontal", (byte) 2),
  @SerializedName("center")
  CENTER("center", (byte) 3);

  private final String serializedValue;
  private final byte metadataValue;

  IconBillboard(String serializedValue, byte metadataValue) {
    this.serializedValue = serializedValue;
    this.metadataValue = metadataValue;
  }

  public byte metadataValue() {
    return metadataValue;
  }

  public static final class Adapter extends TypeAdapter<IconBillboard> {
    @Override
    public void write(JsonWriter out, IconBillboard value) throws IOException {
      if (value == null) {
        out.nullValue();
        return;
      }
      out.value(value.serializedValue);
    }

    @Override
    public IconBillboard read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }
      if (in.peek() != JsonToken.STRING) {
        throw new JsonParseException("Display billboard must be fixed, vertical, horizontal, or center");
      }
      String serializedValue = in.nextString();
      for (IconBillboard billboard : values()) {
        if (billboard.serializedValue.equals(serializedValue)) {
          return billboard;
        }
      }
      throw new JsonParseException("Unknown display billboard: " + serializedValue);
    }
  }
}
