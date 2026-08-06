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

import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class ExprEvaluatorTest {

  private static final double DELTA = 1e-9;
  private static final ExprScope EMPTY = MapScope.empty();

  private static Object eval(String source) {
    return ExprEvaluator.eval(ExprParser.parse(source), EMPTY);
  }

  private static Object eval(String source, Map<String, Object> vars) {
    return ExprEvaluator.eval(ExprParser.parse(source), new MapScope(vars));
  }

  // ---------------------------------------------------------------------
  // Arithmetic operators
  // ---------------------------------------------------------------------

  @Test
  public void addsNumbers() {
    Assert.assertEquals(3.0, (Double) eval("1 + 2"), DELTA);
  }

  @Test
  public void subtractsNumbers() {
    Assert.assertEquals(7.0, (Double) eval("10 - 3"), DELTA);
  }

  @Test
  public void multipliesNumbers() {
    Assert.assertEquals(7.0, (Double) eval("2 * 3.5"), DELTA);
  }

  @Test
  public void dividesNumbers() {
    Assert.assertEquals(3.5, (Double) eval("7 / 2"), DELTA);
  }

  @Test
  public void modOperatorUsesRemainder() {
    Assert.assertEquals(1.0, (Double) eval("7 % 3"), DELTA);
  }

  @Test(expected = ExprException.class)
  public void divisionByZeroThrows() {
    eval("1 / 0");
  }

  @Test(expected = ExprException.class)
  public void moduloByZeroThrows() {
    eval("5 % 0");
  }

  @Test
  public void divisionByZeroMessageIsExact() {
    try {
      eval("1 / 0");
      Assert.fail();
    } catch (ExprException e) {
      Assert.assertEquals("division by zero", e.getMessage());
    }
  }

  @Test
  public void unaryMinusNegates() {
    Assert.assertEquals(-5.0, (Double) eval("-5"), DELTA);
    Assert.assertEquals(5.0, (Double) eval("-(-5)"), DELTA);
  }

  @Test
  public void unaryNotInverts() {
    Assert.assertEquals(false, eval("!true"));
    Assert.assertEquals(true, eval("!false"));
  }

  // ---------------------------------------------------------------------
  // Comparisons
  // ---------------------------------------------------------------------

  @Test
  public void relationalOperatorsCompareNumbers() {
    Assert.assertEquals(true, eval("5 > 3"));
    Assert.assertEquals(false, eval("5 < 3"));
    Assert.assertEquals(true, eval("5 >= 5"));
    Assert.assertEquals(false, eval("5 <= 4"));
  }

  @Test
  public void equalityWorksOnNumbers() {
    Assert.assertEquals(true, eval("5 == 5"));
    Assert.assertEquals(false, eval("5 != 5"));
  }

  @Test
  public void equalityWorksOnStrings() {
    Assert.assertEquals(true, eval("'a' == 'a'"));
    Assert.assertEquals(true, eval("'a' != 'b'"));
  }

  @Test
  public void equalityWorksOnBooleans() {
    Assert.assertEquals(true, eval("true == true"));
    Assert.assertEquals(true, eval("true != false"));
  }

  @Test(expected = ExprException.class)
  public void relationalOperatorsRejectStrings() {
    eval("'a' < 'b'");
  }

  @Test(expected = ExprException.class)
  public void equalityRejectsMixedTypes() {
    eval("1 == '1'");
  }

  // ---------------------------------------------------------------------
  // Logical operators (short-circuit)
  // ---------------------------------------------------------------------

  @Test
  public void andRequiresBothTrue() {
    Assert.assertEquals(false, eval("true && false"));
    Assert.assertEquals(true, eval("true && true"));
  }

  @Test
  public void orRequiresEitherTrue() {
    Assert.assertEquals(true, eval("true || false"));
    Assert.assertEquals(false, eval("false || false"));
  }

  @Test(expected = ExprException.class)
  public void andRejectsNonBooleanOperand() {
    eval("1 && true");
  }

  @Test
  public void andShortCircuitsWithoutEvaluatingRightOperand() {
    CountingScope scope = new CountingScope();
    Object result = ExprEvaluator.eval(ExprParser.parse("false && rightSide"), scope);
    Assert.assertEquals(false, result);
    Assert.assertEquals(0, scope.lookups);
  }

  @Test
  public void orShortCircuitsWithoutEvaluatingRightOperand() {
    CountingScope scope = new CountingScope();
    Object result = ExprEvaluator.eval(ExprParser.parse("true || rightSide"), scope);
    Assert.assertEquals(true, result);
    Assert.assertEquals(0, scope.lookups);
  }

  @Test
  public void ternaryOnlyEvaluatesTakenBranch() {
    CountingScope scope = new CountingScope();
    Object result = ExprEvaluator.eval(ExprParser.parse("true ? 1 : untaken"), scope);
    Assert.assertEquals(1.0, (Double) result, DELTA);
    Assert.assertEquals(0, scope.lookups);
  }

  private static final class CountingScope implements ExprScope {
    int lookups = 0;

    @Override
    public Object variable(String dottedName) {
      lookups++;
      return true;
    }

    @Override
    public Object call(String name, List<Object> args) {
      return ExprFunctions.call(name, args);
    }
  }

  // ---------------------------------------------------------------------
  // Ternary
  // ---------------------------------------------------------------------

  @Test
  public void ternaryEvaluatesChosenBranch() {
    Assert.assertEquals(1.0, (Double) eval("progress * 8 > i ? 1 : 0", Map.of("progress", 0.5, "i", 3.0)), DELTA);
  }

  @Test(expected = ExprException.class)
  public void ternaryConditionMustBeBoolean() {
    eval("1 ? 2 : 3");
  }

  // ---------------------------------------------------------------------
  // Variables
  // ---------------------------------------------------------------------

  @Test(expected = ExprException.class)
  public void unknownVariableThrows() {
    eval("missing + 1");
  }

  @Test
  public void unknownVariableMessageContainsName() {
    try {
      eval("missing + 1");
      Assert.fail();
    } catch (ExprException e) {
      Assert.assertTrue(e.getMessage().contains("missing"));
    }
  }

  @Test
  public void unknownVariablePositionIsMinusOne() {
    try {
      eval("missing + 1");
      Assert.fail();
    } catch (ExprException e) {
      Assert.assertEquals(-1, e.position());
    }
  }

  @Test
  public void dottedVariableResolvesThroughScope() {
    Assert.assertEquals(64.0, (Double) eval("inventory.size", Map.of("inventory.size", 64.0)), DELTA);
  }

  // ---------------------------------------------------------------------
  // Functions
  // ---------------------------------------------------------------------

  @Test(expected = ExprException.class)
  public void unknownFunctionThrows() {
    eval("bogus(1)");
  }

  @Test(expected = ExprException.class)
  public void wrongArgumentCountThrows() {
    eval("clamp(1, 2)");
  }

  @Test
  public void clampClampsIntoRange() {
    Assert.assertEquals(10.0, (Double) eval("clamp(15, 0, 10)"), DELTA);
    Assert.assertEquals(0.0, (Double) eval("clamp(-5, 0, 10)"), DELTA);
    Assert.assertEquals(5.0, (Double) eval("clamp(5, 0, 10)"), DELTA);
  }

  @Test
  public void lerpInterpolatesLinearly() {
    Assert.assertEquals(2.5, (Double) eval("lerp(0, 10, 0.25)"), DELTA);
  }

  @Test
  public void minAndMaxPickExtremes() {
    Assert.assertEquals(3.0, (Double) eval("min(3, 7)"), DELTA);
    Assert.assertEquals(7.0, (Double) eval("max(3, 7)"), DELTA);
  }

  @Test
  public void floorCeilRoundAbs() {
    Assert.assertEquals(3.0, (Double) eval("floor(3.7)"), DELTA);
    Assert.assertEquals(4.0, (Double) eval("ceil(3.2)"), DELTA);
    Assert.assertEquals(3.0, (Double) eval("round(2.5)"), DELTA);
    Assert.assertEquals(7.0, (Double) eval("abs(-7)"), DELTA);
  }

  @Test
  public void roundOfNegativeHalfRoundsTowardPositiveInfinity() {
    // Java's Math.round(-2.5) == -2 (floor(x + 0.5)), NOT -3 (half-away-from-zero, Dart's rule).
    // Pinning this forces a Dart twin to implement Java's rounding rule deliberately.
    Assert.assertEquals(-2.0, (Double) eval("round(-2.5)"), DELTA);
  }

  @Test
  public void modUsesFloorModSemantics() {
    Assert.assertEquals(2.0, (Double) eval("mod(-1, 3)"), DELTA);
  }

  @Test(expected = ExprException.class)
  public void modWrongArgumentCountThrows() {
    eval("mod(1)");
  }

  @Test(expected = ExprException.class)
  public void modTooManyArgumentsThrows() {
    eval("mod(1, 2, 3)");
  }

  @Test
  public void modByZeroThrowsDivisionByZero() {
    // mod() must agree with the % operator on a zero divisor: NaN can't be expressed as a JSON
    // expect value, so leaving this unguarded would make the behavior unpinnable cross-repo.
    try {
      eval("mod(1, 0)");
      Assert.fail();
    } catch (ExprException e) {
      Assert.assertEquals("division by zero", e.getMessage());
    }
  }

  @Test
  public void sinCosUseRadians() {
    Assert.assertEquals(0.0, (Double) eval("sin(0)"), DELTA);
    Assert.assertEquals(1.0, (Double) eval("cos(0)"), DELTA);
    // Pins radians (not degrees): cos(pi) == -1 and sin(pi/2) == 1 only hold in radian mode.
    Assert.assertEquals(-1.0, (Double) eval("cos(3.14159265)"), DELTA);
    Assert.assertEquals(0.9999999999999997, (Double) eval("sin(1.5707963)"), DELTA);
  }

  @Test
  public void rgbPacksOpaqueColorAndClamps() {
    Assert.assertEquals((double) 0xFF0000FFL, (Double) eval("rgb(0, 0, 255)"), DELTA);
    Assert.assertEquals((double) 0xFFFF0080L, (Double) eval("rgb(300, -10, 128)"), DELTA);
  }

  @Test
  public void argbPacksAllChannels() {
    Assert.assertEquals((double) 0x800A141EL, (Double) eval("argb(128, 10, 20, 30)"), DELTA);
  }

  @Test
  public void alphaReplacesOnlyAlphaByte() {
    Assert.assertEquals((double) 0x80FFFFFFL, (Double) eval("alpha(#FFFFFFFF, 128)"), DELTA);
  }

  @Test
  public void mixBlendsPerChannelWithRounding() {
    // channel = (int) Math.round(a + (b - a) * t), t clamped to [0, 1]; hand-computed:
    // 0 + (255 - 0) * 0.25 = 63.75 -> round -> 64 (0x40); alpha 255 stays 255.
    Assert.assertEquals((double) 0xFF404040L, (Double) eval("mix(#FF000000, #FFFFFFFF, 0.25)"), DELTA);
    // 0 + (255 - 0) * 0.5 = 127.5 -> round -> 128 (0x80).
    Assert.assertEquals((double) 0xFF808080L, (Double) eval("mix(#FF000000, #FFFFFFFF, 0.5)"), DELTA);
  }

  @Test
  public void mixClampsTOutsideZeroToOne() {
    Assert.assertEquals((double) 0xFF000000L, (Double) eval("mix(#FF000000, #FFFFFFFF, -1)"), DELTA);
    Assert.assertEquals((double) 0xFFFFFFFFL, (Double) eval("mix(#FF000000, #FFFFFFFF, 2)"), DELTA);
  }

  @Test
  public void paletteWrapsIndexWithFloorMod() {
    Assert.assertEquals(2.0, (Double) eval("palette([1, 2, 3], 4)"), DELTA);
    Assert.assertEquals(30.0, (Double) eval("palette([10, 20, 30], -1)"), DELTA);
  }

  @Test(expected = ExprException.class)
  public void paletteRejectsEmptyList() {
    eval("palette([], 0)");
  }

  @Test
  public void fixedFormatsWithGivenDigits() {
    Assert.assertEquals("3.14", eval("fixed(3.14159, 2)"));
    Assert.assertEquals("10.000", eval("fixed(10, 3)"));
  }

  @Test(expected = ExprException.class)
  public void fixedRejectsNegativeDigits() {
    eval("fixed(1, -1)");
  }

  @Test(expected = ExprException.class)
  public void fixedRejectsDigitsAboveTwenty() {
    eval("fixed(1, 21)");
  }

  @Test(expected = ExprException.class)
  public void fixedRejectsFractionalDigits() {
    eval("fixed(1, 2.5)");
  }

  @Test
  public void fixedAcceptsBoundaryDigitsOfTwenty() {
    Assert.assertEquals("1." + "0".repeat(20), eval("fixed(1, 20)"));
  }

  @Test
  public void strFormatsEachValueType() {
    Assert.assertEquals("54", eval("str(54.0)"));
    Assert.assertEquals("3.5", eval("str(3.5)"));
    Assert.assertEquals("true", eval("str(true)"));
    Assert.assertEquals("hi", eval("str('hi')"));
  }

  @Test
  public void strComposesWithFixed() {
    Assert.assertEquals("3.14", eval("str(fixed(3.14159, 2))"));
  }

  @Test
  public void plainStripsLegacyColorCodes() {
    Assert.assertEquals("Chest", eval("plain('&6&lChest')"));
    Assert.assertEquals("White Shulker", eval("plain('&lWhite Shulker')"));
    Assert.assertEquals("Chest", eval("plain('Chest')"));
    Assert.assertEquals("", eval("plain('')"));
  }

  @Test
  public void plainKeepsAmpersandsThatAreNotColorCodes() {
    Assert.assertEquals("Salt & Pepper", eval("plain('Salt & Pepper')"));
    Assert.assertEquals("&#123456", eval("plain('&#123456')"));
  }

  @Test(expected = ExprException.class)
  public void plainRejectsANonStringArgument() {
    eval("plain(5)");
  }

  @Test
  public void readableTitleCasesAnEnumName() {
    Assert.assertEquals("Iron Ore", eval("readable('IRON_ORE')"));
    Assert.assertEquals("Copper Chest", eval("readable('COPPER_CHEST')"));
    Assert.assertEquals("Powder Snow Cauldron", eval("readable('POWDER_SNOW_CAULDRON')"));
    Assert.assertEquals("Chest", eval("readable('chest')"));
    Assert.assertEquals("", eval("readable('')"));
  }

  /**
   * Separator edges, pinned because {@code split} disagrees across languages: Java drops trailing
   * empty segments (Dart keeps them), so a port that does not reproduce these three drifts from the
   * shipped documents' titles. Same cases live in {@code expr_test_vectors.json}.
   */
  @Test
  public void readableHandlesEmptySegmentsTheWayJavaSplitDoes() {
    Assert.assertEquals("Iron", eval("readable('IRON_')"));
    Assert.assertEquals(" Iron", eval("readable('_IRON')"));
    Assert.assertEquals("Iron  Ore", eval("readable('IRON__ORE')"));
  }

  @Test(expected = ExprException.class)
  public void readableRejectsANonStringArgument() {
    eval("readable(5)");
  }

  // ---------------------------------------------------------------------
  // String concatenation / integral-string coercion
  // ---------------------------------------------------------------------

  @Test
  public void stringConcatenationWithInteger() {
    Assert.assertEquals("x5", eval("'x' + 5"));
  }

  @Test
  public void stringConcatenationWithDecimal() {
    Assert.assertEquals("n=3.5", eval("'n=' + 3.5"));
  }

  @Test
  public void stringConcatenationWithBoolean() {
    Assert.assertEquals("flag:true", eval("'flag:' + true"));
  }

  @Test(expected = ExprException.class)
  public void arithmeticRejectsBooleanOperand() {
    eval("true + 1");
  }

  @Test
  public void numberToStringUsesIntegralRule() {
    // Direct AST construction: 1e9 is not valid DSL literal syntax (no exponent notation),
    // so this exercises ExprEvaluator.string's integral-string rule at the Java-double level.
    Assert.assertEquals("54", ExprEvaluator.string(new Expr.Num(54.0), EMPTY));
    Assert.assertEquals("3.5", ExprEvaluator.string(new Expr.Num(3.5), EMPTY));
    Assert.assertEquals("1000000000", ExprEvaluator.string(new Expr.Num(1e9), EMPTY));
  }

  // ---------------------------------------------------------------------
  // List literals
  // ---------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  public void listLiteralEvaluatesToListOfValues() {
    Object result = eval("[1, 2, 3]");
    Assert.assertTrue(result instanceof List);
    List<Object> list = (List<Object>) result;
    Assert.assertEquals(List.of(1.0, 2.0, 3.0), list);
  }

  // ---------------------------------------------------------------------
  // number(), bool(), color() accessors
  // ---------------------------------------------------------------------

  @Test
  public void numberAccessorReturnsDouble() {
    Assert.assertEquals(7.0, ExprEvaluator.number(ExprParser.parse("3 + 4"), EMPTY), DELTA);
  }

  @Test
  public void boolAccessorReturnsBoolean() {
    Assert.assertTrue(ExprEvaluator.bool(ExprParser.parse("1 < 2"), EMPTY));
  }

  @Test
  public void colorAccessorReinterpretsAsSignedInt() {
    // 0xFFFFFFFF as an unsigned ARGB double reinterprets to the signed int -1.
    Assert.assertEquals(-1, ExprEvaluator.color(ExprParser.parse("#FFFFFFFF"), EMPTY));
    Assert.assertEquals(0x00FF0000, ExprEvaluator.color(ExprParser.parse("rgb(255, 0, 0) - #FF000000"), EMPTY));
  }

  // ---------------------------------------------------------------------
  // isConstant
  // ---------------------------------------------------------------------

  @Test
  public void isConstantTrueForLiteralsOnly() {
    Assert.assertTrue(ExprEvaluator.isConstant(ExprParser.parse("1 + 2 * (3 - 4) / 5")));
    Assert.assertTrue(ExprEvaluator.isConstant(ExprParser.parse("true ? 'a' : 'b'")));
    Assert.assertTrue(ExprEvaluator.isConstant(ExprParser.parse("[1, 2, 3]")));
  }

  @Test
  public void isConstantFalseWhenVarPresent() {
    Assert.assertFalse(ExprEvaluator.isConstant(ExprParser.parse("1 + x")));
    Assert.assertFalse(ExprEvaluator.isConstant(ExprParser.parse("true ? x : 1")));
    Assert.assertFalse(ExprEvaluator.isConstant(ExprParser.parse("[1, x, 3]")));
  }

  @Test
  public void isConstantFalseWhenCallPresent() {
    Assert.assertFalse(ExprEvaluator.isConstant(ExprParser.parse("clamp(1, 0, 2)")));
  }
}
