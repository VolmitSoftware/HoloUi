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
 * Abstract syntax tree for the preview expression language.
 */
public sealed interface Expr permits Expr.Num, Expr.Str, Expr.Bool, Expr.ListLiteral,
    Expr.Var, Expr.Unary, Expr.Binary, Expr.Ternary, Expr.Call {

  record Num(double value) implements Expr {
  }

  record Str(String value) implements Expr {
  }

  record Bool(boolean value) implements Expr {
  }

  record ListLiteral(List<Expr> items) implements Expr {
  }

  /** Dotted identifier, e.g. "inventory.size". */
  record Var(String name) implements Expr {
  }

  /** Prefix operator: "-" or "!". */
  record Unary(String op, Expr operand) implements Expr {
  }

  /** "+ - * / % == != < <= > >= && ||". */
  record Binary(String op, Expr left, Expr right) implements Expr {
  }

  record Ternary(Expr condition, Expr ifTrue, Expr ifFalse) implements Expr {
  }

  /** Function call; names are never dotted. */
  record Call(String name, List<Expr> args) implements Expr {
  }
}
