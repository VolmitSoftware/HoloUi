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

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.expr.Expr;
import art.arcane.holoui.expr.ExprEvaluator;
import art.arcane.holoui.expr.ExprException;
import art.arcane.holoui.expr.ExprParser;
import art.arcane.holoui.expr.ExprScope;
import art.arcane.holoui.menu.special.inventories.doc.CompiledPreviewDocument.CardTemplate;
import art.arcane.holoui.menu.special.inventories.doc.CompiledPreviewDocument.CompiledExpr;
import art.arcane.holoui.menu.special.inventories.doc.CompiledPreviewDocument.CompiledMatch;
import art.arcane.holoui.menu.special.inventories.doc.CompiledPreviewDocument.CompiledVariant;
import art.arcane.holoui.menu.special.inventories.doc.CompiledPreviewDocument.ElementTemplate;
import art.arcane.holoui.menu.special.inventories.doc.CompiledPreviewDocument.ElementType;
import art.arcane.holoui.menu.special.inventories.doc.CompiledPreviewDocument.RepeatTemplate;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Parses one preview JSON document into a {@link CompiledPreviewDocument}: binds the raw text into
 * the {@link PreviewDocument} DTO shape via Gson (JSON syntax/structural errors surface as a
 * {@link JsonParseException} there, which is wrapped below), then manually walks the resulting DTO
 * graph field-by-field to validate, fold, and compile it. Walking the DTO graph ourselves — rather
 * than trusting Gson's own error reporting for semantic checks — is what lets every failure name
 * its exact field path (e.g. {@code "elements[3].color"}); see {@link PreviewDocumentException}.
 */
public final class PreviewDocumentParser {

  private static final Gson GSON = new GsonBuilder().create();

  private static final Set<String> SPECIAL_VALUES = Set.of("enderChest", "locked", "anyInventoryHolder");
  private static final Set<String> ELEMENT_TYPES = Set.of("panel", "cell", "slot", "label");
  private static final Pattern IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
  private static final String VARS_PREFIX = "vars.";
  private static final String VARS_ROOT = "vars";
  private static final String DEFAULT_REPEAT_VAR = "i";

  /** Also the cap {@link CompiledPreviewDocument} truncates a live repeat count to. */
  static final int MAX_REPEAT_COUNT = 1024;
  /**
   * Compile-time bound on expanded elements. Only constant repeat counts are known here, so it is
   * also enforced at build time by {@link CompiledPreviewDocument}, where the dynamic counts a
   * document actually expands to are finally known.
   */
  static final int MAX_TOTAL_TEMPLATES = 4096;

  // Layout constants a document inherits when it omits z / wellColor / minHalfWidth. Frozen: the
  // golden snapshots pin every one of them.
  private static final double DEFAULT_Z_PANEL = 1.0;
  private static final double DEFAULT_Z_WELL = 4.0;
  private static final double DEFAULT_Z_LABEL = 6.0;
  private static final double DEFAULT_WELL_COLOR = (double) 0xFF15151BL;
  private static final int DEFAULT_MIN_HALF_WIDTH = 82;

  /** Never invoked: constant expressions contain no {@link Expr.Var}/{@link Expr.Call} nodes. */
  private static final ExprScope EMPTY_SCOPE = new ExprScope() {
    @Override
    public Object variable(String dottedName) {
      return null;
    }

    @Override
    public Object call(String name, List<Object> args) {
      return null;
    }
  };

  private final String documentName;
  private final Set<String> flatCatalog;
  private final Set<String> declaredVarNames = new LinkedHashSet<>();

  private PreviewDocumentParser(String documentName) {
    this.documentName = documentName;
    Set<String> flat = new LinkedHashSet<>();
    for (Set<String> names : PreviewStateAdapters.catalog().values()) {
      flat.addAll(names);
    }
    this.flatCatalog = flat;
  }

  public static CompiledPreviewDocument parse(String name, String json) {
    return new PreviewDocumentParser(name).compile(json);
  }

  // ---------------------------------------------------------------------
  // Top level
  // ---------------------------------------------------------------------

  private CompiledPreviewDocument compile(String json) {
    PreviewDocument doc;
    try {
      doc = GSON.fromJson(json, PreviewDocument.class);
    } catch (JsonParseException e) {
      throw new PreviewDocumentException(documentName, "malformed JSON: " + e.getMessage(), e);
    }
    if (doc == null) {
      throw new PreviewDocumentException(documentName, "empty document", null);
    }

    CompiledMatch match = compileMatch(doc.match, "match");
    Map<String, Object> vars = doc.match == null ? Map.of() : compileVars(doc.match.vars, "match.vars");
    declaredVarNames.addAll(vars.keySet());

    List<CompiledVariant> variants = compileVariants(doc.variants);
    for (CompiledVariant variant : variants) {
      declaredVarNames.addAll(variant.vars().keySet());
    }

    CardTemplate card = compileCard(doc.card);
    List<ElementTemplate> elements = compileElements(doc.elements);
    checkTotalTemplateCount(elements);

    int priority = doc.match != null && doc.match.priority != null ? doc.match.priority : 0;
    return new CompiledPreviewDocument(documentName, priority, match, variants, vars, card, elements);
  }

  // ---------------------------------------------------------------------
  // Match / variants
  // ---------------------------------------------------------------------

  private List<CompiledVariant> compileVariants(List<VariantDef> variants) {
    if (variants == null) {
      return List.of();
    }
    List<CompiledVariant> compiled = new ArrayList<>(variants.size());
    for (int i = 0; i < variants.size(); i++) {
      VariantDef variant = variants.get(i);
      String path = "variants[" + i + "]";
      if (variant == null) {
        throw fail(path, "must be an object, got null", null);
      }
      CompiledMatch match = compileMatch(variant, path);
      Map<String, Object> vars = compileVars(variant.vars, path + ".vars");
      compiled.add(new CompiledVariant(match, vars));
    }
    return List.copyOf(compiled);
  }

  private CompiledMatch compileMatch(MatchDef match, String path) {
    if (match == null) {
      return new CompiledMatch(Set.of(), List.of(), Set.of(), List.of(), null);
    }
    Names blocks = compileBlockNames(match.blocks, path + ".blocks");
    Names entities = compileEntityNames(match.entities, path + ".entities");
    String special = compileSpecial(match.special, path + ".special");
    return new CompiledMatch(blocks.exact, blocks.globs, entities.exact, entities.globs, special);
  }

  private String compileSpecial(String special, String path) {
    if (special == null) {
      return null;
    }
    if (!SPECIAL_VALUES.contains(special)) {
      throw fail(path, "must be one of " + String.join(", ", SPECIAL_VALUES) + ", got '" + special + "'", null);
    }
    return special;
  }

  /** Splits a list of block/entity match names into exact (uppercased) names and glob predicates. */
  private record Names(Set<String> exact, List<Predicate<String>> globs) {
  }

  private Names compileBlockNames(List<String> raw, String path) {
    return compileNames(raw, path, name -> Material.getMaterial(name) != null, "block material");
  }

  private Names compileEntityNames(List<String> raw, String path) {
    return compileNames(raw, path, PreviewDocumentParser::isKnownEntityType, "entity type");
  }

  private static boolean isKnownEntityType(String name) {
    try {
      EntityType.valueOf(name);
      return true;
    } catch (IllegalArgumentException notFound) {
      return false;
    }
  }

  private Names compileNames(List<String> raw, String path, Predicate<String> known, String kind) {
    if (raw == null) {
      return new Names(Set.of(), List.of());
    }
    Set<String> exact = new LinkedHashSet<>();
    List<Predicate<String>> globs = new ArrayList<>();
    for (int i = 0; i < raw.size(); i++) {
      String rawEntry = raw.get(i);
      if (rawEntry == null) {
        throw fail(path + "[" + i + "]", "must be a string, got null", null);
      }
      String entry = rawEntry.toUpperCase(Locale.ROOT);
      if (entry.indexOf('*') >= 0) {
        globs.add(compileGlob(entry));
        continue;
      }
      if (!known.test(entry)) {
        HoloUI.log(Level.WARNING, "%s: unknown %s '%s' at %s[%d], still compiling", documentName, kind, entry, path, i);
      }
      exact.add(entry);
    }
    return new Names(Set.copyOf(exact), List.copyOf(globs));
  }

  /** '*' is the only wildcard; segments between them are matched literally via {@link Pattern#quote}. */
  private static Predicate<String> compileGlob(String glob) {
    String[] parts = glob.split("\\*", -1);
    StringBuilder regex = new StringBuilder("^");
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) {
        regex.append(".*");
      }
      regex.append(Pattern.quote(parts[i]));
    }
    regex.append("$");
    return Pattern.compile(regex.toString()).asMatchPredicate();
  }

  // ---------------------------------------------------------------------
  // Vars maps (constants only — never expressions)
  // ---------------------------------------------------------------------

  private Map<String, Object> compileVars(Map<String, JsonElement> raw, String path) {
    if (raw == null) {
      return Map.of();
    }
    Map<String, Object> result = new LinkedHashMap<>();
    for (Map.Entry<String, JsonElement> entry : raw.entrySet()) {
      String fieldPath = path + "." + entry.getKey();
      JsonElement value = entry.getValue();
      if (value == null || !value.isJsonPrimitive()) {
        throw fail(fieldPath, "must be a number, boolean, or string constant", null);
      }
      // Contract pin: vars are JSON primitives converted to Double/Boolean/String ONLY. A string
      // value here is a plain string, never parsed as an expression (unlike every other field
      // below): "vars.<name>" always resolves to this exact literal at render time. The single
      // exception is a colour literal, which JSON cannot express as a number without losing the
      // alpha byte to a signed int; see varString.
      JsonPrimitive primitive = value.getAsJsonPrimitive();
      if (primitive.isBoolean()) {
        result.put(entry.getKey(), primitive.getAsBoolean());
      } else if (primitive.isNumber()) {
        result.put(entry.getKey(), primitive.getAsDouble());
      } else {
        result.put(entry.getKey(), varString(primitive.getAsString(), fieldPath));
      }
    }
    return Map.copyOf(result);
  }

  /**
   * A leading {@code '#'} selects the expression language's own colour-literal grammar
   * ({@code #RGB}, {@code #RRGGBB}, {@code #AARRGGBB}), so a variant can carry a per-material
   * accent as {@code "accent": "#FFB02E26"} and have it arrive as the same unsigned ARGB number an
   * inline {@code #FFB02E26} would compile to. Every other string is left exactly as written — a
   * MiniMessage tag like {@code "<#F2A535>"} is text, not a colour, because it does not lead with
   * the hash. A leading hash that is not a valid literal is a typo, not a string: it fails here
   * rather than silently rendering the raw text somewhere.
   */
  private Object varString(String value, String path) {
    if (value.isEmpty() || value.charAt(0) != '#') {
      return value;
    }
    Expr expr;
    try {
      expr = ExprParser.parse(value);
    } catch (ExprException e) {
      throw fail(path, "invalid color literal '" + value + "': " + e.getMessage(), e);
    }
    if (expr instanceof Expr.Num literal) {
      return literal.value();
    }
    throw fail(path, "invalid color literal '" + value + "'", null);
  }

  // ---------------------------------------------------------------------
  // Card
  // ---------------------------------------------------------------------

  private CardTemplate compileCard(CardDef card) {
    if (card == null) {
      return null;
    }
    // Declaring a card at all means asking for the chrome: a document that only wants bare content
    // omits the card object entirely, so the default here has to be true.
    CompiledExpr framed = compileBoolField(card.framed, "card.framed", true, Set.of());
    CompiledExpr title = card.title == null ? null : compileExpr(card.title, "card.title", Set.of());
    CompiledExpr accent = card.accent == null ? null : compileExpr(card.accent, "card.accent", Set.of());
    int minHalfWidth = card.minHalfWidth != null ? card.minHalfWidth : DEFAULT_MIN_HALF_WIDTH;
    return new CardTemplate(framed, title, accent, minHalfWidth);
  }

  // ---------------------------------------------------------------------
  // Elements
  // ---------------------------------------------------------------------

  private List<ElementTemplate> compileElements(List<ElementDef> defs) {
    if (defs == null) {
      return List.of();
    }
    List<ElementTemplate> elements = new ArrayList<>(defs.size());
    for (int i = 0; i < defs.size(); i++) {
      ElementDef def = defs.get(i);
      String path = "elements[" + i + "]";
      if (def == null) {
        throw fail(path, "must be an object, got null", null);
      }
      elements.add(compileElement(def, path));
    }
    return List.copyOf(elements);
  }

  private ElementTemplate compileElement(ElementDef def, String path) {
    ElementType type = compileElementType(def.type, path + ".type");

    RepeatTemplate repeat = null;
    Set<String> scope = Set.of();
    if (def.repeat != null) {
      repeat = compileRepeat(def.repeat, path + ".repeat");
      scope = Set.of(repeat.var());
    }

    CompiledExpr x = compileNumericField(def.x, path + ".x", 0.0, scope);
    CompiledExpr y = compileNumericField(def.y, path + ".y", 0.0, scope);
    CompiledExpr z = compileNumericField(def.z, path + ".z", defaultZ(type), scope);

    CompiledExpr width = null;
    CompiledExpr height = null;
    CompiledExpr size = null;
    CompiledExpr color = null;
    CompiledExpr wellColor = null;
    CompiledExpr index = null;
    CompiledExpr background = null;
    CompiledExpr text = null;

    switch (type) {
      case PANEL -> {
        width = requireNumericField(def.width, path + ".width", type, scope);
        height = requireNumericField(def.height, path + ".height", type, scope);
        color = requireNumericField(def.color, path + ".color", type, scope);
      }
      case CELL -> {
        size = requireNumericField(def.size, path + ".size", type, scope);
        color = requireNumericField(def.color, path + ".color", type, scope);
      }
      case SLOT -> {
        size = requireNumericField(def.size, path + ".size", type, scope);
        index = requireNumericField(def.index, path + ".index", type, scope);
        wellColor = compileNumericField(def.wellColor, path + ".wellColor", DEFAULT_WELL_COLOR, scope);
      }
      case LABEL -> {
        if (def.text == null) {
          throw fail(path + ".text", "required for type label", null);
        }
        text = compileExpr(def.text, path + ".text", scope);
        background = compileNumericField(def.background, path + ".background", 0.0, scope);
      }
    }

    CompiledExpr visible = compileBoolField(def.visible, path + ".visible", true, scope);
    return new ElementTemplate(type, x, y, z, width, height, size, color, wellColor, index, background, text, visible, repeat);
  }

  private ElementType compileElementType(String type, String path) {
    if (type == null || !ELEMENT_TYPES.contains(type)) {
      throw fail(path, "must be one of " + String.join(", ", ELEMENT_TYPES) + ", got "
          + (type == null ? "nothing" : "'" + type + "'"), null);
    }
    return ElementType.valueOf(type.toUpperCase(Locale.ROOT));
  }

  private static double defaultZ(ElementType type) {
    return switch (type) {
      case PANEL -> DEFAULT_Z_PANEL;
      case CELL, SLOT -> DEFAULT_Z_WELL;
      case LABEL -> DEFAULT_Z_LABEL;
    };
  }

  private RepeatTemplate compileRepeat(RepeatDef repeat, String path) {
    String var = repeat.var == null || repeat.var.isEmpty() ? DEFAULT_REPEAT_VAR : repeat.var;
    if (!IDENTIFIER.matcher(var).matches()) {
      throw fail(path + ".var", "must be a valid identifier, got '" + var + "'", null);
    }
    // checkVariableName resolves flatCatalog names before scope, so a repeat var sharing a catalog
    // name (or the "vars" root) would never actually be reachable inside the element's own fields —
    // every reference to it would silently resolve to the adapter/vars namespace instead of the
    // loop variable.
    if (var.equals(VARS_ROOT) || flatCatalog.contains(var)) {
      throw fail(path + ".var", "'" + var + "' collides with a reserved variable name and would be unreachable", null);
    }
    if (repeat.count == null || repeat.count.isJsonNull()) {
      throw fail(path + ".count", "required", null);
    }
    CompiledExpr count = compileNumericField(repeat.count, path + ".count", 0.0, Set.of());
    if (count.constant() instanceof Double constant && constant > MAX_REPEAT_COUNT) {
      throw fail(path + ".count", "constant repeat count " + formatNumber(constant) + " exceeds " + MAX_REPEAT_COUNT, null);
    }
    return new RepeatTemplate(count, var);
  }

  private void checkTotalTemplateCount(List<ElementTemplate> elements) {
    long total = 0;
    for (ElementTemplate element : elements) {
      RepeatTemplate repeat = element.repeat();
      if (repeat != null && repeat.count().constant() instanceof Double constant) {
        total += Math.max(0L, (long) Math.floor(constant));
      } else {
        total += 1;
      }
    }
    if (total > MAX_TOTAL_TEMPLATES) {
      throw fail("elements", "total compiled template count " + total + " exceeds " + MAX_TOTAL_TEMPLATES, null);
    }
  }

  // ---------------------------------------------------------------------
  // Field compilation: number-or-expression, bool-or-expression, plain expression
  // ---------------------------------------------------------------------

  private CompiledExpr requireNumericField(JsonElement raw, String path, ElementType type, Set<String> scope) {
    // Gson binds an explicit JSON `null` to JsonNull.INSTANCE (a non-null JsonElement), not a bare
    // Java null, so a required check must reject both an absent key and an explicit null value —
    // otherwise `"width": null` would silently fall through to compileNumericField's own null
    // handling and compile as the (wrong, for a required field) default of 0.0.
    if (raw == null || raw.isJsonNull()) {
      throw fail(path, "required for type " + type.name().toLowerCase(Locale.ROOT), null);
    }
    return compileNumericField(raw, path, 0.0, scope);
  }

  private CompiledExpr compileNumericField(JsonElement raw, String path, double defaultValue, Set<String> scope) {
    if (raw == null || raw.isJsonNull()) {
      return fold(new Expr.Num(defaultValue), path);
    }
    if (!raw.isJsonPrimitive()) {
      throw fail(path, "must be a number or a string expression", null);
    }
    JsonPrimitive primitive = raw.getAsJsonPrimitive();
    Expr expr;
    if (primitive.isNumber()) {
      expr = new Expr.Num(primitive.getAsDouble());
    } else if (primitive.isString()) {
      expr = parseExprSource(primitive.getAsString(), path);
    } else {
      throw fail(path, "must be a number or a string expression, got a boolean", null);
    }
    validateVariables(expr, path, scope);
    return fold(expr, path);
  }

  private CompiledExpr compileBoolField(JsonElement raw, String path, boolean defaultValue, Set<String> scope) {
    if (raw == null || raw.isJsonNull()) {
      return fold(new Expr.Bool(defaultValue), path);
    }
    if (!raw.isJsonPrimitive()) {
      throw fail(path, "must be a boolean or a string expression", null);
    }
    JsonPrimitive primitive = raw.getAsJsonPrimitive();
    Expr expr;
    if (primitive.isBoolean()) {
      expr = new Expr.Bool(primitive.getAsBoolean());
    } else if (primitive.isString()) {
      expr = parseExprSource(primitive.getAsString(), path);
    } else {
      throw fail(path, "must be a boolean or a string expression, got a number", null);
    }
    validateVariables(expr, path, scope);
    return fold(expr, path);
  }

  /** For fields that are always an expression source string (label {@code text}, card title/accent). */
  private CompiledExpr compileExpr(String source, String path, Set<String> scope) {
    Expr expr = parseExprSource(source, path);
    validateVariables(expr, path, scope);
    return fold(expr, path);
  }

  private Expr parseExprSource(String source, String path) {
    try {
      return ExprParser.parse(source);
    } catch (ExprException e) {
      throw fail(path, e.getMessage() + " at " + e.position(), e);
    }
  }

  private CompiledExpr fold(Expr expr, String path) {
    if (!ExprEvaluator.isConstant(expr)) {
      return new CompiledExpr(expr, null);
    }
    try {
      Object value = ExprEvaluator.eval(expr, EMPTY_SCOPE);
      return new CompiledExpr(expr, value);
    } catch (ExprException e) {
      throw fail(path, e.getMessage(), e);
    }
  }

  // ---------------------------------------------------------------------
  // Variable validation
  // ---------------------------------------------------------------------

  private void validateVariables(Expr expr, String path, Set<String> scope) {
    switch (expr) {
      case Expr.Num n -> {
      }
      case Expr.Str s -> {
      }
      case Expr.Bool b -> {
      }
      case Expr.ListLiteral l -> {
        for (Expr item : l.items()) {
          validateVariables(item, path, scope);
        }
      }
      case Expr.Var v -> checkVariableName(v.name(), path, scope);
      case Expr.Unary u -> validateVariables(u.operand(), path, scope);
      case Expr.Binary b -> {
        validateVariables(b.left(), path, scope);
        validateVariables(b.right(), path, scope);
      }
      case Expr.Ternary t -> {
        validateVariables(t.condition(), path, scope);
        validateVariables(t.ifTrue(), path, scope);
        validateVariables(t.ifFalse(), path, scope);
      }
      case Expr.Call c -> {
        for (Expr arg : c.args()) {
          validateVariables(arg, path, scope);
        }
      }
    }
  }

  private void checkVariableName(String name, String path, Set<String> scope) {
    if (flatCatalog.contains(name)) {
      return;
    }
    if (name.startsWith(VARS_PREFIX)) {
      String declared = name.substring(VARS_PREFIX.length());
      if (declaredVarNames.contains(declared)) {
        return;
      }
      throw fail(path, "unknown variable: " + name, null);
    }
    int dot = name.indexOf('.');
    if (dot < 0) {
      if (scope.contains(name)) {
        return;
      }
      throw fail(path, "unknown variable: " + name, null);
    }
    // A dotted name whose prefix is a reserved namespace (i.e. it is, or would collide with, a
    // built-in adapter variable — see PreviewStateAdapters.isReservedNamespace, the same check
    // PreviewStateContext uses against live provider namespaces) can never be provider-supplied,
    // so an unresolved name under it is a typo, not a future provider. A non-reserved prefix might
    // still be filled in by a provider registered later, so it only warrants a warning.
    String prefix = name.substring(0, dot);
    if (PreviewStateAdapters.isReservedNamespace(prefix)) {
      throw fail(path, "unknown variable: " + name, null);
    }
    HoloUI.log(Level.WARNING, "%s: %s references provider namespace '%s', not verifiable at parse time",
        documentName, path, name);
  }

  // ---------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------

  private PreviewDocumentException fail(String path, String detail, Throwable cause) {
    return new PreviewDocumentException(documentName, path + ": " + detail, cause);
  }

  private static String formatNumber(double value) {
    if (Double.isFinite(value) && value == Math.rint(value)) {
      return Long.toString((long) value);
    }
    return Double.toString(value);
  }
}
