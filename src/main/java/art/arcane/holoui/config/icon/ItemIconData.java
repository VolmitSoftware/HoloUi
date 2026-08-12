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
import art.arcane.volmlib.util.bukkit.registry.RegistryUtil;
import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;

public record ItemIconData(
    @SerializedName("item")
    @JsonAdapter(ItemIconData.MaterialAdapter.class)
    Material materialType,
    int count,
    int customModelValue,
    IconDisplayStyle style
) implements MenuIconData {
  public static ItemIconData of(ItemStack stack, boolean facing) {
    if (stack.hasItemMeta() && stack.getItemMeta().hasCustomModelData())
      return new ItemIconData(stack.getType(), stack.getAmount(), stack.getItemMeta().getCustomModelData(), null);
    else
      return new ItemIconData(stack.getType(), stack.getAmount(), 0, null);
  }

  public MenuIconType getType() {
    return MenuIconType.ITEM;
  }

  public Material requireMaterial() throws MenuIconException {
    if (materialType == null)
      throw new MenuIconException("Item icon has an unknown or invalid item id");
    return materialType;
  }

  static class MaterialAdapter extends TypeAdapter<Material> {
    @Override
    public void write(JsonWriter out, Material value) throws IOException {
      if (value == null) {
        out.nullValue();
        return;
      }
      out.value(value.getKey().toString());
    }

    @Override
    public Material read(JsonReader in) throws IOException {
      if (in.peek() == JsonToken.NULL) {
        in.nextNull();
        return null;
      }

      String raw = in.nextString();
      try {
        NamespacedKey key = NamespacedKey.fromString(raw);
        return key == null ? null : RegistryUtil.find(Material.class, key);
      } catch (RuntimeException | LinkageError e) {
        return null;
      }
    }
  }
}
