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

/**
 * Variable and function resolution for the preview expression language. Implementations bridge
 * live document/Bukkit state (variables, adapter snapshots, provider namespaces) and layered
 * function namespaces (context-specific names falling back to {@link ExprFunctions}) into the
 * tree-walking {@link ExprEvaluator}.
 */
public interface ExprScope {

  /**
   * Resolves a possibly-dotted variable name (e.g. {@code "inventory.size"}).
   *
   * @return a {@code Double}, {@code String}, {@code Boolean}, or {@code List<Object>}, or
   *     {@code null} if the name is not known to this scope. The evaluator raises an
   *     {@link ExprException} when this returns {@code null} for a referenced variable.
   */
  Object variable(String dottedName);

  /**
   * Invokes a function call by name with already-evaluated arguments.
   *
   * @return the call result ({@code Double}, {@code String}, or {@code Boolean}), or
   *     {@code null} if the name is unknown to this scope. The evaluator raises an
   *     {@link ExprException} when this returns {@code null} for a referenced call. Scope
   *     implementations should resolve their own context-specific names first, then fall back
   *     to {@link ExprFunctions#call(String, List)} for the standard library.
   */
  Object call(String name, List<Object> args);
}
