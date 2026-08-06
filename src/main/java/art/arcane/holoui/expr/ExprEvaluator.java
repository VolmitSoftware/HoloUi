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

import java.util.ArrayList;
import java.util.List;

/**
 * Tree-walking evaluator for the preview expression language. All numbers are {@code double};
 * strings, booleans, and {@code List<Object>} (list literals, only meaningful as function
 * arguments such as {@code palette}) round out the runtime value set.
 *
 * <p>Parse errors ({@link ExprParser}) carry the source character position they occurred at.
 * Evaluation errors raised here have no such position, since evaluation walks an already-parsed
 * tree with no remaining source text to point at; every {@link ExprException} thrown by this
 * class uses the {@link #NO_POSITION} sentinel for that reason.
 */
public final class ExprEvaluator {

  /** Evaluation errors carry no source position; see the class-level note above. */
  private static final int NO_POSITION = -1;

  private ExprEvaluator() {
  }

  public static Object eval(Expr expr, ExprScope scope) {
    return switch (expr) {
      case Expr.Num n -> n.value();
      case Expr.Str s -> s.value();
      case Expr.Bool b -> b.value();
      case Expr.ListLiteral l -> evalList(l, scope);
      case Expr.Var v -> evalVar(v, scope);
      case Expr.Unary u -> evalUnary(u, scope);
      case Expr.Binary b -> evalBinary(b, scope);
      case Expr.Ternary t -> evalTernary(t, scope);
      case Expr.Call c -> evalCall(c, scope);
    };
  }

  public static double number(Expr expr, ExprScope scope) {
    return requireNumber(eval(expr, scope));
  }

  public static boolean bool(Expr expr, ExprScope scope) {
    return requireBoolean(eval(expr, scope));
  }

  /** Numbers render per the integral-string rule: {@code 54.0 -> "54"}, {@code 3.5 -> "3.5"}. */
  public static String string(Expr expr, ExprScope scope) {
    return stringify(eval(expr, scope));
  }

  /** Reinterprets the numeric ARGB value as a signed 32-bit int: {@code (int) (long) value}. */
  public static int color(Expr expr, ExprScope scope) {
    return (int) (long) number(expr, scope);
  }

  /** True when {@code expr} contains no {@link Expr.Var} or {@link Expr.Call} node anywhere. */
  public static boolean isConstant(Expr expr) {
    return switch (expr) {
      case Expr.Num n -> true;
      case Expr.Str s -> true;
      case Expr.Bool b -> true;
      case Expr.ListLiteral l -> l.items().stream().allMatch(ExprEvaluator::isConstant);
      case Expr.Var v -> false;
      case Expr.Unary u -> isConstant(u.operand());
      case Expr.Binary b -> isConstant(b.left()) && isConstant(b.right());
      case Expr.Ternary t -> isConstant(t.condition()) && isConstant(t.ifTrue()) && isConstant(t.ifFalse());
      case Expr.Call c -> false;
    };
  }

  // ---------------------------------------------------------------------
  // Node evaluation
  // ---------------------------------------------------------------------

  private static List<Object> evalList(Expr.ListLiteral l, ExprScope scope) {
    List<Object> items = new ArrayList<>(l.items().size());
    for (Expr item : l.items()) {
      items.add(eval(item, scope));
    }
    return items;
  }

  private static Object evalVar(Expr.Var v, ExprScope scope) {
    Object value = scope.variable(v.name());
    if (value == null) {
      throw new ExprException("unknown variable: " + v.name(), NO_POSITION);
    }
    return value;
  }

  private static Object evalUnary(Expr.Unary u, ExprScope scope) {
    Object value = eval(u.operand(), scope);
    return switch (u.op()) {
      case "-" -> -requireNumber(value);
      case "!" -> !requireBoolean(value);
      default -> throw new ExprException("unknown unary operator: " + u.op(), NO_POSITION);
    };
  }

  private static Object evalTernary(Expr.Ternary t, ExprScope scope) {
    boolean condition = requireBoolean(eval(t.condition(), scope));
    return condition ? eval(t.ifTrue(), scope) : eval(t.ifFalse(), scope);
  }

  private static Object evalCall(Expr.Call c, ExprScope scope) {
    List<Object> args = new ArrayList<>(c.args().size());
    for (Expr arg : c.args()) {
      args.add(eval(arg, scope));
    }
    Object result = scope.call(c.name(), args);
    if (result == null) {
      throw new ExprException("unknown function: " + c.name(), NO_POSITION);
    }
    return result;
  }

  private static Object evalBinary(Expr.Binary b, ExprScope scope) {
    String op = b.op();
    // && and || short-circuit: the right operand is only evaluated (and its variables/calls only
    // looked up) when the left operand does not already decide the result.
    if (op.equals("&&")) {
      boolean left = requireBoolean(eval(b.left(), scope));
      return left && requireBoolean(eval(b.right(), scope));
    }
    if (op.equals("||")) {
      boolean left = requireBoolean(eval(b.left(), scope));
      return left || requireBoolean(eval(b.right(), scope));
    }

    Object leftVal = eval(b.left(), scope);
    Object rightVal = eval(b.right(), scope);
    return switch (op) {
      case "+" -> evalAdd(leftVal, rightVal);
      case "-" -> requireNumber(leftVal) - requireNumber(rightVal);
      case "*" -> requireNumber(leftVal) * requireNumber(rightVal);
      case "/" -> requireNumber(leftVal) / requireNonZero(rightVal);
      case "%" -> requireNumber(leftVal) % requireNonZero(rightVal);
      case "==" -> valuesEqual(leftVal, rightVal);
      case "!=" -> !valuesEqual(leftVal, rightVal);
      case "<" -> requireNumber(leftVal) < requireNumber(rightVal);
      case "<=" -> requireNumber(leftVal) <= requireNumber(rightVal);
      case ">" -> requireNumber(leftVal) > requireNumber(rightVal);
      case ">=" -> requireNumber(leftVal) >= requireNumber(rightVal);
      default -> throw new ExprException("unknown binary operator: " + op, NO_POSITION);
    };
  }

  /** {@code +} is numeric addition unless either side is a string, in which case it concatenates. */
  private static Object evalAdd(Object left, Object right) {
    if (left instanceof String || right instanceof String) {
      return stringify(left) + stringify(right);
    }
    return requireNumber(left) + requireNumber(right);
  }

  /** {@code ==}/{@code !=} compare numbers, strings, or booleans; mixed types are a type error. */
  private static boolean valuesEqual(Object left, Object right) {
    if (left instanceof Double a && right instanceof Double b) {
      return a.doubleValue() == b.doubleValue();
    }
    if (left instanceof String a && right instanceof String b) {
      return a.equals(b);
    }
    if (left instanceof Boolean a && right instanceof Boolean b) {
      return a.equals(b);
    }
    throw new ExprException("cannot compare " + typeName(left) + " and " + typeName(right), NO_POSITION);
  }

  // ---------------------------------------------------------------------
  // Coercion helpers
  // ---------------------------------------------------------------------

  private static double requireNumber(Object value) {
    if (value instanceof Double d) {
      return d;
    }
    throw new ExprException("expected number, got " + typeName(value), NO_POSITION);
  }

  private static boolean requireBoolean(Object value) {
    if (value instanceof Boolean b) {
      return b;
    }
    throw new ExprException("expected boolean, got " + typeName(value), NO_POSITION);
  }

  private static double requireNonZero(Object value) {
    double d = requireNumber(value);
    if (d == 0.0) {
      throw new ExprException("division by zero", NO_POSITION);
    }
    return d;
  }

  /**
   * Converts an evaluated value to its text form. Numbers use the integral-string rule (a double
   * {@code d} is integral when {@code d == Math.rint(d)} and {@code Double.isFinite(d)}, and
   * renders as a {@code long} with no decimal point); booleans render as {@code "true"}/
   * {@code "false"}; strings pass through unchanged. Package-visible so {@link ExprFunctions#call}
   * can share the same rule for {@code str(x)}.
   */
  static String stringify(Object value) {
    if (value instanceof String s) {
      return s;
    }
    if (value instanceof Double d) {
      return numberToString(d);
    }
    if (value instanceof Boolean b) {
      return b.toString();
    }
    throw new ExprException("cannot convert " + typeName(value) + " to string", NO_POSITION);
  }

  private static String numberToString(double value) {
    if (Double.isFinite(value) && value == Math.rint(value)) {
      return Long.toString((long) value);
    }
    return Double.toString(value);
  }

  private static String typeName(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof Double) {
      return "number";
    }
    if (value instanceof String) {
      return "string";
    }
    if (value instanceof Boolean) {
      return "boolean";
    }
    if (value instanceof List) {
      return "list";
    }
    return value.getClass().getSimpleName();
  }
}
