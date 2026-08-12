package art.arcane.holoui.importer;

import art.arcane.holoui.board.BoardDefinition;
import art.arcane.holoui.board.BoardIds;
import art.arcane.holoui.board.BoardTransform;
import art.arcane.holoui.board.BoardVisibility;
import art.arcane.holoui.config.components.DecoComponentData;
import art.arcane.holoui.config.icon.BlockIconData;
import art.arcane.holoui.config.icon.EntityIconData;
import art.arcane.holoui.config.icon.IconArgbColor;
import art.arcane.holoui.config.icon.ItemIconData;
import art.arcane.holoui.config.icon.MenuIconData;
import art.arcane.holoui.config.menu.MenuDocument;
import art.arcane.holoui.config.menu.MenuDocumentParser;
import art.arcane.holoui.config.menu.MenuIds;
import art.arcane.volmlib.util.bukkit.json.BukkitJson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class LegacyHologramConverter {
  private static final double DEFAULT_VIEW_RANGE = 120.0D;
  private static final double DEFAULT_INTERACTION_RANGE = 8.0D;
  private static final double TEXT_LINE_HEIGHT = 3.5D / 16.0D;
  private static final double BLOCK_ICON_VERTICAL_OFFSET = -0.05D;
  private static final double BLOCK_ICON_SCALE_COMPENSATION = 4.0D / 3.0D;
  private static final double ITEM_ICON_OFFSET = -1.09D;
  private static final double BLOCK_ITEM_VERTICAL_OFFSET = -0.95D;
  private static final double BLOCK_ITEM_DEPTH_OFFSET = 0.3D;
  private static final int MAX_CULLING_DIMENSION = 4096;
  private static final int MAX_WARNING_ROW_LENGTH = 160;
  private static final Set<String> ITEM_BLOCK_LAYOUT_EXCLUSIONS = Set.of(
      "BARRIER", "LIGHT", "HOPPER", "TURTLE_EGG", "GRASS", "SHORT_GRASS", "TALL_GRASS",
      "WHITE_STAINED_GLASS_PANE", "ORANGE_STAINED_GLASS_PANE", "MAGENTA_STAINED_GLASS_PANE",
      "LIGHT_BLUE_STAINED_GLASS_PANE", "YELLOW_STAINED_GLASS_PANE", "LIME_STAINED_GLASS_PANE",
      "PINK_STAINED_GLASS_PANE", "GRAY_STAINED_GLASS_PANE", "LIGHT_GRAY_STAINED_GLASS_PANE",
      "CYAN_STAINED_GLASS_PANE", "PURPLE_STAINED_GLASS_PANE", "BLUE_STAINED_GLASS_PANE",
      "BROWN_STAINED_GLASS_PANE", "GREEN_STAINED_GLASS_PANE", "RED_STAINED_GLASS_PANE",
      "BLACK_STAINED_GLASS_PANE", "GLASS_PANE", "POPPY", "DANDELION"
  );

  ConversionResult convert(LegacyImportSource source, List<LegacyHologramDraft> drafts,
                           LegacyWorldCatalog worlds) {
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(drafts, "drafts");
    Objects.requireNonNull(worlds, "worlds");
    List<LegacyImportCandidate> candidates = new ArrayList<>();
    List<LegacyImportIssue> issues = new ArrayList<>();
    Map<String, String> outputOwners = new LinkedHashMap<>();

    for (LegacyHologramDraft draft : drafts) {
      try {
        LegacyImportCandidate candidate = convertOne(source, draft, worlds);
        String owner = outputOwners.putIfAbsent(candidate.boardId(), draft.sourceIdentity());
        if (owner != null) {
          issues.add(error(draft.legacyId(), "canonical id collides with " + owner
              + " as " + candidate.boardId()));
          candidates.add(candidate.withDisposition(LegacyImportDisposition.CONFLICT,
              "canonical id collides inside source"));
          markConflict(candidates, candidate.boardId(), owner);
          continue;
        }
        candidates.add(candidate);
      } catch (RuntimeException failure) {
        issues.add(error(draft.legacyId(), safeMessage(failure)));
      }
    }
    return new ConversionResult(candidates, issues);
  }

  private LegacyImportCandidate convertOne(LegacyImportSource source, LegacyHologramDraft draft,
                                           LegacyWorldCatalog worlds) {
    String leafId = canonicalLeaf(draft.legacyId());
    String prefix = "imports/" + source.id();
    String menuId = MenuIds.require(prefix + "/" + leafId);
    String boardId = BoardIds.canonicalize(prefix + "/" + leafId);
    LegacyWorldCatalog.WorldDescriptor world = worlds.resolve(draft.location().worldReference())
        .orElseThrow(() -> new IllegalArgumentException(
            "world is not loaded: " + draft.location().worldReference()));
    double viewRange = normalizeViewRange(draft.viewRange());
    double interactionRange = Math.min(DEFAULT_INTERACTION_RANGE, viewRange);
    BoardVisibility visibility = draft.permission() == null
        ? BoardVisibility.publicAccess().withRanges(viewRange, interactionRange)
        : BoardVisibility.permission(draft.permission(), draft.permission())
            .withRanges(viewRange, interactionRange);
    BoardTransform transform = new BoardTransform(world.key(), world.uuid(), draft.location().x(),
        draft.location().y(), draft.location().z(), draft.location().yaw() - 180.0D,
        draft.location().pitch(), 0.0D, 1.0D);
    BoardDefinition board = BoardDefinition.create(boardId, menuId, transform)
        .withVisibility(visibility);
    List<String> warnings = new ArrayList<>(draft.warnings());
    if (draft.viewRange() != -1.0D && draft.viewRange() > BoardVisibility.MAX_VIEW_RANGE) {
      warnings.add("Source view range " + draft.viewRange() + " was capped at HoloUI's "
          + BoardVisibility.MAX_VIEW_RANGE + "-block board limit");
    }
    MenuConversion menu = menuSource(draft, viewRange, transform.yaw(), transform.pitch());
    String menuSource = menu.source();
    validateMenu(menuId, menuSource);
    warnings.addAll(menu.warnings());
    return new LegacyImportCandidate(draft.legacyId(), draft.sourceIdentity(), menuId, boardId,
        menuSource, board, LegacyImportDisposition.READY, "", distinct(warnings));
  }

  private static MenuConversion menuSource(LegacyHologramDraft draft, double viewRange,
                                           double facingYaw, double pitch) {
    JsonObject menu = new JsonObject();
    menu.add("offset", vector(0.0D, 0.0D, 0.0D));
    menu.addProperty("lockPosition", true);
    menu.addProperty("followPlayer", false);
    menu.addProperty("closeOnDeath", false);
    menu.addProperty("closeOnTeleport", false);
    menu.addProperty("maxDistance", viewRange);
    JsonArray components = new JsonArray();
    List<String> warnings = new ArrayList<>();
    int rowNumber = 1;
    for (LegacyHologramDraft.LegacyRow row : draft.rows()) {
      LegacyHologramDraft.LegacyStyle style = draft.style().overlay(row.style());
      JsonObject component = new JsonObject();
      component.addProperty("id", "row-" + rowNumber++);
      JsonObject data = new JsonObject();
      data.addProperty("type", "decoration");
      IconConversion icon = icon(row.text(), style, viewRange, warnings);
      LocalOffset encoded = inverseOffset(row.x(), row.y(), row.z(), facingYaw, pitch)
          .subtract(icon.anchorOffset());
      component.add("offset", vector(encoded.x(), encoded.y(), encoded.z()));
      data.add("icon", icon.icon());
      component.add("data", data);
      components.add(component);
    }
    menu.add("components", components);
    return new MenuConversion(BukkitJson.GSON.toJson(menu) + System.lineSeparator(), warnings);
  }

  private static IconConversion icon(String content, LegacyHologramDraft.LegacyStyle style,
                                     double viewRange, List<String> warnings) {
    String rowLabel = warningRow(content);
    if (style.brightness() != null
        && (style.brightness() < 0 || style.brightness() > 15)) {
      warnings.add("GHolo brightness for row '" + rowLabel
          + "' was clamped to HoloUI's 0-15 light range");
    }
    IconReference reference = iconReference(content);
    if (reference == null || !reference.staticPayload()) {
      JsonObject icon = new JsonObject();
      icon.addProperty("type", "text");
      icon.addProperty("text", translateText(content));
      icon.add("style", style(style, viewRange, true, true, 1.0D));
      if (reference != null) {
        warnings.add("Dynamic " + reference.type() + " row '" + rowLabel
            + "' was preserved as text because its icon type can change per viewer");
      }
      if (content.indexOf('\n') >= 0) {
        warnings.add("Multi-line GHolo row '" + rowLabel
            + "' is rendered as separate HoloUI text displays");
      }
      return new IconConversion(icon,
          new LocalOffset(0.0D, -2.0D * TEXT_LINE_HEIGHT * effectiveScale(style.scaleY()), 0.0D));
    }

    JsonObject icon = new JsonObject();
    LocalOffset anchorOffset;
    switch (reference.type()) {
      case "block" -> {
        icon.addProperty("type", "block");
        icon.addProperty("block", reference.payload());
        icon.add("style", style(style, viewRange, true, false,
            BLOCK_ICON_SCALE_COMPENSATION));
        anchorOffset = new LocalOffset(0.0D, BLOCK_ICON_VERTICAL_OFFSET, 0.0D);
        warnings.add("GHolo block row '" + rowLabel
            + "' preserves entity anchor and scale, but HoloUI centers block geometry around that anchor");
      }
      case "item" -> {
        icon.addProperty("type", "item");
        icon.addProperty("item", reference.payload());
        icon.addProperty("count", 1);
        icon.add("style", style(style, viewRange, true, false, 1.0D));
        anchorOffset = itemAnchorOffset(reference.payload(), style);
      }
      case "itemstack" -> {
        icon.addProperty("type", "item");
        icon.addProperty("item", reference.payload());
        icon.addProperty("count", 1);
        LegacyHologramDraft.LegacyStyle itemStackStyle = LegacyHologramDraft.LegacyStyle.empty();
        icon.add("style", style(itemStackStyle, viewRange, false, false, 1.0D));
        anchorOffset = itemAnchorOffset(reference.payload(), itemStackStyle);
        warnings.add("GHolo itemstack row '" + rowLabel
            + "' was imported as a default-style item display; dropped-item spin, bob, and raw-entity presentation are not preserved");
      }
      case "entity" -> {
        icon.addProperty("type", "entity");
        icon.addProperty("entity", reference.payload());
        anchorOffset = LocalOffset.ZERO;
        warnings.add("GHolo entity row '" + rowLabel
            + "' uses HoloUI's default 1x1 interaction geometry; GHolo display size does not apply to raw entities");
      }
      default -> throw new IllegalArgumentException("unsupported GHolo row type: " + reference.type());
    }
    return new IconConversion(icon, anchorOffset);
  }

  private static JsonObject style(LegacyHologramDraft.LegacyStyle style, double viewRange,
                                  boolean includeCulling, boolean includeText,
                                  double scaleMultiplier) {
    JsonObject output = new JsonObject();
    addString(output, "billboard", billboard(style.billboard()));
    output.addProperty("viewRange", viewRange / 64.0D);
    if (includeText) {
      addBoolean(output, "shadow", style.textShadow());
      addBoolean(output, "seeThrough", style.seeThrough());
      addString(output, "textAlignment", alignment(style.textAlignment()));
      addString(output, "backgroundArgb", background(style.background()));
      output.addProperty("lineWidth", 10000);
      if (style.textOpacityPercent() != null) {
        int percent = Math.max(0, Math.min(100, style.textOpacityPercent()));
        output.addProperty("textOpacity", 255 - percent * 231 / 100);
      }
    }
    if (style.brightness() != null) {
      int light = Math.max(0, Math.min(15, style.brightness()));
      output.addProperty("blockLight", light);
      output.addProperty("skyLight", 15);
    }
    addScale(output, "scaleX", scaled(style.scaleX(), scaleMultiplier));
    addScale(output, "scaleY", scaled(style.scaleY(), scaleMultiplier));
    addScale(output, "scaleZ", scaled(style.scaleZ(), scaleMultiplier));
    if (includeCulling) {
      output.addProperty("cullingWidth", dimension(style.width(), "culling width"));
      output.addProperty("cullingHeight", dimension(style.height(), "culling height"));
    }
    return output;
  }

  static LocalOffset inverseOffset(double x, double y, double z, double facingYaw,
                                   double pitch) {
    double yawRadians = Math.toRadians(facingYaw);
    double yawCosine = Math.cos(yawRadians);
    double yawSine = Math.sin(yawRadians);
    double yawX = yawCosine * x + yawSine * z;
    double yawZ = -yawSine * x + yawCosine * z;
    double pitchRadians = Math.toRadians(-pitch);
    double pitchCosine = Math.cos(pitchRadians);
    double pitchSine = Math.sin(pitchRadians);
    double pitchY = pitchCosine * y - pitchSine * yawZ;
    double pitchZ = pitchSine * y + pitchCosine * yawZ;
    return new LocalOffset(-yawX, pitchY, pitchZ);
  }

  private static LocalOffset itemAnchorOffset(String payload,
                                              LegacyHologramDraft.LegacyStyle style) {
    return itemAnchorOffset(usesBlockItemLayout(payload), effectiveScale(style.scaleY()));
  }

  static LocalOffset itemAnchorOffset(boolean blockLayout, double scaleY) {
    if (blockLayout) {
      return new LocalOffset(0.0D, BLOCK_ITEM_VERTICAL_OFFSET, BLOCK_ITEM_DEPTH_OFFSET);
    }
    return new LocalOffset(0.0D, ITEM_ICON_OFFSET - TEXT_LINE_HEIGHT * scaleY, 0.0D);
  }

  private static boolean usesBlockItemLayout(String payload) {
    if (Bukkit.getServer() == null) {
      return false;
    }
    Material material = Material.matchMaterial(payload);
    if (material == null) {
      return false;
    }
    try {
      return material.isBlock() && !ITEM_BLOCK_LAYOUT_EXCLUSIONS.contains(material.name());
    } catch (RuntimeException | LinkageError failure) {
      return false;
    }
  }

  private static Double scaled(Double value, double multiplier) {
    if (multiplier == 1.0D) {
      return value;
    }
    return effectiveScale(value) * multiplier;
  }

  private static double effectiveScale(Double value) {
    double resolved = value == null ? 1.0D : value;
    if (!Double.isFinite(resolved) || resolved < 0.01D || resolved > 64.0D) {
      throw new IllegalArgumentException("display scale must be finite and between 0.01 and 64");
    }
    return resolved;
  }

  private static IconReference iconReference(String content) {
    if (content == null) {
      return null;
    }
    int separator = content.indexOf(':');
    if (separator <= 0 || separator == content.length() - 1) {
      return null;
    }
    String type = content.substring(0, separator).toLowerCase(Locale.ROOT);
    if (!type.equals("block") && !type.equals("item") && !type.equals("itemstack")
        && !type.equals("entity")) {
      return null;
    }
    String payload = content.substring(separator + 1).strip().toLowerCase(Locale.ROOT);
    boolean staticPayload = payload.matches("(?:[a-z0-9_.-]+:)?[a-z0-9/._-]+");
    return new IconReference(type, payload, staticPayload);
  }

  private static void validateMenu(String menuId, String menuSource) {
    if (Bukkit.getServer() == null) {
      if (!JsonParser.parseString(menuSource).isJsonObject()) {
        throw new IllegalArgumentException("generated menu must be a JSON object");
      }
      return;
    }
    MenuDocument document = MenuDocumentParser.parse(menuId, menuSource);
    document.definition().getComponents().forEach(component -> {
      if (!(component.data() instanceof DecoComponentData decoration)) {
        return;
      }
      MenuIconData icon = decoration.iconData();
      try {
        if (icon instanceof BlockIconData block) {
          block.requireBlock();
        } else if (icon instanceof ItemIconData item) {
          item.requireMaterial();
        } else if (icon instanceof EntityIconData entity) {
          entity.requireEntityType();
        }
      } catch (Exception failure) {
        throw new IllegalArgumentException("row " + component.id() + " has an invalid icon: "
            + safeMessage(failure), failure);
      }
    });
  }

  private static double dimension(Double value, String field) {
    double resolved = value == null ? 1.0D : value;
    if (!Double.isFinite(resolved) || resolved <= 0.0D || resolved > MAX_CULLING_DIMENSION) {
      throw new IllegalArgumentException(field + " must be finite, greater than 0, and at most "
          + MAX_CULLING_DIMENSION);
    }
    return resolved;
  }

  private static String translateText(String source) {
    if (source == null || source.isEmpty()) {
      return "";
    }
    String value = source.replaceAll("(?i)<#([0-9A-F]{6})>", "<color:#$1>");
    value = value.replaceAll("(?i)&#([0-9A-F]{6})[0-9A-F]*", "<color:#$1>");
    return value.replaceAll("(?i)(?<![:<&])#([0-9A-F]{6})(?![0-9A-F])", "<color:#$1>");
  }

  private static String warningRow(String content) {
    if (content == null || content.isEmpty()) {
      return "";
    }
    String normalized = content.replace('\n', ' ').replace('\r', ' ');
    return normalized.length() <= MAX_WARNING_ROW_LENGTH
        ? normalized
        : normalized.substring(0, MAX_WARNING_ROW_LENGTH) + "...";
  }

  private static String billboard(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return switch (value.strip().toLowerCase(Locale.ROOT)) {
      case "fixed" -> "fixed";
      case "vertical" -> "vertical";
      case "horizontal" -> "horizontal";
      case "center" -> "center";
      default -> throw new IllegalArgumentException("unsupported billboard: " + value);
    };
  }

  private static String alignment(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return switch (value.strip().toLowerCase(Locale.ROOT)) {
      case "left" -> "left";
      case "right" -> "right";
      case "center" -> "center";
      default -> throw new IllegalArgumentException("unsupported text alignment: " + value);
    };
  }

  private static String background(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.strip();
    if (normalized.equalsIgnoreCase("transparent")) {
      return IconArgbColor.TRANSPARENT.hex();
    }
    if (!normalized.startsWith("#")) {
      normalized = "#" + normalized;
    }
    if (normalized.matches("#[0-9A-Fa-f]{6}")) {
      normalized = "#40" + normalized.substring(1);
    } else if (normalized.matches("#[0-9A-Fa-f]{8}")) {
      normalized = "#" + normalized.substring(7, 9) + normalized.substring(1, 7);
    }
    return IconArgbColor.parse(normalized).hex();
  }

  static String canonicalLeaf(String legacyId) {
    if (legacyId == null || legacyId.isBlank()) {
      throw new IllegalArgumentException("legacy id must not be blank");
    }
    String normalized = legacyId.strip().toLowerCase(Locale.ROOT);
    StringBuilder output = new StringBuilder(Math.min(normalized.length(), 64));
    boolean separator = false;
    for (int index = 0; index < normalized.length(); index++) {
      char character = normalized.charAt(index);
      boolean allowed = character >= 'a' && character <= 'z'
          || character >= '0' && character <= '9'
          || character == '.' || character == '_' || character == '-';
      if (allowed) {
        output.append(character);
        separator = false;
      } else if (!separator && !output.isEmpty()) {
        output.append('-');
        separator = true;
      }
      if (output.length() >= 64) {
        break;
      }
    }
    while (!output.isEmpty() && output.charAt(output.length() - 1) == '-') {
      output.deleteCharAt(output.length() - 1);
    }
    if (output.isEmpty() || !Character.isLetterOrDigit(output.charAt(0))) {
      throw new IllegalArgumentException("legacy id has no usable characters: " + legacyId);
    }
    return output.toString();
  }

  private static double normalizeViewRange(double value) {
    double range = value == -1.0D ? DEFAULT_VIEW_RANGE : value;
    if (!Double.isFinite(range) || range <= 0.0D) {
      throw new IllegalArgumentException("view range must be finite and greater than zero");
    }
    return Math.min(range, BoardVisibility.MAX_VIEW_RANGE);
  }

  private static void addString(JsonObject output, String key, String value) {
    if (value != null) {
      output.addProperty(key, value);
    }
  }

  private static void addBoolean(JsonObject output, String key, Boolean value) {
    if (value != null) {
      output.addProperty(key, value);
    }
  }

  private static void addScale(JsonObject output, String key, Double value) {
    if (value == null) {
      return;
    }
    if (!Double.isFinite(value) || value < 0.01D || value > 64.0D) {
      throw new IllegalArgumentException(key + " must be finite and between 0.01 and 64");
    }
    output.addProperty(key, value);
  }

  private static JsonArray vector(double x, double y, double z) {
    JsonArray vector = new JsonArray();
    vector.add(x);
    vector.add(y);
    vector.add(z);
    return vector;
  }

  private static List<String> distinct(List<String> values) {
    return values.stream().filter(value -> value != null && !value.isBlank()).distinct().toList();
  }

  private static void markConflict(List<LegacyImportCandidate> candidates, String boardId,
                                   String sourceIdentity) {
    for (int index = 0; index < candidates.size(); index++) {
      LegacyImportCandidate candidate = candidates.get(index);
      if (candidate.boardId().equals(boardId) && candidate.sourceIdentity().equals(sourceIdentity)) {
        candidates.set(index, candidate.withDisposition(LegacyImportDisposition.CONFLICT,
            "canonical id collides inside source"));
        return;
      }
    }
  }

  private static LegacyImportIssue error(String id, String message) {
    return new LegacyImportIssue(LegacyImportIssue.Severity.ERROR, id, message);
  }

  private static String safeMessage(Throwable failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
  }

  record ConversionResult(List<LegacyImportCandidate> candidates,
                          List<LegacyImportIssue> issues) {
    ConversionResult {
      candidates = List.copyOf(candidates);
      issues = List.copyOf(issues);
    }
  }

  private record MenuConversion(String source, List<String> warnings) {
    private MenuConversion {
      source = Objects.requireNonNull(source, "source");
      warnings = List.copyOf(warnings);
    }
  }

  private record IconReference(String type, String payload, boolean staticPayload) {
  }

  private record IconConversion(JsonObject icon, LocalOffset anchorOffset) {
    private IconConversion {
      icon = Objects.requireNonNull(icon, "icon");
      anchorOffset = Objects.requireNonNull(anchorOffset, "anchorOffset");
    }
  }

  static record LocalOffset(double x, double y, double z) {
    private static final LocalOffset ZERO = new LocalOffset(0.0D, 0.0D, 0.0D);

    LocalOffset {
      if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
        throw new IllegalArgumentException("local offset must be finite");
      }
    }

    LocalOffset subtract(LocalOffset other) {
      LocalOffset required = Objects.requireNonNull(other, "other");
      return new LocalOffset(x - required.x, y - required.y, z - required.z);
    }
  }
}
