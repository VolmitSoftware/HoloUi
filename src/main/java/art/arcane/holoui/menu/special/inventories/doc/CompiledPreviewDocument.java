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
import art.arcane.holoui.expr.ExprScope;
import art.arcane.holoui.menu.special.inventories.PreviewElement;
import art.arcane.holoui.util.common.TextUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Parsed and compile-time-validated form of a preview JSON document: {@link PreviewDocumentParser}
 * builds one of these once per document (re-parsed only on hot reload); {@link #build} then
 * resolves it against one live {@link PreviewStateContext} on every preview refresh.
 *
 * <p><b>Build-time versus live.</b> Everything structural — positions, sizes, z layers, panel and
 * well colours, {@code visible}, repeat counts, the card's accent and title — is evaluated exactly
 * once, while {@link #build} runs. Only two things stay live, because {@link PreviewElement} models
 * them as suppliers the renderer polls every refresh: a cell's colour and a label's text. Those two
 * closures capture the scope they were built under, so a repeat instance keeps the loop variable
 * value it was expanded with rather than re-reading whatever the loop ended on. A cell colour or
 * label text that folded to a constant is resolved at build time anyway and handed back unchanged
 * by every poll, since the renderer polls those suppliers every four ticks.
 *
 * <p><b>Expansion cap.</b> {@link PreviewDocumentParser} can only bound the templates whose repeat
 * counts are constant, so the same {@code MAX_TOTAL_TEMPLATES} bound is enforced again here across
 * the whole build: a repeat that would cross it is truncated, any element after it is skipped, and
 * {@link #build} returns what it managed to expand. Without it a handful of templates with
 * live counts could each expand to the per-repeat maximum and produce millions of elements.
 *
 * <p><b>Failure policy.</b> A preview must never take a frame down. An element whose build-time
 * expressions fail is skipped and the rest of the document still renders; a live closure that fails
 * renders transparent (cells) or empty (labels). Every such failure logs at most once per document
 * per minute, since the alternative is a log line every four ticks for as long as the player looks
 * at the block.
 */
public final class CompiledPreviewDocument {

  /** Colour a cell renders when its live colour expression fails at render time. */
  private static final int TRANSPARENT = 0x00000000;

  /**
   * Accent used when a card declares no {@code accent}: the neutral grey a legacy colour code the
   * theme table did not recognise used to map to. Only the low 24 bits reach the chrome.
   */
  private static final int DEFAULT_ACCENT_COLOR = 0xFFCBD0D9;

  private static final long ERROR_LOG_INTERVAL_MS = 60_000L;

  /** Last error log timestamp per document name; see the failure policy in the class javadoc. */
  private static final Map<String, Long> LAST_ERROR_LOG = new ConcurrentHashMap<>();

  private final String name;
  private final int priority;
  private final CompiledMatch match;
  private final List<CompiledVariant> variants;
  private final Map<String, Object> vars;
  private final CardTemplate card;
  private final List<ElementTemplate> elements;

  CompiledPreviewDocument(
      String name,
      int priority,
      CompiledMatch match,
      List<CompiledVariant> variants,
      Map<String, Object> vars,
      CardTemplate card,
      List<ElementTemplate> elements
  ) {
    this.name = name;
    this.priority = priority;
    this.match = match;
    this.variants = variants;
    this.vars = vars;
    this.card = card;
    this.elements = elements;
  }

  /** The name the document was parsed under (typically its file name); used for logging. */
  public String name() {
    return name;
  }

  /** Document-level match priority; higher wins when more than one document matches a target. */
  public int priority() {
    return priority;
  }

  CompiledMatch match() {
    return match;
  }

  List<CompiledVariant> variants() {
    return variants;
  }

  /** Document-level default {@code vars}; constants only (Double/Boolean/String), never expressions. */
  Map<String, Object> vars() {
    return vars;
  }

  /** Null when the document declares no {@code card} object at all. */
  CardTemplate card() {
    return card;
  }

  List<ElementTemplate> elements() {
    return elements;
  }

  // ---------------------------------------------------------------------
  // Matching
  // ---------------------------------------------------------------------

  /** One resolved preview target: the document that matched and the variables it matched with. */
  public record Resolved(CompiledPreviewDocument doc, Map<String, Object> vars) {
  }

  /**
   * The document's {@code special} marker ({@code enderChest}, {@code locked},
   * {@code anyInventoryHolder}), or null. Interpreting it is the registry's job; matching here is
   * purely by declared block/entity name.
   */
  public String special() {
    return match.special();
  }

  /** True when the document's own match, or any variant's, names this material. */
  public boolean matchesBlock(Material material) {
    if (material == null) {
      return false;
    }
    String name = material.name();
    if (matchesBlockName(match, name)) {
      return true;
    }
    for (CompiledVariant variant : variants) {
      if (matchesBlockName(variant.match(), name)) {
        return true;
      }
    }
    return false;
  }

  /**
   * The document's {@code vars} with the first matching variant's {@code vars} merged over them.
   * Variants are tried in declaration order, so an earlier variant wins an overlap; a material no
   * variant claims simply gets the document defaults.
   */
  public Map<String, Object> varsForBlock(Material material) {
    if (material == null) {
      return vars;
    }
    String name = material.name();
    for (CompiledVariant variant : variants) {
      if (matchesBlockName(variant.match(), name)) {
        return merged(variant.vars());
      }
    }
    return vars;
  }

  /** Counts backing {@code /holoui previews list}: {@link #matchSummary()}. */
  public record MatchSummary(int blocks, int entities, String special, int priority) {
  }

  /**
   * How many block/entity matchers this document declares (its own match plus every variant's,
   * exact names and globs both counted), its {@code special} marker, and its priority. Purely a
   * reporting summary for {@code /holoui previews list}; matching itself still goes through
   * {@link #matchesBlock}/{@link #matchesEntity}.
   */
  public MatchSummary matchSummary() {
    int blocks = matcherCount(match.exactBlocks(), match.blockGlobs());
    int entities = matcherCount(match.exactEntities(), match.entityGlobs());
    for (CompiledVariant variant : variants) {
      blocks += matcherCount(variant.match().exactBlocks(), variant.match().blockGlobs());
      entities += matcherCount(variant.match().exactEntities(), variant.match().entityGlobs());
    }
    return new MatchSummary(blocks, entities, match.special(), priority);
  }

  private static int matcherCount(Set<String> exact, List<Predicate<String>> globs) {
    return exact.size() + globs.size();
  }

  public boolean matchesEntity(Entity entity) {
    if (entity == null || entity.getType() == null) {
      return false;
    }
    String name = entity.getType().name();
    if (matchesEntityName(match, name)) {
      return true;
    }
    for (CompiledVariant variant : variants) {
      if (matchesEntityName(variant.match(), name)) {
        return true;
      }
    }
    return false;
  }

  /** Entity counterpart of {@link #varsForBlock}. */
  public Map<String, Object> varsForEntity(Entity entity) {
    if (entity == null || entity.getType() == null) {
      return vars;
    }
    String name = entity.getType().name();
    for (CompiledVariant variant : variants) {
      if (matchesEntityName(variant.match(), name)) {
        return merged(variant.vars());
      }
    }
    return vars;
  }

  private Map<String, Object> merged(Map<String, Object> overrides) {
    if (overrides.isEmpty()) {
      return vars;
    }
    Map<String, Object> combined = new LinkedHashMap<>(vars);
    combined.putAll(overrides);
    return Map.copyOf(combined);
  }

  private static boolean matchesBlockName(CompiledMatch match, String name) {
    return match.exactBlocks().contains(name) || matchesGlob(match.blockGlobs(), name);
  }

  private static boolean matchesEntityName(CompiledMatch match, String name) {
    return match.exactEntities().contains(name) || matchesGlob(match.entityGlobs(), name);
  }

  private static boolean matchesGlob(List<Predicate<String>> globs, String name) {
    for (Predicate<String> glob : globs) {
      if (glob.test(name)) {
        return true;
      }
    }
    return false;
  }

  // ---------------------------------------------------------------------
  // Build
  // ---------------------------------------------------------------------

  /** No-op sink for {@link #build(PreviewStateContext)}, the hot render path that never collects errors. */
  private static final Consumer<String> NO_OP_SINK = message -> {
  };

  /**
   * Resolves this compiled document against one live preview target, producing the elements
   * {@code ContainerPreview} renders. Never null; empty when the document has no elements or the
   * build fails outright. Equivalent to {@link #build(PreviewStateContext, Consumer)} with a sink
   * that discards everything.
   */
  public List<PreviewElement> build(PreviewStateContext context) {
    return build(context, NO_OP_SINK);
  }

  /**
   * As {@link #build(PreviewStateContext)}, but every failure message this build produces — build-time
   * field errors and, for any live cell colour or label text closure the caller goes on to invoke,
   * render-time errors too — is also handed to {@code errorSink} immediately and unthrottled. This is
   * a per-invocation channel for {@code /holoui previews dump}: {@link #throttledLog} still caps the
   * shared plugin logger at one line per document per minute, but a caller collecting into its own
   * list must see every failure from its own build, including a second dump of the same broken
   * document seconds later. {@code errorSink} must never throw.
   */
  public List<PreviewElement> build(PreviewStateContext context, Consumer<String> errorSink) {
    Consumer<String> sink = errorSink == null ? NO_OP_SINK : errorSink;
    try {
      List<PreviewElement> content = new ArrayList<>();
      // One budget per build call, so the object is confined to this thread's expansion.
      Budget budget = new Budget(PreviewDocumentParser.MAX_TOTAL_TEMPLATES);
      for (ElementTemplate template : elements) {
        if (budget.exhausted()) {
          reportError(sink, "element cap " + PreviewDocumentParser.MAX_TOTAL_TEMPLATES + " reached, remaining elements skipped");
          break;
        }
        try {
          expand(template, context, budget, content, sink);
        } catch (RuntimeException failure) {
          reportError(sink, describe(template) + ": " + failure.getMessage());
        }
      }
      return framed(content, context, sink);
    } catch (RuntimeException failure) {
      reportError(sink, "build failed: " + failure.getMessage());
      return List.of();
    }
  }

  private void expand(ElementTemplate template, PreviewStateContext context, Budget budget, List<PreviewElement> out, Consumer<String> sink) {
    RepeatTemplate repeat = template.repeat();
    if (repeat == null) {
      budget.take();
      emit(template, context, context, out, sink);
      return;
    }
    int count = repeatCount(repeat, context, sink);
    if (count > budget.remaining()) {
      reportError(sink, "repeat of " + count + " truncated at the " + PreviewDocumentParser.MAX_TOTAL_TEMPLATES + " element cap");
      count = budget.remaining();
    }
    for (int index = 0; index < count; index++) {
      budget.take();
      // One scope per instance, holding this instance's loop value: the live cell/label closures
      // built below capture it, so instance 3 still reads 3 after the loop has moved on.
      emit(template, context, new RepeatScope(context, repeat.var(), (double) index), out, sink);
    }
  }

  private int repeatCount(RepeatTemplate repeat, ExprScope scope, Consumer<String> sink) {
    double raw = number(repeat.count(), scope);
    if (!(raw >= 1.0)) {
      return 0;
    }
    long count = (long) Math.floor(raw);
    if (count > PreviewDocumentParser.MAX_REPEAT_COUNT) {
      reportError(sink, "repeat count " + count + " exceeds " + PreviewDocumentParser.MAX_REPEAT_COUNT + ", truncated");
      return PreviewDocumentParser.MAX_REPEAT_COUNT;
    }
    return (int) count;
  }

  private void emit(ElementTemplate template, PreviewStateContext context, ExprScope scope, List<PreviewElement> out, Consumer<String> sink) {
    if (!bool(template.visible(), scope)) {
      return;
    }
    int x = coordinate(template.x(), scope);
    int y = coordinate(template.y(), scope);
    int z = coordinate(template.z(), scope);
    switch (template.type()) {
      case PANEL -> out.add(new PreviewElement.Panel(
          x, y, z,
          coordinate(template.width(), scope),
          coordinate(template.height(), scope),
          color(template.color(), scope)));
      case CELL -> out.add(new PreviewElement.Cell(
          x, y, z,
          coordinate(template.size(), scope),
          cellColor(template.color(), scope, sink)));
      case SLOT -> {
        Inventory inventory = context.inventory();
        if (inventory == null) {
          reportError(sink, "slot: target has no inventory");
          return;
        }
        out.add(new PreviewElement.Slot(
            x, y, z,
            coordinate(template.size(), scope),
            color(template.wellColor(), scope),
            inventory,
            coordinate(template.index(), scope)));
      }
      case LABEL -> out.add(new PreviewElement.Label(
          x, y, z,
          labelText(template.text(), scope, sink),
          color(template.background(), scope)));
    }
  }

  private List<PreviewElement> framed(List<PreviewElement> content, PreviewStateContext context, Consumer<String> sink) {
    if (card == null || !cardFramed(context, sink)) {
      return content;
    }
    // Both evaluated once per build, not per frame: a card's title and accent are chrome, and the
    // golden snapshots record them as fixed values.
    Component title = cardTitle(context, sink);
    int accent = cardAccent(context, sink);
    return CardFramer.frame(content, () -> title, accent, card.minHalfWidth());
  }

  private boolean cardFramed(PreviewStateContext context, Consumer<String> sink) {
    try {
      return bool(card.framed(), context);
    } catch (RuntimeException failure) {
      reportError(sink, "card framed: " + failure.getMessage());
      return false;
    }
  }

  private Component cardTitle(PreviewStateContext context, Consumer<String> sink) {
    if (card.title() == null) {
      return Component.empty();
    }
    try {
      return TextUtils.parse(string(card.title(), context));
    } catch (RuntimeException failure) {
      reportError(sink, "card title: " + failure.getMessage());
      return Component.empty();
    }
  }

  private int cardAccent(PreviewStateContext context, Consumer<String> sink) {
    if (card.accent() == null) {
      return DEFAULT_ACCENT_COLOR;
    }
    try {
      return color(card.accent(), context);
    } catch (RuntimeException failure) {
      reportError(sink, "card accent: " + failure.getMessage());
      return DEFAULT_ACCENT_COLOR;
    }
  }

  // ---------------------------------------------------------------------
  // Live closures
  // ---------------------------------------------------------------------

  private IntSupplier cellColor(CompiledExpr field, ExprScope scope, Consumer<String> sink) {
    if (field.constant() instanceof Double constant) {
      int folded = narrow(constant);
      return () -> folded;
    }
    Expr expr = field.expr();
    return () -> {
      try {
        return ExprEvaluator.color(expr, scope);
      } catch (RuntimeException failure) {
        reportError(sink, "cell color: " + failure.getMessage());
        return TRANSPARENT;
      }
    };
  }

  /**
   * Text goes through {@link TextUtils#parse} — the same call the retired layouts used to turn a
   * legacy-coded string into a Component — so a document renders byte-identically to the layout it
   * replaces.
   *
   * <p>A folded-constant text is parsed once, here, and the resulting Component is handed back by
   * every poll: {@code parse} runs a colour-code translation, a couple of dozen string scans, and a
   * MiniMessage deserialize, none of which may sit on a supplier the renderer calls every four
   * ticks for a string that cannot change. The retired layouts likewise built their static
   * Components once.
   */
  private Supplier<Component> labelText(CompiledExpr field, ExprScope scope, Consumer<String> sink) {
    if (field.isConstant()) {
      Component folded = renderText(field.expr(), scope, sink);
      return () -> folded;
    }
    Expr expr = field.expr();
    return () -> renderText(expr, scope, sink);
  }

  private Component renderText(Expr expr, ExprScope scope, Consumer<String> sink) {
    try {
      return TextUtils.parse(ExprEvaluator.string(expr, scope));
    } catch (RuntimeException failure) {
      reportError(sink, "label text: " + failure.getMessage());
      return Component.empty();
    }
  }

  // ---------------------------------------------------------------------
  // Field evaluation
  // ---------------------------------------------------------------------

  /** Positions and sizes round; a folded constant of the wrong type falls through to a typed error. */
  private static int coordinate(CompiledExpr field, ExprScope scope) {
    return (int) Math.round(number(field, scope));
  }

  private static int color(CompiledExpr field, ExprScope scope) {
    if (field.constant() instanceof Double constant) {
      return narrow(constant);
    }
    return ExprEvaluator.color(field.expr(), scope);
  }

  /**
   * Reinterprets the unsigned ARGB double the expression language carries colours as. A plain
   * {@code (int)} cast on the double saturates at {@code 0x7FFFFFFF}, which silently ruins every
   * colour from alpha {@code 0x80} up; going through {@code long} truncates the bits instead, which
   * is what {@link ExprEvaluator#color} does.
   */
  private static int narrow(double value) {
    return (int) (long) value;
  }

  private static double number(CompiledExpr field, ExprScope scope) {
    return field.constant() instanceof Double constant ? constant : ExprEvaluator.number(field.expr(), scope);
  }

  private static boolean bool(CompiledExpr field, ExprScope scope) {
    return field.constant() instanceof Boolean constant ? constant : ExprEvaluator.bool(field.expr(), scope);
  }

  private static String string(CompiledExpr field, ExprScope scope) {
    return field.constant() instanceof String constant ? constant : ExprEvaluator.string(field.expr(), scope);
  }

  // ---------------------------------------------------------------------
  // Logging
  // ---------------------------------------------------------------------

  private static String describe(ElementTemplate template) {
    return template.type().name().toLowerCase(Locale.ROOT);
  }

  /**
   * One line per document per minute. The read-then-write race between two region threads is
   * benign: the worst case is a second line in the same minute.
   */
  private void throttledLog(String message) {
    long now = System.currentTimeMillis();
    Long last = LAST_ERROR_LOG.get(name);
    if (last != null && now - last < ERROR_LOG_INTERVAL_MS) {
      return;
    }
    LAST_ERROR_LOG.put(name, now);
    HoloUI.log(Level.WARNING, "%s %s", name, message);
  }

  /**
   * Every failure path reports here: the shared logger (throttled, one line per document per
   * minute) and the caller's own sink (unthrottled — a per-invocation channel, so a second build of
   * the same broken document a moment later, e.g. a second {@code /holoui previews dump}, still sees
   * its own errors even though the logger would have suppressed a second log line).
   */
  private void reportError(Consumer<String> sink, String message) {
    throttledLog(message);
    sink.accept(message);
  }

  // ---------------------------------------------------------------------
  // Compiled data types
  // ---------------------------------------------------------------------

  /**
   * How many more elements one {@link #build} call may expand. Counts attempts rather than emitted
   * elements, so a document whose elements are all invisible still costs a bounded amount of work.
   */
  private static final class Budget {

    private int remaining;

    private Budget(int remaining) {
      this.remaining = remaining;
    }

    private boolean exhausted() {
      return remaining <= 0;
    }

    private int remaining() {
      return remaining;
    }

    private void take() {
      remaining--;
    }
  }

  /** Resolves one repeat variable, delegating everything else to the document's live scope. */
  private record RepeatScope(ExprScope parent, String name, Object value) implements ExprScope {

    @Override
    public Object variable(String dottedName) {
      return name.equals(dottedName) ? value : parent.variable(dottedName);
    }

    @Override
    public Object call(String function, List<Object> args) {
      return parent.call(function, args);
    }
  }

  enum ElementType {
    PANEL, CELL, SLOT, LABEL
  }

  /**
   * One field's compiled expression, plus its folded value when {@link Expr} is constant.
   * {@code constant} is non-null exactly when {@code expr} needed no live scope to resolve
   * ({@link art.arcane.holoui.expr.ExprEvaluator#isConstant}); {@link #build} prefers it over
   * re-evaluating {@code expr} on every refresh.
   */
  record CompiledExpr(Expr expr, Object constant) {

    boolean isConstant() {
      return constant != null;
    }
  }

  record RepeatTemplate(CompiledExpr count, String var) {
  }

  /**
   * One compiled element. Fields unused by {@link #type} are null (e.g. {@code width}/{@code height}
   * on a non-panel element); see {@link PreviewDocumentParser} for which fields are required or
   * defaulted per type.
   */
  record ElementTemplate(
      ElementType type,
      CompiledExpr x,
      CompiledExpr y,
      CompiledExpr z,
      CompiledExpr width,
      CompiledExpr height,
      CompiledExpr size,
      CompiledExpr color,
      CompiledExpr wellColor,
      CompiledExpr index,
      CompiledExpr background,
      CompiledExpr text,
      CompiledExpr visible,
      RepeatTemplate repeat
  ) {
  }

  /** {@code title}/{@code accent} are null when the document's {@code card} object omits them. */
  record CardTemplate(CompiledExpr framed, CompiledExpr title, CompiledExpr accent, int minHalfWidth) {
  }

  /**
   * Compiled block/entity matchers for one match block (the document's own, or one variant's).
   * Exact names are kept as {@code Material.name()}/{@code EntityType.name()} strings rather than
   * resolved enum constants so an unknown-today name (future game versions, modded servers) still
   * compiles and can still match once it exists; see {@link PreviewDocumentParser}.
   */
  record CompiledMatch(
      Set<String> exactBlocks,
      List<Predicate<String>> blockGlobs,
      Set<String> exactEntities,
      List<Predicate<String>> entityGlobs,
      String special
  ) {
  }

  record CompiledVariant(CompiledMatch match, Map<String, Object> vars) {
  }
}
