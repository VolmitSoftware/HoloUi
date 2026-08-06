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

public class ExprParserTest {

  @Test
  public void parsesNumberLiteral() {
    Assert.assertEquals(new Expr.Num(42.0), ExprParser.parse("42"));
  }

  @Test
  public void parsesColorLiteralAsUnsignedArgb() {
    Assert.assertEquals(new Expr.Num((double) 0xF21B1B22L), ExprParser.parse("#F21B1B22"));
    Assert.assertEquals(new Expr.Num((double) 0xFF33333EL), ExprParser.parse("#33333E"));
    Assert.assertEquals(new Expr.Num((double) 0xFFFF0000L), ExprParser.parse("#F00"));
  }

  @Test
  public void precedenceMulOverAdd() {
    Assert.assertEquals(
        new Expr.Binary("+", new Expr.Num(1), new Expr.Binary("*", new Expr.Num(2), new Expr.Num(3))),
        ExprParser.parse("1 + 2 * 3"));
  }

  @Test
  public void ternaryIsRightAssociative() {
    Expr parsed = ExprParser.parse("a ? 1 : b ? 2 : 3");
    Assert.assertTrue(((Expr.Ternary) parsed).ifFalse() instanceof Expr.Ternary);
  }

  @Test
  public void dottedVariableAndCall() {
    Assert.assertEquals(new Expr.Var("inventory.size"), ExprParser.parse("inventory.size"));
    Assert.assertEquals(new Expr.Call("clamp", List.of(new Expr.Var("x"), new Expr.Num(0), new Expr.Num(1))),
        ExprParser.parse("clamp(x, 0, 1)"));
  }

  @Test(expected = ExprException.class)
  public void rejectsDottedCallName() {
    ExprParser.parse("math.clamp(1, 0, 2)");
  }

  @Test(expected = ExprException.class)
  public void rejectsUnclosedParen() {
    ExprParser.parse("(1 + 2");
  }

  @Test(expected = ExprException.class)
  public void rejectsBadHex() {
    ExprParser.parse("#F0000");
  }

  @Test
  public void errorCarriesPosition() {
    try {
      ExprParser.parse("1 + + 2");
      Assert.fail();
    } catch (ExprException e) {
      Assert.assertTrue(e.position() >= 4);
    }
  }

  @Test(expected = ExprException.class)
  public void rejectsUnclosedBracket() {
    ExprParser.parse("[1, 2");
  }

  @Test(expected = ExprException.class)
  public void rejectsEmptySource() {
    ExprParser.parse("");
  }

  @Test(expected = ExprException.class)
  public void rejectsUnterminatedString() {
    ExprParser.parse("'abc");
  }

  @Test
  public void rejectsUnrecognizedEscapeAtBackslashPosition() {
    try {
      ExprParser.parse("'a\\tb'");
      Assert.fail();
    } catch (ExprException e) {
      Assert.assertEquals(2, e.position());
    }
  }

  @Test
  public void parsesStringEscapes() {
    Assert.assertEquals(new Expr.Str("a\nb"), ExprParser.parse("'a\\nb'"));
    Assert.assertEquals(new Expr.Str("a'b"), ExprParser.parse("'a\\'b'"));
    Assert.assertEquals(new Expr.Str("a\"b"), ExprParser.parse("'a\\\"b'"));
    Assert.assertEquals(new Expr.Str("a\\b"), ExprParser.parse("'a\\\\b'"));
  }

  @Test
  public void parsesDoubleQuotedString() {
    Assert.assertEquals(new Expr.Str("hello"), ExprParser.parse("\"hello\""));
  }

  @Test
  public void parsesBooleanLiterals() {
    Assert.assertEquals(new Expr.Bool(true), ExprParser.parse("true"));
    Assert.assertEquals(new Expr.Bool(false), ExprParser.parse("false"));
  }

  @Test
  public void parsesListLiteral() {
    Assert.assertEquals(
        new Expr.ListLiteral(List.of(new Expr.Num(1), new Expr.Num(2), new Expr.Num(3))),
        ExprParser.parse("[1, 2, 3]"));
  }

  @Test
  public void parsesEmptyListLiteral() {
    Assert.assertEquals(new Expr.ListLiteral(List.of()), ExprParser.parse("[]"));
  }

  @Test
  public void parsesUnaryMinusAndNot() {
    Assert.assertEquals(new Expr.Unary("-", new Expr.Num(5)), ExprParser.parse("-5"));
    Assert.assertEquals(new Expr.Unary("!", new Expr.Bool(true)), ExprParser.parse("!true"));
  }

  @Test
  public void comparisonChainsAreLeftAssociative() {
    Expr parsed = ExprParser.parse("1 < 2 < 3");
    Expr.Binary outer = (Expr.Binary) parsed;
    Assert.assertEquals("<", outer.op());
    Assert.assertTrue(outer.left() instanceof Expr.Binary);
    Assert.assertEquals(new Expr.Num(3), outer.right());
  }

  @Test
  public void andBindsTighterThanOr() {
    Assert.assertEquals(
        new Expr.Binary("||", new Expr.Bool(true), new Expr.Binary("&&", new Expr.Bool(false), new Expr.Bool(true))),
        ExprParser.parse("true || false && true"));
  }

  @Test
  public void parsesAllComparisonOperators() {
    Assert.assertEquals(new Expr.Binary("==", new Expr.Num(1), new Expr.Num(1)), ExprParser.parse("1 == 1"));
    Assert.assertEquals(new Expr.Binary("!=", new Expr.Num(1), new Expr.Num(2)), ExprParser.parse("1 != 2"));
    Assert.assertEquals(new Expr.Binary("<=", new Expr.Num(1), new Expr.Num(2)), ExprParser.parse("1 <= 2"));
    Assert.assertEquals(new Expr.Binary(">=", new Expr.Num(1), new Expr.Num(2)), ExprParser.parse("1 >= 2"));
  }

  @Test
  public void parsesModulo() {
    Assert.assertEquals(new Expr.Binary("%", new Expr.Num(7), new Expr.Num(3)), ExprParser.parse("7 % 3"));
  }

  @Test
  public void parenthesesOverridePrecedence() {
    Assert.assertEquals(
        new Expr.Binary("*", new Expr.Binary("+", new Expr.Num(1), new Expr.Num(2)), new Expr.Num(3)),
        ExprParser.parse("(1 + 2) * 3"));
  }

  @Test
  public void toleratesExtraWhitespace() {
    Assert.assertEquals(new Expr.Binary("+", new Expr.Num(1), new Expr.Num(2)), ExprParser.parse("  1   +\t2  \n"));
  }

  @Test
  public void parsesDecimalNumber() {
    Assert.assertEquals(new Expr.Num(3.5), ExprParser.parse("3.5"));
  }

  @Test
  public void ternaryConditionUsesOrPrecedence() {
    Assert.assertEquals(
        new Expr.Ternary(
            new Expr.Binary("||", new Expr.Bool(true), new Expr.Bool(false)),
            new Expr.Num(1),
            new Expr.Num(2)),
        ExprParser.parse("true || false ? 1 : 2"));
  }

  @Test(expected = ExprException.class)
  public void rejectsDeeplyNestedParensAsExprExceptionNotStackOverflow() {
    ExprParser.parse("(".repeat(5000) + "1" + ")".repeat(5000));
  }

  @Test(expected = ExprException.class)
  public void rejectsDeeplyNestedUnaryChainAsExprExceptionNotStackOverflow() {
    ExprParser.parse("!".repeat(5000) + "true");
  }

  @Test(expected = ExprException.class)
  public void rejectsDeeplyNestedTernaryAsExprExceptionNotStackOverflow() {
    StringBuilder source = new StringBuilder();
    for (int i = 0; i < 5000; i++) {
      source.append("a?1:");
    }
    source.append("1");
    ExprParser.parse(source.toString());
  }

  @Test
  public void nestingAtTheCapStillParses() {
    Expr parsed = ExprParser.parse("(".repeat(255) + "1" + ")".repeat(255));
    Assert.assertEquals(new Expr.Num(1), parsed);
  }
}
