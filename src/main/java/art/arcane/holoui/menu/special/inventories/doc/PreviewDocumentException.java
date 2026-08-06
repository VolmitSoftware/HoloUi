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

/**
 * Compile-time failure for a preview JSON document: malformed JSON, a structurally invalid field,
 * or an expression that fails to parse, fold, or reference a known variable. {@link #getMessage()}
 * is always {@code documentName + " " + message}, so callers can log it directly; {@code message}
 * itself should read {@code "<field path>: <detail>"} (e.g.
 * {@code "elements[3].color: unexpected token at 12"}) for every failure that traces to one field.
 */
public final class PreviewDocumentException extends RuntimeException {

  private final String documentName;

  public PreviewDocumentException(String documentName, String message, Throwable cause) {
    super(documentName + " " + message, cause);
    this.documentName = documentName;
  }

  public String documentName() {
    return documentName;
  }
}
