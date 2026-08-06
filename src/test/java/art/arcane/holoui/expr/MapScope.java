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
package art.arcane.holoui.expr;

import java.util.List;
import java.util.Map;

/**
 * Minimal {@link ExprScope} for tests: variables come from a fixed map, calls fall straight
 * through to {@link ExprFunctions} (there are no context-specific names at this layer).
 */
final class MapScope implements ExprScope {

  private final Map<String, Object> variables;

  MapScope(Map<String, Object> variables) {
    this.variables = variables;
  }

  static MapScope empty() {
    return new MapScope(Map.of());
  }

  @Override
  public Object variable(String dottedName) {
    return variables.get(dottedName);
  }

  @Override
  public Object call(String name, List<Object> args) {
    return ExprFunctions.call(name, args);
  }
}
