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
package art.arcane.holoui.menu.special.inventories.doc;

import art.arcane.holoui.menu.special.inventories.PreviewElement;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.List;
import java.util.Locale;

/**
 * Renders a built element list to the canonical text the golden snapshots are compared on. The
 * snapshots in {@code src/test/resources/golden} are the permanent regression record for the
 * preview engine: a document is rebuilt through here and compared byte for byte, so any difference
 * is a behaviour difference.
 *
 * <p>Determinism rules, in order of how easily they are broken:
 *
 * <ul>
 *   <li>Nothing is sorted or re-ordered. Elements keep their list order and every object keeps a
 *       fixed field order, because element order is itself part of the contract (it drives the
 *       render order of the preview).
 *   <li>The lazy suppliers on {@code Cell} and {@code Label} are evaluated exactly once, here, in
 *       list order. Some of them mutate a {@code TimeFlowTracker} as a side effect, so evaluating
 *       twice or out of order would change the result.
 *   <li>Colours are written as unsigned {@code #AARRGGBB}, never as the signed {@code int} they
 *       are stored in.
 *   <li>Label components are {@link net.kyori.adventure.text.Component#compact() compacted} before
 *       serialization, so that a component assembled programmatically and the same component
 *       parsed from a string are not separated by an empty wrapper node.
 * </ul>
 *
 * <p>No field is excluded. The engine has no {@code Random}, no wall clock and no identity-derived
 * value: every element it emits is a pure function of the fake block state and the game time it is
 * sampled at, both of which {@link GoldenFakes} pins.
 */
public final class GoldenSerializer {

  private GoldenSerializer() {
  }

  public static String serialize(List<PreviewElement> elements) {
    StringBuilder json = new StringBuilder("[\n");
    for (int index = 0; index < elements.size(); index++) {
      json.append("  ").append(element(elements.get(index)));
      if (index < elements.size() - 1) {
        json.append(',');
      }
      json.append('\n');
    }
    return json.append("]\n").toString();
  }

  private static String element(PreviewElement element) {
    return switch (element) {
      case PreviewElement.Panel panel -> "{\"type\":\"panel\""
          + position(panel)
          + ",\"width\":" + panel.width()
          + ",\"height\":" + panel.height()
          + ",\"color\":" + color(panel.color())
          + "}";
      case PreviewElement.Cell cell -> "{\"type\":\"cell\""
          + position(cell)
          + ",\"size\":" + cell.size()
          + ",\"color\":" + color(cell.color().getAsInt())
          + "}";
      case PreviewElement.Slot slot -> "{\"type\":\"slot\""
          + position(slot)
          + ",\"size\":" + slot.size()
          + ",\"wellColor\":" + color(slot.wellColor())
          + ",\"slot\":" + slot.slot()
          + "}";
      case PreviewElement.Label label -> "{\"type\":\"label\""
          + position(label)
          + ",\"text\":" + GsonComponentSerializer.gson().serialize(label.text().get().compact())
          + ",\"background\":" + color(label.backgroundColor())
          + "}";
    };
  }

  private static String position(PreviewElement element) {
    return ",\"x\":" + element.x() + ",\"y\":" + element.y() + ",\"z\":" + element.z();
  }

  private static String color(int argb) {
    return "\"#" + String.format(Locale.ROOT, "%08X", argb) + "\"";
  }
}
