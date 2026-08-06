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
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Standard function library for the preview expression language: math, color, and string
 * helpers. {@link #call} is a pure name-to-result dispatcher over already-evaluated arguments;
 * it returns {@code null} for unknown names so an {@link ExprScope} can resolve its own
 * context-specific names (e.g. {@code lang}, {@code count}, {@code occupied}) first and fall
 * back to this library.
 *
 * <p>Colors are the unsigned 32-bit ARGB value stored as a {@code double} (matching how
 * {@link ExprParser} lexes {@code #RRGGBB}-style literals); channel order is alpha, red, green,
 * blue from the most to least significant byte.
 */
public final class ExprFunctions {

  /** Evaluation errors carry no source position; see {@link ExprEvaluator}'s class-level note. */
  private static final int NO_POSITION = -1;

  /** Legacy colour/format codes: {@code &} followed by a colour digit, a format letter, or reset. */
  private static final Pattern LEGACY_CODE = Pattern.compile("&[0-9A-Fa-fK-Ok-oRr]");

  private ExprFunctions() {
  }

  public static Object call(String name, List<Object> args) {
    return switch (name) {
      case "clamp" -> clamp(name, args);
      case "lerp" -> lerp(name, args);
      case "min" -> min(name, args);
      case "max" -> max(name, args);
      case "floor" -> Math.floor(oneNumArg(name, args));
      case "ceil" -> Math.ceil(oneNumArg(name, args));
      case "round" -> (double) Math.round(oneNumArg(name, args));
      case "abs" -> Math.abs(oneNumArg(name, args));
      case "mod" -> mod(name, args);
      case "sin" -> Math.sin(oneNumArg(name, args));
      case "cos" -> Math.cos(oneNumArg(name, args));
      case "rgb" -> rgb(name, args);
      case "argb" -> argb(name, args);
      case "alpha" -> alpha(name, args);
      case "mix" -> mix(name, args);
      case "palette" -> palette(name, args);
      case "str" -> str(name, args);
      case "fixed" -> fixed(name, args);
      case "plain" -> plain(name, args);
      case "readable" -> readable(name, args);
      default -> null;
    };
  }

  // ---------------------------------------------------------------------
  // Math
  // ---------------------------------------------------------------------

  private static double clamp(String name, List<Object> args) {
    requireCount(name, args, 3);
    double x = numArg(name, args, 0);
    double lo = numArg(name, args, 1);
    double hi = numArg(name, args, 2);
    return Math.min(Math.max(x, lo), hi);
  }

  private static double lerp(String name, List<Object> args) {
    requireCount(name, args, 3);
    double a = numArg(name, args, 0);
    double b = numArg(name, args, 1);
    double t = numArg(name, args, 2);
    return a + (b - a) * t;
  }

  private static double min(String name, List<Object> args) {
    requireCount(name, args, 2);
    return Math.min(numArg(name, args, 0), numArg(name, args, 1));
  }

  private static double max(String name, List<Object> args) {
    requireCount(name, args, 2);
    return Math.max(numArg(name, args, 0), numArg(name, args, 1));
  }

  /**
   * {@code floorMod} semantics on doubles: {@code a - floor(a / b) * b}. A zero divisor throws
   * exactly like the {@code %} operator, rather than silently producing {@code NaN}: the two
   * remainder operations should agree within the DSL, and {@code NaN} cannot be expressed as a
   * JSON {@code expect} value, so leaving it unguarded would make the behavior structurally
   * unpinnable in the Dart-parity vectors file.
   */
  private static double mod(String name, List<Object> args) {
    requireCount(name, args, 2);
    double a = numArg(name, args, 0);
    double b = numArg(name, args, 1);
    if (b == 0.0) {
      throw new ExprException("division by zero", NO_POSITION);
    }
    return a - Math.floor(a / b) * b;
  }

  // ---------------------------------------------------------------------
  // Color
  // ---------------------------------------------------------------------

  private static double rgb(String name, List<Object> args) {
    requireCount(name, args, 3);
    int r = clampChannel(numArg(name, args, 0));
    int g = clampChannel(numArg(name, args, 1));
    int b = clampChannel(numArg(name, args, 2));
    return packArgb(0xFF, r, g, b);
  }

  private static double argb(String name, List<Object> args) {
    requireCount(name, args, 4);
    int a = clampChannel(numArg(name, args, 0));
    int r = clampChannel(numArg(name, args, 1));
    int g = clampChannel(numArg(name, args, 2));
    int b = clampChannel(numArg(name, args, 3));
    return packArgb(a, r, g, b);
  }

  private static double alpha(String name, List<Object> args) {
    requireCount(name, args, 2);
    long color = (long) numArg(name, args, 0);
    int a = clampChannel(numArg(name, args, 1));
    long replaced = (color & 0x00FFFFFFL) | ((long) a << 24);
    return (double) replaced;
  }

  /** Per-channel linear blend (including alpha): {@code channel = round(a + (b - a) * t)}, {@code t} clamped to [0, 1]. */
  private static double mix(String name, List<Object> args) {
    requireCount(name, args, 3);
    long c1 = (long) numArg(name, args, 0);
    long c2 = (long) numArg(name, args, 1);
    double t = Math.min(1.0, Math.max(0.0, numArg(name, args, 2)));
    int a = mixChannel(channel(c1, 24), channel(c2, 24), t);
    int r = mixChannel(channel(c1, 16), channel(c2, 16), t);
    int g = mixChannel(channel(c1, 8), channel(c2, 8), t);
    int b = mixChannel(channel(c1, 0), channel(c2, 0), t);
    return packArgb(a, r, g, b);
  }

  private static int mixChannel(int a, int b, double t) {
    return (int) Math.round(a + (b - a) * t);
  }

  private static int channel(long argb, int shift) {
    return (int) ((argb >> shift) & 0xFF);
  }

  private static int clampChannel(double value) {
    return (int) Math.round(Math.min(255.0, Math.max(0.0, value)));
  }

  private static double packArgb(int a, int r, int g, int b) {
    long value = ((long) a << 24) | ((long) r << 16) | ((long) g << 8) | b;
    return (double) value;
  }

  // ---------------------------------------------------------------------
  // Lists / strings
  // ---------------------------------------------------------------------

  /** Index is {@code (int) floor(index)}, wrapped into range with {@code floorMod}. */
  private static double palette(String name, List<Object> args) {
    requireCount(name, args, 2);
    Object listArg = args.get(0);
    if (!(listArg instanceof List<?> list)) {
      throw new ExprException(name + " argument 1 must be a list", NO_POSITION);
    }
    if (list.isEmpty()) {
      throw new ExprException(name + " list must not be empty", NO_POSITION);
    }
    int index = (int) Math.floor(numArg(name, args, 1));
    int wrapped = Math.floorMod(index, list.size());
    Object item = list.get(wrapped);
    if (!(item instanceof Double d)) {
      throw new ExprException(name + " list entries must be numbers", NO_POSITION);
    }
    return d;
  }

  private static String str(String name, List<Object> args) {
    requireCount(name, args, 1);
    return ExprEvaluator.stringify(args.get(0));
  }

  private static String fixed(String name, List<Object> args) {
    requireCount(name, args, 2);
    double x = numArg(name, args, 0);
    double digitsArg = numArg(name, args, 1);
    // digits must be a whole number in [0, 20]: negative/fractional precision is meaningless to
    // String.format, and >20 is valid in Java but a RangeError in Dart's toStringAsFixed, so the
    // shared range keeps both implementations of the contract behaving identically.
    if (digitsArg != Math.rint(digitsArg) || digitsArg < 0 || digitsArg > 20) {
      throw new ExprException(name + " argument 2 (digits) must be a whole number in [0, 20]", NO_POSITION);
    }
    int digits = (int) digitsArg;
    return String.format(Locale.ROOT, "%." + digits + "f", x);
  }

  /**
   * Drops legacy {@code &x} colour and format codes, leaving every other {@code &} alone. A
   * document that wants a localized title in its own styling rather than the catalog's writes
   * {@code '&f&l' + plain(lang(vars.titleKey))}: the entry's own codes are stripped first, so only
   * the document's prefix survives.
   */
  private static String plain(String name, List<Object> args) {
    requireCount(name, args, 1);
    return LEGACY_CODE.matcher(strArg(name, args, 0)).replaceAll("");
  }

  private static String readable(String name, List<Object> args) {
    requireCount(name, args, 1);
    return readable(strArg(name, args, 0));
  }

  /**
   * Turns an enum-style id into display text: {@code IRON_ORE -> "Iron Ore"}. This is how the
   * material behind a copper chest, a shelf, a minecart and a furnace input gets named, both from
   * a document's {@code readable(...)} call and from the state adapters, which pre-format a few
   * material names into variables before any expression sees them.
   */
  public static String readable(String value) {
    String[] words = value.toLowerCase(Locale.ENGLISH).split("_");
    StringBuilder out = new StringBuilder();
    for (int index = 0; index < words.length; index++) {
      if (index > 0) {
        out.append(' ');
      }
      String word = words[index];
      out.append(word.isEmpty() ? word : Character.toUpperCase(word.charAt(0)) + word.substring(1));
    }
    return out.toString();
  }

  // ---------------------------------------------------------------------
  // Argument helpers
  // ---------------------------------------------------------------------

  private static double oneNumArg(String name, List<Object> args) {
    requireCount(name, args, 1);
    return numArg(name, args, 0);
  }

  private static void requireCount(String name, List<Object> args, int count) {
    if (args.size() != count) {
      throw new ExprException(name + " expects " + count + " argument(s), got " + args.size(), NO_POSITION);
    }
  }

  private static double numArg(String name, List<Object> args, int index) {
    Object value = args.get(index);
    if (value instanceof Double d) {
      return d;
    }
    throw new ExprException(name + " argument " + (index + 1) + " must be a number", NO_POSITION);
  }

  private static String strArg(String name, List<Object> args, int index) {
    Object value = args.get(index);
    if (value instanceof String s) {
      return s;
    }
    throw new ExprException(name + " argument " + (index + 1) + " must be a string", NO_POSITION);
  }
}
