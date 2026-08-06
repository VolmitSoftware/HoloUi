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
 * Hand-rolled lexer + recursive-descent (precedence-climbing) parser for the preview
 * expression language. See task-1-brief.md for the grammar this implements.
 */
public final class ExprParser {

  /**
   * Cap on live recursion depth through {@link #parseTernary()} and {@link #parseUnary()} — the
   * only two productions that recurse into themselves (directly or through a parenthesized,
   * bracketed, or call-argument sub-expression, which all re-enter {@code parseTernary}).
   * Pathological input like 5000 nested parens would otherwise recurse the JVM call stack into a
   * {@link StackOverflowError}, an {@link Error} rather than an {@link ExprException}, which is
   * uncatchable by the document loader's {@code RuntimeException} guard.
   */
  private static final int MAX_DEPTH = 256;

  private final List<Token> tokens;
  private int pos = 0;
  private int depth = 0;

  private ExprParser(String source) {
    this.tokens = tokenize(source);
  }

  public static Expr parse(String source) {
    return new ExprParser(source).parseProgram();
  }

  // ---------------------------------------------------------------------
  // Parser (precedence-climbing, low to high):
  // ternary -> || -> && -> equality -> relational -> additive -> multiplicative -> unary -> primary
  // ---------------------------------------------------------------------

  private Expr parseProgram() {
    if (tokens.size() == 1) {
      throw new ExprException("empty source", 0);
    }
    Expr expr = parseTernary();
    if (!check(TokenType.EOF)) {
      throw new ExprException("unexpected token", peek().position);
    }
    return expr;
  }

  private Expr parseTernary() {
    enterNesting();
    try {
      Expr condition = parseOr();
      if (match(TokenType.QUESTION)) {
        Expr ifTrue = parseTernary();
        expect(TokenType.COLON, "expected ':'");
        Expr ifFalse = parseTernary();
        return new Expr.Ternary(condition, ifTrue, ifFalse);
      }
      return condition;
    } finally {
      depth--;
    }
  }

  private Expr parseOr() {
    Expr left = parseAnd();
    while (check(TokenType.OR)) {
      advance();
      left = new Expr.Binary("||", left, parseAnd());
    }
    return left;
  }

  private Expr parseAnd() {
    Expr left = parseEquality();
    while (check(TokenType.AND)) {
      advance();
      left = new Expr.Binary("&&", left, parseEquality());
    }
    return left;
  }

  private Expr parseEquality() {
    Expr left = parseRelational();
    while (check(TokenType.EQEQ) || check(TokenType.NEQ)) {
      String op = opText(advance().type);
      left = new Expr.Binary(op, left, parseRelational());
    }
    return left;
  }

  private Expr parseRelational() {
    Expr left = parseAdditive();
    while (check(TokenType.LT) || check(TokenType.LE) || check(TokenType.GT) || check(TokenType.GE)) {
      String op = opText(advance().type);
      left = new Expr.Binary(op, left, parseAdditive());
    }
    return left;
  }

  private Expr parseAdditive() {
    Expr left = parseMultiplicative();
    while (check(TokenType.PLUS) || check(TokenType.MINUS)) {
      String op = opText(advance().type);
      left = new Expr.Binary(op, left, parseMultiplicative());
    }
    return left;
  }

  private Expr parseMultiplicative() {
    Expr left = parseUnary();
    while (check(TokenType.STAR) || check(TokenType.SLASH) || check(TokenType.PERCENT)) {
      String op = opText(advance().type);
      left = new Expr.Binary(op, left, parseUnary());
    }
    return left;
  }

  private Expr parseUnary() {
    if (check(TokenType.MINUS) || check(TokenType.NOT)) {
      enterNesting();
      try {
        String op = opText(advance().type);
        return new Expr.Unary(op, parseUnary());
      } finally {
        depth--;
      }
    }
    return parsePrimary();
  }

  /** Throws once live recursion depth passes {@link #MAX_DEPTH}; paired with a {@code depth--} in the caller's {@code finally}. */
  private void enterNesting() {
    if (++depth > MAX_DEPTH) {
      throw new ExprException("expression too deeply nested", peek().position);
    }
  }

  private Expr parsePrimary() {
    Token t = peek();
    switch (t.type) {
      case NUMBER:
        advance();
        return new Expr.Num(t.number);
      case STRING:
        advance();
        return new Expr.Str(t.text);
      case TRUE:
        advance();
        return new Expr.Bool(true);
      case FALSE:
        advance();
        return new Expr.Bool(false);
      case LPAREN: {
        advance();
        Expr inner = parseTernary();
        if (!check(TokenType.RPAREN)) {
          throw new ExprException("unclosed paren", peek().position);
        }
        advance();
        return inner;
      }
      case LBRACKET: {
        advance();
        List<Expr> items = new ArrayList<>();
        if (!check(TokenType.RBRACKET)) {
          items.add(parseTernary());
          while (match(TokenType.COMMA)) {
            items.add(parseTernary());
          }
        }
        if (!check(TokenType.RBRACKET)) {
          throw new ExprException("unclosed bracket", peek().position);
        }
        advance();
        return new Expr.ListLiteral(items);
      }
      case IDENT: {
        advance();
        String name = t.text;
        if (check(TokenType.LPAREN)) {
          if (name.indexOf('.') >= 0) {
            throw new ExprException("call names cannot be dotted", t.position);
          }
          advance();
          List<Expr> args = new ArrayList<>();
          if (!check(TokenType.RPAREN)) {
            args.add(parseTernary());
            while (match(TokenType.COMMA)) {
              args.add(parseTernary());
            }
          }
          if (!check(TokenType.RPAREN)) {
            throw new ExprException("unclosed paren", peek().position);
          }
          advance();
          return new Expr.Call(name, args);
        }
        return new Expr.Var(name);
      }
      default:
        throw new ExprException("unexpected token", t.position);
    }
  }

  private Token peek() {
    return tokens.get(pos);
  }

  private Token advance() {
    return tokens.get(pos++);
  }

  private boolean check(TokenType type) {
    return peek().type == type;
  }

  private boolean match(TokenType type) {
    if (check(type)) {
      pos++;
      return true;
    }
    return false;
  }

  private void expect(TokenType type, String message) {
    if (!check(type)) {
      throw new ExprException(message, peek().position);
    }
    pos++;
  }

  private static String opText(TokenType type) {
    switch (type) {
      case PLUS:
        return "+";
      case MINUS:
        return "-";
      case STAR:
        return "*";
      case SLASH:
        return "/";
      case PERCENT:
        return "%";
      case EQEQ:
        return "==";
      case NEQ:
        return "!=";
      case LT:
        return "<";
      case LE:
        return "<=";
      case GT:
        return ">";
      case GE:
        return ">=";
      case AND:
        return "&&";
      case OR:
        return "||";
      case NOT:
        return "!";
      default:
        throw new IllegalStateException("no operator text for " + type);
    }
  }

  // ---------------------------------------------------------------------
  // Lexer
  // ---------------------------------------------------------------------

  private enum TokenType {
    NUMBER, STRING, IDENT, TRUE, FALSE,
    LPAREN, RPAREN, LBRACKET, RBRACKET, COMMA, QUESTION, COLON,
    PLUS, MINUS, STAR, SLASH, PERCENT,
    EQEQ, NEQ, LT, LE, GT, GE, AND, OR, NOT,
    EOF
  }

  private record Token(TokenType type, String text, double number, int position) {
  }

  private static List<Token> tokenize(String source) {
    List<Token> tokens = new ArrayList<>();
    int n = source.length();
    int i = 0;
    while (i < n) {
      char c = source.charAt(i);
      if (Character.isWhitespace(c)) {
        i++;
        continue;
      }
      int start = i;
      if (c == '#') {
        i++;
        int hexStart = i;
        while (i < n && isHexDigit(source.charAt(i))) {
          i++;
        }
        String hex = source.substring(hexStart, i);
        if (hex.length() != 3 && hex.length() != 6 && hex.length() != 8) {
          throw new ExprException("bad hex length", start);
        }
        tokens.add(new Token(TokenType.NUMBER, null, (double) expandColor(hex), start));
        continue;
      }
      if (Character.isDigit(c)) {
        i++;
        while (i < n && Character.isDigit(source.charAt(i))) {
          i++;
        }
        if (i < n && source.charAt(i) == '.' && i + 1 < n && Character.isDigit(source.charAt(i + 1))) {
          i++;
          while (i < n && Character.isDigit(source.charAt(i))) {
            i++;
          }
        }
        tokens.add(new Token(TokenType.NUMBER, null, Double.parseDouble(source.substring(start, i)), start));
        continue;
      }
      if (c == '\'' || c == '"') {
        char quote = c;
        i++;
        StringBuilder sb = new StringBuilder();
        boolean closed = false;
        while (i < n) {
          char cc = source.charAt(i);
          if (cc == quote) {
            i++;
            closed = true;
            break;
          }
          if (cc == '\\' && i + 1 < n) {
            char esc = source.charAt(i + 1);
            switch (esc) {
              case '\\':
                sb.append('\\');
                break;
              case '\'':
                sb.append('\'');
                break;
              case '"':
                sb.append('"');
                break;
              case 'n':
                sb.append('\n');
                break;
              default:
                throw new ExprException("unrecognized escape sequence", i);
            }
            i += 2;
          } else {
            sb.append(cc);
            i++;
          }
        }
        if (!closed) {
          throw new ExprException("unterminated string", start);
        }
        tokens.add(new Token(TokenType.STRING, sb.toString(), 0, start));
        continue;
      }
      if (Character.isLetter(c) || c == '_') {
        i = scanIdentifierEnd(source, i, n);
        String text = source.substring(start, i);
        TokenType type;
        if (text.equals("true")) {
          type = TokenType.TRUE;
        } else if (text.equals("false")) {
          type = TokenType.FALSE;
        } else {
          type = TokenType.IDENT;
        }
        tokens.add(new Token(type, text, 0, start));
        continue;
      }
      switch (c) {
        case '(':
          tokens.add(new Token(TokenType.LPAREN, null, 0, start));
          i++;
          continue;
        case ')':
          tokens.add(new Token(TokenType.RPAREN, null, 0, start));
          i++;
          continue;
        case '[':
          tokens.add(new Token(TokenType.LBRACKET, null, 0, start));
          i++;
          continue;
        case ']':
          tokens.add(new Token(TokenType.RBRACKET, null, 0, start));
          i++;
          continue;
        case ',':
          tokens.add(new Token(TokenType.COMMA, null, 0, start));
          i++;
          continue;
        case '?':
          tokens.add(new Token(TokenType.QUESTION, null, 0, start));
          i++;
          continue;
        case ':':
          tokens.add(new Token(TokenType.COLON, null, 0, start));
          i++;
          continue;
        case '+':
          tokens.add(new Token(TokenType.PLUS, null, 0, start));
          i++;
          continue;
        case '-':
          tokens.add(new Token(TokenType.MINUS, null, 0, start));
          i++;
          continue;
        case '*':
          tokens.add(new Token(TokenType.STAR, null, 0, start));
          i++;
          continue;
        case '/':
          tokens.add(new Token(TokenType.SLASH, null, 0, start));
          i++;
          continue;
        case '%':
          tokens.add(new Token(TokenType.PERCENT, null, 0, start));
          i++;
          continue;
        case '!':
          if (i + 1 < n && source.charAt(i + 1) == '=') {
            tokens.add(new Token(TokenType.NEQ, null, 0, start));
            i += 2;
          } else {
            tokens.add(new Token(TokenType.NOT, null, 0, start));
            i++;
          }
          continue;
        case '=':
          if (i + 1 < n && source.charAt(i + 1) == '=') {
            tokens.add(new Token(TokenType.EQEQ, null, 0, start));
            i += 2;
            continue;
          }
          throw new ExprException("unexpected token", start);
        case '<':
          if (i + 1 < n && source.charAt(i + 1) == '=') {
            tokens.add(new Token(TokenType.LE, null, 0, start));
            i += 2;
          } else {
            tokens.add(new Token(TokenType.LT, null, 0, start));
            i++;
          }
          continue;
        case '>':
          if (i + 1 < n && source.charAt(i + 1) == '=') {
            tokens.add(new Token(TokenType.GE, null, 0, start));
            i += 2;
          } else {
            tokens.add(new Token(TokenType.GT, null, 0, start));
            i++;
          }
          continue;
        case '&':
          if (i + 1 < n && source.charAt(i + 1) == '&') {
            tokens.add(new Token(TokenType.AND, null, 0, start));
            i += 2;
            continue;
          }
          throw new ExprException("unexpected token", start);
        case '|':
          if (i + 1 < n && source.charAt(i + 1) == '|') {
            tokens.add(new Token(TokenType.OR, null, 0, start));
            i += 2;
            continue;
          }
          throw new ExprException("unexpected token", start);
        default:
          throw new ExprException("unexpected token", start);
      }
    }
    tokens.add(new Token(TokenType.EOF, null, 0, n));
    return tokens;
  }

  private static int scanIdentifierEnd(String source, int i, int n) {
    i++;
    while (i < n && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_')) {
      i++;
    }
    while (i < n && source.charAt(i) == '.' && i + 1 < n
        && (Character.isLetter(source.charAt(i + 1)) || source.charAt(i + 1) == '_')) {
      i++;
      i++;
      while (i < n && (Character.isLetterOrDigit(source.charAt(i)) || source.charAt(i) == '_')) {
        i++;
      }
    }
    return i;
  }

  private static boolean isHexDigit(char c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }

  private static long expandColor(String hex) {
    if (hex.length() == 3) {
      StringBuilder rgb = new StringBuilder();
      for (int i = 0; i < 3; i++) {
        char c = hex.charAt(i);
        rgb.append(c).append(c);
      }
      return Long.parseLong("FF" + rgb, 16);
    }
    if (hex.length() == 6) {
      return Long.parseLong("FF" + hex, 16);
    }
    return Long.parseLong(hex, 16);
  }
}
