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

import com.google.gson.JsonElement;

import java.util.List;
import java.util.Map;

/**
 * Raw shape of a preview JSON document, populated field-by-field from a Gson {@link com.google.gson.Gson}
 * binding by {@link PreviewDocumentParser}. Every field that can be either a JSON constant or a
 * live expression (numbers, colors, and the two booleans {@code framed}/{@code visible}) is typed
 * {@link JsonElement} so the parser decides constant-vs-expression itself instead of letting Gson's
 * reflective binding reject one of the two accepted shapes; see {@link PreviewDocumentParser} for
 * the exact per-field rules.
 */
final class PreviewDocument {
  MatchDef match;
  List<VariantDef> variants;
  CardDef card;
  List<ElementDef> elements;
}

/**
 * Match-criteria shape shared between the document's own top-level match and each
 * {@link VariantDef}. {@code special} and {@code priority} are only read from the top-level match;
 * {@code vars} values are JSON primitives only and are never treated as expressions (see
 * {@link PreviewDocumentParser}).
 */
class MatchDef {
  List<String> blocks;
  List<String> entities;
  String special;
  Integer priority;
  Map<String, JsonElement> vars;
}

/** A variant reuses the match shape to pick alternate {@code vars} for the same element templates. */
final class VariantDef extends MatchDef {
}

final class CardDef {
  // JsonElement rather than the brief sketch's plain Boolean: framed accepts a JSON boolean
  // constant OR an expression string, same as element `visible` below.
  JsonElement framed;
  String title;
  String accent;
  Integer minHalfWidth;
}

final class ElementDef {
  String type;
  JsonElement x;
  JsonElement y;
  JsonElement z;
  JsonElement width;
  JsonElement height;
  JsonElement size;
  JsonElement color;
  JsonElement wellColor;
  JsonElement index;
  JsonElement background;
  String text;
  // JsonElement rather than the brief sketch's plain String: visible accepts a JSON boolean
  // constant OR an expression string.
  JsonElement visible;
  RepeatDef repeat;
}

final class RepeatDef {
  JsonElement count;
  String var;
}
