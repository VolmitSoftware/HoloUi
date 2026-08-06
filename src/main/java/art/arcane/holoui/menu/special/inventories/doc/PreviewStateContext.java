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
import art.arcane.holoui.api.PreviewStateProvider;
import art.arcane.holoui.api.PreviewStateProviders;
import art.arcane.holoui.expr.ExprException;
import art.arcane.holoui.expr.ExprFunctions;
import art.arcane.holoui.expr.ExprScope;
import art.arcane.holoui.localization.HoloLocalization;
import art.arcane.holoui.localization.HoloMessages;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.MessageKey;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * {@link ExprScope} over one live preview target: the block/entity/inventory being previewed, the
 * document's own {@code vars}, and every registered {@link PreviewStateProvider} namespace.
 *
 * <p>Variable resolution order:
 * <ol>
 *   <li>{@code vars.<name>} reads the injected variables map. Variables are reachable only under
 *       that prefix, so a document variable can never shadow (or be shadowed by) an adapter name.</li>
 *   <li>everything else reads the cached adapter snapshot, which already contains the provider
 *       namespaces merged under {@code <namespace>.<key>}.</li>
 * </ol>
 * Unknown names return null; {@code ExprEvaluator} turns that into an error naming the variable.
 *
 * <p>The snapshot is sampled lazily on the first lookup and re-sampled whenever the world game
 * time changes, so one preview refresh reads each Bukkit getter once no matter how many
 * expressions reference it. Contexts without a world (a bare ender-chest inventory, or
 * {@link #statics}) fall back to a wall-clock tick counter.
 *
 * <p><b>Publication contract.</b> A context is constructed on whichever thread opened the preview
 * but sampled from the region thread that owns the target, and Folia can move a region between
 * threads across the four-tick refresh interval. The tick and the map it produced are therefore held
 * together in one immutable {@link Sampled} pair behind a single {@code volatile} reference: one
 * write per sample, one read per lookup. That makes publication atomic — a reader can never pair one
 * sample's tick with another sample's map, which two independent {@code volatile} fields could not
 * guarantee once more than one thread samples (writer A's map could land between writer B's map and
 * B's tick).
 *
 * <p>Concurrent sampling is last-writer-wins, and benignly so: both writers read the same block at
 * the same game tick, so they produce equivalent maps and whichever pair survives is correct. The
 * map is fully populated before the pair is constructed and is never mutated afterwards, so a reader
 * holding an older pair still sees a coherent, self-consistent sample.
 */
public final class PreviewStateContext implements ExprScope {

  private static final String VARS_PREFIX = "vars.";
  private static final String LANG_ARG_PREFIX = "arg";
  private static final long MILLIS_PER_TICK = 50L;

  /** Evaluation errors carry no source position; see ExprEvaluator's class-level note. */
  private static final int NO_POSITION = -1;

  /** Namespaces already warned about, so a rejected provider logs once rather than every refresh. */
  private static final Set<String> WARNED_NAMESPACES = ConcurrentHashMap.newKeySet();

  /**
   * Function names {@link #call} resolves itself before falling back to {@link ExprFunctions}.
   * Exposed for {@code VariableCatalogSyncTest}, which pins the shipped variable catalog's
   * {@code functions} section against this set.
   */
  static final Set<String> CONTEXT_FUNCTIONS = Set.of("lang", "count", "occupied", "item");

  private final Block block;
  private final Entity entity;
  private final Player player;
  private final Inventory inventory;
  private final Map<String, Object> vars;
  private final String category;
  private final TimeFlowTracker flow;
  private final World world;

  /** The one field carrying both halves of a sample; see the publication contract in the javadoc. */
  private volatile Sampled sampled;

  private PreviewStateContext(
      Block block,
      Entity entity,
      Player player,
      Inventory inventory,
      String category,
      Map<String, Object> vars
  ) {
    this.block = block;
    this.entity = entity;
    this.player = player;
    this.inventory = inventory;
    this.category = category;
    this.vars = vars == null ? Map.of() : vars;
    this.flow = PreviewStateAdapters.tracksTimeFlow(category)
        ? new TimeFlowTracker(PreviewStateAdapters.countsDown(category))
        : null;
    this.world = block != null ? block.getWorld() : entity != null ? entity.getWorld() : null;
  }

  public static PreviewStateContext forBlock(Block block, Player player, Map<String, Object> vars) {
    Objects.requireNonNull(block, "block");
    PreviewStateAdapters.Selection selection = PreviewStateAdapters.selectBlock(block, player);
    return new PreviewStateContext(block, null, player, selection.inventory(), selection.category(), vars);
  }

  public static PreviewStateContext forEntity(Entity entity, Player player, Map<String, Object> vars) {
    Objects.requireNonNull(entity, "entity");
    PreviewStateAdapters.Selection selection = PreviewStateAdapters.selectEntity(entity);
    return new PreviewStateContext(null, entity, player, selection.inventory(), selection.category(), vars);
  }

  /** Inventory-only target, e.g. a viewer's ender chest. */
  public static PreviewStateContext forInventory(Inventory inventory, Map<String, Object> vars) {
    Objects.requireNonNull(inventory, "inventory");
    return new PreviewStateContext(null, null, null, inventory, PreviewStateAdapters.CATEGORY_INVENTORY, vars);
  }

  /** Target-less context for locked previews and document validation: only {@code vars} and {@code time}. */
  public static PreviewStateContext statics(Map<String, Object> vars) {
    return new PreviewStateContext(null, null, null, null, PreviewStateAdapters.CATEGORY_STATIC, vars);
  }

  /** The previewed inventory, or null when the target has none; Slot elements require non-null. */
  public Inventory inventory() {
    return inventory;
  }

  /** The adapter category chosen at construction; see {@link PreviewStateAdapters#catalog()}. */
  String category() {
    return category;
  }

  // ---------------------------------------------------------------------
  // ExprScope
  // ---------------------------------------------------------------------

  @Override
  public Object variable(String dottedName) {
    if (dottedName.startsWith(VARS_PREFIX)) {
      return vars.get(dottedName.substring(VARS_PREFIX.length()));
    }
    return snapshot().get(dottedName);
  }

  @Override
  public Object call(String name, List<Object> args) {
    return switch (name) {
      case "lang" -> lang(args);
      case "count" -> count(args);
      case "occupied" -> occupied(args);
      case "item" -> item(args);
      default -> ExprFunctions.call(name, args);
    };
  }

  // ---------------------------------------------------------------------
  // Snapshot
  // ---------------------------------------------------------------------

  /** One tick and the map sampled at it, so the two can only ever be published together. */
  record Sampled(long tick, Map<String, Object> values) {
  }

  private Map<String, Object> snapshot() {
    long tick = currentTick();
    Sampled cached = sampled;
    if (cached != null && cached.tick() == tick) {
      return cached.values();
    }
    Map<String, Object> values = new HashMap<>();
    PreviewStateAdapters.sample(category, block, entity, inventory, flow, tick, values);
    mergeProviders(values);
    sampled = new Sampled(tick, values);
    return values;
  }

  private long currentTick() {
    return world == null ? System.currentTimeMillis() / MILLIS_PER_TICK : world.getGameTime();
  }

  private void mergeProviders(Map<String, Object> out) {
    for (PreviewStateProvider provider : PreviewStateProviders.all()) {
      String namespace = null;
      Map<String, Object> values;
      try {
        namespace = provider.namespace();
        if (namespace == null || namespace.isBlank() || rejectReserved(namespace)) {
          continue;
        }
        values = provider.snapshot(block, entity, player);
      } catch (RuntimeException failure) {
        // A third-party provider must never take a preview down with it.
        warnProviderFailure(namespace, provider, failure);
        continue;
      }
      if (values == null) {
        continue;
      }
      for (Map.Entry<String, Object> entry : values.entrySet()) {
        Object value = coerce(entry.getValue());
        if (entry.getKey() != null && value != null) {
          out.put(namespace + "." + entry.getKey(), value);
        }
      }
    }
  }

  /**
   * Once-per-namespace terse warn, reusing {@link #WARNED_NAMESPACES} so a provider that is both
   * reserved and throwing does not double-log. When {@code provider.namespace()} itself is what
   * threw, there is no namespace to key on yet, so the implementing class name stands in.
   */
  private static void warnProviderFailure(String namespace, PreviewStateProvider provider, RuntimeException failure) {
    String key = namespace != null ? namespace : provider.getClass().getName();
    if (!WARNED_NAMESPACES.add(key)) {
      return;
    }
    String message = failure.getMessage();
    HoloUI.log(Level.WARNING, "Preview provider '%s' threw %s%s; provider ignored.",
        key, failure.getClass().getSimpleName(), message == null || message.isEmpty() ? "" : ": " + message);
  }

  /**
   * True when a provider namespace would shadow a built-in variable, e.g. a provider called
   * {@code inventory} publishing {@code inventory.size}. Such a provider is dropped whole rather
   * than partially merged, and warned about once per namespace so a misconfigured plugin is
   * diagnosable without spamming the log every refresh.
   */
  private static boolean rejectReserved(String namespace) {
    if (!PreviewStateAdapters.isReservedNamespace(namespace)) {
      return false;
    }
    if (WARNED_NAMESPACES.add(namespace)) {
      HoloUI.log(Level.WARNING, "Preview provider namespace '%s' is reserved by a built-in variable; provider ignored.", namespace);
    }
    return true;
  }

  /** Narrows a provider value to the expression runtime's types; anything else is dropped. */
  private static Object coerce(Object value) {
    if (value instanceof Double || value instanceof String || value instanceof Boolean) {
      return value;
    }
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    return null;
  }

  // ---------------------------------------------------------------------
  // Functions
  // ---------------------------------------------------------------------

  /**
   * {@code lang(key, ...)} resolves a message through the same chain the retired layouts used
   * ({@link HoloLocalization#globalText}), so a running plugin renders the active locale and a
   * headless test renders the English default.
   *
   * <p>The key is looked up in {@link HoloMessages#catalog()} first, because only the catalog's own
   * {@link TextKey} carries the real English template and therefore the real placeholder names. An
   * id the catalog does not know falls back to {@code TextKey.of(key, key)}, which renders as
   * itself instead of failing.
   */
  private String lang(List<Object> args) {
    if (args.isEmpty()) {
      throw new ExprException("lang expects at least 1 argument (the message key), got 0", NO_POSITION);
    }
    if (!(args.get(0) instanceof String key)) {
      throw new ExprException("lang argument 1 (key) must be a string", NO_POSITION);
    }
    try {
      TextKey resolved = messageKey(key);
      return HoloLocalization.globalText(resolved, langArguments(resolved, args));
    } catch (IllegalArgumentException invalid) {
      throw new ExprException("lang: " + invalid.getMessage(), NO_POSITION);
    }
  }

  /** The catalog's own key when the id is known, else a key whose default is the id itself. */
  static TextKey messageKey(String key) {
    MessageKey known = HoloMessages.catalog().key(key);
    return known instanceof TextKey text ? text : TextKey.of(key, key);
  }

  /**
   * Binds positional call arguments onto the resolved key's own placeholder names: argument 1 fills
   * the first <code>{name}</code> in the English template, argument 2 the second, and so on. That
   * is what lets a document write {@code lang("holoui.preview.state.smelting_item", item, percent)}
   * and get {@code "Smelting Iron Ore 42%"} out of the template {@code "Smelting {item} {percent}%"}.
   *
   * <p>Arguments past the last placeholder are named {@code arg0}, {@code arg1}, ... by position and
   * simply go unused; VolmLib rejects a placeholder name that does not start with a letter, so a
   * bare {@code "0"} is not a legal name and never was. Values are stringified with the expression
   * language's own rule, so {@code 42.0} inserts as {@code "42"}, and they are inserted as untrusted
   * text so a container name can never smuggle in colour codes.
   */
  static MessageArgs langArguments(TextKey key, List<Object> args) {
    if (args.size() <= 1) {
      return MessageArgs.empty();
    }
    List<String> placeholders = orderedPlaceholders(key.english());
    MessageArgs.Builder builder = MessageArgs.builder();
    for (int index = 1; index < args.size(); index++) {
      int position = index - 1;
      String name = position < placeholders.size() ? placeholders.get(position) : LANG_ARG_PREFIX + position;
      builder.untrusted(name, ExprFunctions.call("str", List.of(args.get(index))));
    }
    return builder.build();
  }

  /**
   * Placeholder names in first-appearance order, honouring the <code>{{</code> escape VolmLib's own
   * scanner uses. {@code TextKey.placeholders()} cannot be used here: it returns a
   * {@code Set.copyOf(...)}, which has already lost the insertion order this binding depends on.
   */
  static List<String> orderedPlaceholders(String template) {
    List<String> names = new ArrayList<>();
    int cursor = 0;
    while (cursor < template.length()) {
      int open = template.indexOf('{', cursor);
      if (open < 0) {
        break;
      }
      if (open + 1 < template.length() && template.charAt(open + 1) == '{') {
        cursor = open + 2;
        continue;
      }
      int close = template.indexOf('}', open + 1);
      if (close < 0) {
        break;
      }
      String name = template.substring(open + 1, close);
      if (!names.contains(name)) {
        names.add(name);
      }
      cursor = close + 1;
    }
    return names;
  }

  private double count(List<Object> args) {
    ItemStack stack = slotItem("count", args);
    return stack == null ? 0.0 : stack.getAmount();
  }

  private boolean occupied(List<Object> args) {
    return slotItem("occupied", args) != null;
  }

  /**
   * {@code item(slot)} is the material id in a slot ({@code "IRON_ORE"}), or the empty string when
   * the slot is empty, out of range, or the target has no inventory. Ids rather than display text,
   * matching {@code blockType}: a document that wants the name a player reads writes
   * {@code readable(item(0))}, which is the pair the retired furnace state line drew.
   */
  private String item(List<Object> args) {
    ItemStack stack = slotItem("item", args);
    return stack == null ? "" : stack.getType().name();
  }

  /** Null for a missing inventory, an out-of-range slot, or an empty stack. */
  private ItemStack slotItem(String name, List<Object> args) {
    if (args.size() != 1) {
      throw new ExprException(name + " expects 1 argument(s), got " + args.size(), NO_POSITION);
    }
    if (!(args.get(0) instanceof Double index)) {
      throw new ExprException(name + " argument 1 must be a number", NO_POSITION);
    }
    if (inventory == null) {
      return null;
    }
    int slot = (int) Math.floor(index);
    if (slot < 0 || slot >= inventory.getSize()) {
      return null;
    }
    ItemStack stack = inventory.getItem(slot);
    return PreviewStateAdapters.empty(stack) ? null : stack;
  }
}
