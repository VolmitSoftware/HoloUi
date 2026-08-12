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

import art.arcane.holoui.enums.MenuIconType;
import art.arcane.holoui.exceptions.MenuIconException;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;

import java.io.IOException;

public record EntityIconData(
    @SerializedName("entity")
    @JsonAdapter(EntityIconData.EntityTypeAdapter.class)
    EntityType entityType,
    Float width,
    Float height
) implements MenuIconData {
  public static final float DEFAULT_WIDTH = 1F;
  public static final float DEFAULT_HEIGHT = 1F;
  public static final float MAX_DIMENSION = 64F;

  public EntityIconData {
    validateDimension(width, "width");
    validateDimension(height, "height");
  }

  public MenuIconType getType() {
    return MenuIconType.ENTITY;
  }

  public float resolvedWidth() {
    return width == null ? DEFAULT_WIDTH : width;
  }

  public float resolvedHeight() {
    return height == null ? DEFAULT_HEIGHT : height;
  }

  public EntityType requireEntityType() throws MenuIconException {
    if (entityType == null) {
      throw new MenuIconException("Entity icon has an unknown or invalid entity id");
    }
    if (!entityType.isSpawnable() || !entityType.isAlive()) {
      throw new MenuIconException("Entity icon type \"%s\" is not a spawnable living entity", entityType.getKey());
    }
    return entityType;
  }

  private static void validateDimension(Float value, String name) {
    if (value != null && (!Float.isFinite(value) || value <= 0F || value > MAX_DIMENSION)) {
      throw new IllegalArgumentException(name + " must be finite, greater than 0, and at most " + MAX_DIMENSION);
    }
  }

  static final class EntityTypeAdapter extends TypeAdapter<EntityType> {
    @Override
    public void write(JsonWriter out, EntityType value) throws IOException {
      if (value == null) {
        out.nullValue();
        return;
      }
      out.value(value.getKey().toString());
    }

    @Override
    public EntityType read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }

      String raw = in.nextString();
      try {
        NamespacedKey key = NamespacedKey.fromString(raw);
        if (key == null) {
          return null;
        }
        for (EntityType entityType : EntityType.values()) {
          if (entityType != EntityType.UNKNOWN && entityType.getKey().equals(key)) {
            return entityType;
          }
        }
        return null;
      } catch (RuntimeException | LinkageError failure) {
        return null;
      }
    }
  }
}
