package art.arcane.holoui.importer;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class LegacyHologramScanner {
  static final int MAX_ROW_TEXT_LENGTH = 8192;
  static final int MAX_FILES = 4096;
  static final int MAX_HOLOGRAMS = 4096;
  static final int MAX_ROWS_PER_HOLOGRAM = 1024;
  static final long MAX_SOURCE_BYTES = 8L * 1024L * 1024L;

  private static final double DEFAULT_ROW_SPACING = 0.26D;
  private static final double DEFAULT_VIEW_RANGE = 120.0D;
  private static final int MAX_WARNING_ROW_LENGTH = 160;
  private static final Set<String> SUPPORTED_YAML_EXTENSIONS = Set.of(".yml", ".yaml");

  LegacyScanResult scan(LegacyImportSource source, Path sourcePath, Path trustedRoot) {
    Objects.requireNonNull(source, "source");
    Path requiredPath = Objects.requireNonNull(sourcePath, "sourcePath").toAbsolutePath().normalize();
    Path requiredRoot = Objects.requireNonNull(trustedRoot, "trustedRoot").toAbsolutePath().normalize();
    List<LegacyHologramDraft> drafts = new ArrayList<>();
    List<LegacyImportIssue> issues = new ArrayList<>();
    try {
      validateSourcePath(requiredRoot, requiredPath);
    } catch (IOException failure) {
      issues.add(error("-", safeMessage(failure)));
      return new LegacyScanResult(Files.exists(requiredPath, LinkOption.NOFOLLOW_LINKS), drafts, issues);
    }
    boolean present = Files.exists(requiredPath, LinkOption.NOFOLLOW_LINKS);
    if (!present) {
      return new LegacyScanResult(false, List.of(), List.of());
    }
    if (Files.isSymbolicLink(requiredPath)) {
      issues.add(error("-", "source path is a symbolic link"));
      return new LegacyScanResult(true, drafts, issues);
    }

    try {
      switch (source) {
        case GHOLO -> scanDirectory(requiredPath, drafts, issues, this::parseGholoFile);
        case DECENT_HOLOGRAMS -> scanDirectory(requiredPath, drafts, issues, this::parseDecentFile);
        case HOLOGRAPHIC_DISPLAYS -> scanHolographicDisplays(requiredPath, drafts, issues);
        case FANCY_HOLOGRAMS -> scanFancyHolograms(requiredPath, drafts, issues);
      }
    } catch (IOException failure) {
      issues.add(error("-", safeMessage(failure)));
    }

    return new LegacyScanResult(true, drafts, issues);
  }

  private void scanDirectory(Path directory, List<LegacyHologramDraft> drafts,
                             List<LegacyImportIssue> issues, FileParser parser) throws IOException {
    if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
      issues.add(error("-", "source path is not a real directory"));
      return;
    }
    List<Path> files = new ArrayList<>();
    int entriesSeen = 0;
    try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
      for (Path entry : entries) {
        entriesSeen++;
        if (entriesSeen > MAX_FILES) {
          issues.add(error("-", "source directory contains more than " + MAX_FILES + " entries"));
          return;
        }
        if (Files.isSymbolicLink(entry)) {
          issues.add(error(displayName(entry), "symbolic-link source entry was rejected"));
          continue;
        }
        if (!Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS) || !isYaml(entry)) {
          continue;
        }
        files.add(entry);
      }
    }
    files.sort(Comparator
        .comparing((Path path) -> displayName(path).toLowerCase(Locale.ROOT))
        .thenComparing(LegacyHologramScanner::displayName));
    for (Path file : files) {
      if (drafts.size() >= MAX_HOLOGRAMS) {
        issues.add(error("-", "source contains more than " + MAX_HOLOGRAMS + " holograms"));
        return;
      }
      parseOne(file, drafts, issues, parser);
    }
  }

  private void scanHolographicDisplays(Path file, List<LegacyHologramDraft> drafts,
                                       List<LegacyImportIssue> issues) throws IOException {
    YamlConfiguration yaml = loadYaml(file);
    List<String> ids = new ArrayList<>(yaml.getKeys(false));
    ids.sort(stableIdOrder());
    requireHologramCount(ids.size());
    for (String id : ids) {
      try {
        String world;
        double x;
        double y;
        double z;
        String location = yaml.getString(id + ".location");
        if (location != null) {
          String[] parts = location.split(",", -1);
          if (parts.length < 4) {
            throw new IllegalArgumentException("location must contain world,x,y,z");
          }
          world = parts[0];
          x = parseNumber(parts[1], "location x");
          y = parseNumber(parts[2], "location y") - 0.51D;
          z = parseNumber(parts[3], "location z");
        } else {
          String path = id + ".position";
          world = requiredString(yaml, path + ".world");
          x = requiredDouble(yaml, path + ".x");
          y = requiredDouble(yaml, path + ".y") - 0.51D;
          z = requiredDouble(yaml, path + ".z");
        }
        LegacyHologramDraft.LegacyStyle defaults = LegacyHologramDraft.LegacyStyle.gholoDefaults();
        List<LegacyHologramDraft.LegacyRow> rows = rows(yaml.getStringList(id + ".lines"),
            defaults, true);
        drafts.add(new LegacyHologramDraft(id, id,
            new LegacyHologramDraft.LegacyLocation(world, x, y, z, 0.0D, 0.0D),
            DEFAULT_VIEW_RANGE, null, defaults, rows,
            List.of("HolographicDisplays item, image, and interaction lines are not imported")));
      } catch (RuntimeException failure) {
        issues.add(error(id, safeMessage(failure)));
      }
    }
  }

  private void scanFancyHolograms(Path file, List<LegacyHologramDraft> drafts,
                                  List<LegacyImportIssue> issues) throws IOException {
    YamlConfiguration yaml = loadYaml(file);
    if (yaml.getInt("version", -1) != 2) {
      issues.add(error("-", "only FancyHolograms document version 2 is supported"));
      return;
    }
    ConfigurationSection holograms = yaml.getConfigurationSection("holograms");
    if (holograms == null) {
      throw new IllegalArgumentException("holograms section is missing");
    }
    List<String> ids = new ArrayList<>(holograms.getKeys(false));
    ids.sort(stableIdOrder());
    requireHologramCount(ids.size());
    for (String id : ids) {
      String base = "holograms." + id;
      try {
        String type = yaml.getString(base + ".type", "");
        if (!type.equalsIgnoreCase("TEXT")) {
          issues.add(warning(id, "non-text FancyHolograms type " + type + " was skipped"));
          continue;
        }
        String location = base + ".location";
        String world = requiredString(yaml, location + ".world");
        double x = requiredDouble(yaml, location + ".x");
        double y = requiredDouble(yaml, location + ".y") + 0.75D;
        double z = requiredDouble(yaml, location + ".z");
        double yaw = optionalDouble(yaml, location + ".yaw", 0.0D);
        double pitch = optionalDouble(yaml, location + ".pitch", 0.0D);
        double range = optionalDouble(yaml, base + ".visibility_distance", DEFAULT_VIEW_RANGE);
        LegacyHologramDraft.LegacyStyle style = LegacyHologramDraft.LegacyStyle.gholoDefaults().overlay(
            new LegacyHologramDraft.LegacyStyle(
            yaml.getString(base + ".background"),
            null,
            yaml.contains(base + ".text_shadow") ? yaml.getBoolean(base + ".text_shadow") : null,
            yaml.getString(base + ".text_alignment"),
            yaml.getString(base + ".billboard"),
            yaml.contains(base + ".see_through") ? yaml.getBoolean(base + ".see_through") : null,
            optionalNullableDouble(yaml, base + ".scale_x"),
            optionalNullableDouble(yaml, base + ".scale_y"),
            optionalNullableDouble(yaml, base + ".scale_z"),
            optionalNullableInteger(yaml, base + ".brightness"),
                null, null, null, null
            ));
        List<String> sourceRows = yaml.getStringList(base + ".text");
        List<LegacyHologramDraft.LegacyRow> rows = rows(sourceRows, style, false);
        drafts.add(new LegacyHologramDraft(id, id,
            new LegacyHologramDraft.LegacyLocation(world, x, y, z, yaw, pitch),
            range, null, style, rows,
            List.of("FancyHolograms update intervals, linked NPCs, and non-text holograms are not imported")));
      } catch (RuntimeException failure) {
        issues.add(error(id, safeMessage(failure)));
      }
    }
  }

  private LegacyHologramDraft parseGholoFile(Path file) throws IOException {
    YamlConfiguration yaml = loadYaml(file);
    String id = sourceId(file);
    String location = "Holo.location";
    String world = requiredString(yaml, location + ".world");
    double x = requiredDouble(yaml, location + ".x");
    double y = requiredDouble(yaml, location + ".y");
    double z = requiredDouble(yaml, location + ".z");
    LegacyHologramDraft.LegacyStyle baseStyle = LegacyHologramDraft.LegacyStyle.gholoDefaults()
        .overlay(style(yaml.getConfigurationSection("Holo.data")));
    double range = optionalDouble(yaml, "Holo.data.range", DEFAULT_VIEW_RANGE);
    String permission = yaml.getString("Holo.data.permission");
    List<Map<?, ?>> sourceRows = gholoRows(yaml);
    requireRowCount(sourceRows.size());
    List<LegacyHologramDraft.LegacyRow> rows = new ArrayList<>(sourceRows.size());
    List<String> warnings = new ArrayList<>();
    for (int rowIndex = 0; rowIndex < sourceRows.size(); rowIndex++) {
      Map<?, ?> sourceRow = sourceRows.get(rowIndex);
      String content = rowContent(sourceRow, rowIndex);
      Map<?, ?> offset = optionalMap(sourceRow, "offset", rowIndex);
      Map<?, ?> rowData = optionalMap(sourceRow, "data", rowIndex);
      LegacyHologramDraft.LegacyStyle rowStyle = style(rowData);
      rows.add(new LegacyHologramDraft.LegacyRow(content,
          mapNumber(offset, "x", 0.0D), mapNumber(offset, "y", 0.0D),
          mapNumber(offset, "z", 0.0D), effectiveGholoRowStyle(baseStyle, rowStyle)));
      appendUnsupportedRowDataWarnings(rowData, content, warnings);
    }
    return new LegacyHologramDraft(id, displayName(file),
        new LegacyHologramDraft.LegacyLocation(world, x, y, z,
            nullable(baseStyle.yaw(), 0.0D), nullable(baseStyle.pitch(), 0.0D)),
        range, permission, baseStyle, rows, List.copyOf(warnings));
  }

  private LegacyHologramDraft parseDecentFile(Path file) throws IOException {
    YamlConfiguration yaml = loadYaml(file);
    String id = sourceId(file);
    String location = requiredString(yaml, "location");
    String[] parts = location.split(":", -1);
    if (parts.length < 4) {
      throw new IllegalArgumentException("location must contain world:x:y:z");
    }
    String world = parts[0];
    double x = parseNumber(parts[1].replace(',', '.'), "location x");
    double y = parseNumber(parts[2].replace(',', '.'), "location y") - 0.41D;
    double z = parseNumber(parts[3].replace(',', '.'), "location z");
    double range = optionalDouble(yaml, "display-range", DEFAULT_VIEW_RANGE);
    List<String> text = new ArrayList<>();
    List<?> pages = yaml.getList("pages", List.of());
    for (Object pageValue : pages) {
      if (!(pageValue instanceof Map<?, ?> page)) {
        continue;
      }
      Object linesValue = page.get("lines");
      if (!(linesValue instanceof List<?> lines)) {
        continue;
      }
      for (Object lineValue : lines) {
        if (!(lineValue instanceof Map<?, ?> line)) {
          continue;
        }
        Object content = line.get("content");
        if (content instanceof String row) {
          text.add(row);
        }
      }
    }
    LegacyHologramDraft.LegacyStyle defaults = LegacyHologramDraft.LegacyStyle.gholoDefaults();
    List<LegacyHologramDraft.LegacyRow> rows = rows(text, defaults, false);
    return new LegacyHologramDraft(id, displayName(file),
        new LegacyHologramDraft.LegacyLocation(world, x, y, z, 0.0D, 0.0D),
        range, null, defaults, rows,
        List.of("Only DecentHolograms text line content is imported; page timing, actions, flags, and non-text line fields are not"));
  }

  private static List<Map<?, ?>> gholoRows(YamlConfiguration yaml) {
    Object value = yaml.get("Holo.rows");
    if (value == null) {
      return List.of();
    }
    if (!(value instanceof List<?> rows)) {
      throw new IllegalArgumentException("Holo.rows must be a list");
    }
    requireRowCount(rows.size());
    List<Map<?, ?>> output = new ArrayList<>(rows.size());
    for (int index = 0; index < rows.size(); index++) {
      Object row = rows.get(index);
      if (!(row instanceof Map<?, ?> map)) {
        throw new IllegalArgumentException("Holo.rows[" + index + "] must be a section");
      }
      output.add(map);
    }
    return output;
  }

  private static String rowContent(Map<?, ?> row, int rowIndex) {
    Object value = row.get("content");
    if (value == null) {
      return "";
    }
    if (!(value instanceof String text)) {
      throw new IllegalArgumentException("Holo.rows[" + rowIndex + "].content must be text");
    }
    return text;
  }

  private static Map<?, ?> optionalMap(Map<?, ?> row, String key, int rowIndex) {
    if (!row.containsKey(key)) {
      return Map.of();
    }
    Object value = row.get(key);
    if (!(value instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException("Holo.rows[" + rowIndex + "]." + key
          + " must be a section");
    }
    return map;
  }

  private void parseOne(Path file, List<LegacyHologramDraft> drafts,
                        List<LegacyImportIssue> issues, FileParser parser) {
    try {
      drafts.add(parser.parse(file));
    } catch (IOException | RuntimeException failure) {
      issues.add(error(displayName(file), safeMessage(failure)));
    }
  }

  private static List<LegacyHologramDraft.LegacyRow> rows(List<String> content,
                                                           LegacyHologramDraft.LegacyStyle style,
                                                           boolean removeNullRows) {
    requireRowCount(content.size());
    List<LegacyHologramDraft.LegacyRow> rows = new ArrayList<>(content.size());
    double offset = 0.0D;
    for (String row : content) {
      if (removeNullRows && "null".equalsIgnoreCase(row)) {
        continue;
      }
      rows.add(new LegacyHologramDraft.LegacyRow(row, 0.0D, offset, 0.0D, style));
      offset -= DEFAULT_ROW_SPACING;
    }
    return rows;
  }

  private static LegacyHologramDraft.LegacyStyle style(ConfigurationSection section) {
    if (section == null) {
      return LegacyHologramDraft.LegacyStyle.empty();
    }
    return new LegacyHologramDraft.LegacyStyle(
        firstString(section, "backgroundColor", "background_color"),
        firstInteger(section, "textOpacity", "text_opacity"),
        firstBoolean(section, "textShadow", "text_shadow"),
        firstString(section, "textAlignment", "text_alignment"),
        firstString(section, "billboard"),
        firstBoolean(section, "seeThrough", "see_through"),
        nestedDouble(section, "scale", "x", 1.0D),
        nestedDouble(section, "scale", "y", 1.0D),
        nestedDouble(section, "scale", "z", 1.0D),
        firstInteger(section, "brightness"),
        nestedDouble(section, "rotation", "yaw", null),
        nestedDouble(section, "rotation", "pitch", null),
        nestedDouble(section, "size", "width", 1.0D),
        nestedDouble(section, "size", "height", 1.0D)
    );
  }

  private static LegacyHologramDraft.LegacyStyle style(Map<?, ?> values) {
    Map<String, Object> normalized = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : values.entrySet()) {
      if (entry.getKey() instanceof String key) {
        normalized.put(key, entry.getValue());
      }
    }
    return new LegacyHologramDraft.LegacyStyle(
        mapString(normalized, "backgroundColor", "background_color"),
        mapInteger(normalized, "textOpacity", "text_opacity"),
        mapBoolean(normalized, "textShadow", "text_shadow"),
        mapString(normalized, "textAlignment", "text_alignment"),
        mapString(normalized, "billboard"),
        mapBoolean(normalized, "seeThrough", "see_through"),
        nestedMapDouble(normalized.get("scale"), "scale", "x", 1.0D),
        nestedMapDouble(normalized.get("scale"), "scale", "y", 1.0D),
        nestedMapDouble(normalized.get("scale"), "scale", "z", 1.0D),
        mapInteger(normalized, "brightness"),
        nestedMapDouble(normalized.get("rotation"), "rotation", "yaw", null),
        nestedMapDouble(normalized.get("rotation"), "rotation", "pitch", null),
        nestedMapDouble(normalized.get("size"), "size", "width", 1.0D),
        nestedMapDouble(normalized.get("size"), "size", "height", 1.0D)
    );
  }

  private static LegacyHologramDraft.LegacyStyle effectiveGholoRowStyle(
      LegacyHologramDraft.LegacyStyle base, LegacyHologramDraft.LegacyStyle row) {
    boolean rowScale = differs(row.scaleX(), 1.0D)
        || differs(row.scaleY(), 1.0D)
        || differs(row.scaleZ(), 1.0D);
    boolean rowSize = differs(row.width(), 1.0D) || differs(row.height(), 1.0D);
    return new LegacyHologramDraft.LegacyStyle(
        row.background() != null && !row.background().equals("#000000")
            ? row.background() : base.background(),
        row.textOpacityPercent() != null && row.textOpacityPercent() != 0
            ? row.textOpacityPercent() : base.textOpacityPercent(),
        Boolean.TRUE.equals(row.textShadow()) ? true : base.textShadow(),
        row.textAlignment() != null && !row.textAlignment().equals("center")
            ? row.textAlignment() : base.textAlignment(),
        row.billboard() != null && !row.billboard().equals("center")
            ? row.billboard() : base.billboard(),
        Boolean.TRUE.equals(row.seeThrough()) ? true : base.seeThrough(),
        rowScale ? row.scaleX() : base.scaleX(),
        rowScale ? row.scaleY() : base.scaleY(),
        rowScale ? row.scaleZ() : base.scaleZ(),
        row.brightness() == null ? base.brightness() : row.brightness(),
        row.yaw() == null ? base.yaw() : row.yaw(),
        row.pitch() == null ? base.pitch() : row.pitch(),
        rowSize ? row.width() : base.width(),
        rowSize ? row.height() : base.height()
    );
  }

  private static boolean differs(Double value, double expected) {
    return value != null && Double.compare(value, expected) != 0;
  }

  private static YamlConfiguration loadYaml(Path path) throws IOException {
    Path file = path.toAbsolutePath().normalize();
    if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("source must be a real regular file");
    }
    byte[] source;
    try (InputStream input = Files.newInputStream(file, StandardOpenOption.READ,
        LinkOption.NOFOLLOW_LINKS)) {
      source = input.readNBytes((int) MAX_SOURCE_BYTES + 1);
    }
    if (source.length > MAX_SOURCE_BYTES) {
      throw new IOException("source exceeds " + MAX_SOURCE_BYTES + " bytes");
    }
    YamlConfiguration yaml = new YamlConfiguration();
    try {
      yaml.loadFromString(new String(source, StandardCharsets.UTF_8));
    } catch (Exception failure) {
      throw new IOException("invalid YAML: " + safeMessage(failure), failure);
    }
    return yaml;
  }

  private static boolean isYaml(Path path) {
    String name = displayName(path).toLowerCase(Locale.ROOT);
    for (String extension : SUPPORTED_YAML_EXTENSIONS) {
      if (name.endsWith(extension)) {
        return true;
      }
    }
    return false;
  }

  private static String sourceId(Path file) {
    String name = displayName(file);
    int extension = name.lastIndexOf('.');
    return extension < 1 ? name : name.substring(0, extension);
  }

  private static String displayName(Path path) {
    Path name = path.getFileName();
    return name == null ? path.toString() : name.toString();
  }

  private static Comparator<String> stableIdOrder() {
    return String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());
  }

  private static String requiredString(YamlConfiguration yaml, String path) {
    String value = yaml.getString(path);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(path + " must not be blank");
    }
    return value;
  }

  private static double requiredDouble(YamlConfiguration yaml, String path) {
    Object value = yaml.get(path);
    if (!(value instanceof Number number)) {
      if (value instanceof String text) {
        return parseNumber(text, path);
      }
      throw new IllegalArgumentException(path + " must be a number");
    }
    return finite(number.doubleValue(), path);
  }

  private static double optionalDouble(YamlConfiguration yaml, String path, double fallback) {
    return yaml.contains(path) ? requiredDouble(yaml, path) : fallback;
  }

  private static Double optionalNullableDouble(YamlConfiguration yaml, String path) {
    return yaml.contains(path) ? requiredDouble(yaml, path) : null;
  }

  private static Integer optionalNullableInteger(YamlConfiguration yaml, String path) {
    if (!yaml.contains(path)) {
      return null;
    }
    Object value = yaml.get(path);
    try {
      return value instanceof Number number
          ? number.intValue()
          : Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException failure) {
      throw new IllegalArgumentException(path + " must be an integer");
    }
  }

  private static double parseNumber(String value, String field) {
    try {
      return finite(Double.parseDouble(value.strip()), field);
    } catch (NumberFormatException failure) {
      throw new IllegalArgumentException(field + " must be a number");
    }
  }

  private static double finite(double value, String field) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException(field + " must be finite");
    }
    return value;
  }

  private static void requireHologramCount(int count) {
    if (count > MAX_HOLOGRAMS) {
      throw new IllegalArgumentException("source contains more than " + MAX_HOLOGRAMS + " holograms");
    }
  }

  private static void requireRowCount(int count) {
    if (count > MAX_ROWS_PER_HOLOGRAM) {
      throw new IllegalArgumentException("hologram contains more than "
          + MAX_ROWS_PER_HOLOGRAM + " rows");
    }
  }

  private static void appendUnsupportedRowDataWarnings(Map<?, ?> rowData, String content,
                                                       List<String> warnings) {
    String rowLabel = warningRow(content);
    if (rowData.containsKey("range")) {
      warnings.add("GHolo row-specific range is not preserved for row '" + rowLabel + "'");
    }
    if (rowData.containsKey("permission")) {
      warnings.add("GHolo row-specific permission is not preserved for row '" + rowLabel + "'");
    }
    if (rowData.containsKey("rotation")) {
      warnings.add("GHolo row-specific rotation is not preserved for row '" + rowLabel + "'");
    }
  }

  private static String warningRow(String content) {
    String normalized = content.replace('\n', ' ').replace('\r', ' ');
    return normalized.length() <= MAX_WARNING_ROW_LENGTH
        ? normalized
        : normalized.substring(0, MAX_WARNING_ROW_LENGTH) + "...";
  }

  private static void validateSourcePath(Path root, Path target) throws IOException {
    if (!target.startsWith(root)) {
      throw new IOException("legacy import source escapes the plugins directory");
    }
    Path current = root;
    if (Files.isSymbolicLink(current)) {
      throw new IOException("plugins directory is a symbolic link");
    }
    for (Path segment : root.relativize(target)) {
      current = current.resolve(segment);
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
        throw new IOException("legacy import source contains a symbolic-link path: " + current);
      }
    }
  }

  private static String firstString(ConfigurationSection section, String... keys) {
    for (String key : keys) {
      if (section.contains(key)) {
        Object value = section.get(key);
        if (!(value instanceof String text)) {
          throw new IllegalArgumentException(key + " must be text");
        }
        return text;
      }
    }
    return null;
  }

  private static Integer firstInteger(ConfigurationSection section, String... keys) {
    for (String key : keys) {
      if (section.contains(key)) {
        Object value = section.get(key);
        if (!(value instanceof Number number)) {
          throw new IllegalArgumentException(key + " must be an integer");
        }
        return number.intValue();
      }
    }
    return null;
  }

  private static Boolean firstBoolean(ConfigurationSection section, String... keys) {
    for (String key : keys) {
      if (section.contains(key)) {
        Object value = section.get(key);
        if (!(value instanceof Boolean bool)) {
          throw new IllegalArgumentException(key + " must be true or false");
        }
        return bool;
      }
    }
    return null;
  }

  private static Double nestedDouble(ConfigurationSection section, String key, String child,
                                     Double missingChildValue) {
    if (!section.contains(key)) {
      return null;
    }
    ConfigurationSection nested = section.getConfigurationSection(key);
    if (nested == null) {
      throw new IllegalArgumentException(key + " must be a section");
    }
    if (!nested.contains(child)) {
      return missingChildValue;
    }
    return requiredMapNumber(nested.get(child), key + "." + child);
  }

  private static String mapString(Map<String, Object> values, String... keys) {
    for (String key : keys) {
      if (values.containsKey(key)) {
        Object value = values.get(key);
        if (!(value instanceof String text)) {
          throw new IllegalArgumentException(key + " must be text");
        }
        return text;
      }
    }
    return null;
  }

  private static Integer mapInteger(Map<String, Object> values, String... keys) {
    for (String key : keys) {
      if (values.containsKey(key)) {
        Object value = values.get(key);
        if (!(value instanceof Number number)) {
          throw new IllegalArgumentException(key + " must be an integer");
        }
        return number.intValue();
      }
    }
    return null;
  }

  private static Boolean mapBoolean(Map<String, Object> values, String... keys) {
    for (String key : keys) {
      if (values.containsKey(key)) {
        Object value = values.get(key);
        if (!(value instanceof Boolean bool)) {
          throw new IllegalArgumentException(key + " must be true or false");
        }
        return bool;
      }
    }
    return null;
  }

  private static Double nestedMapDouble(Object value, String key, String child,
                                        Double missingChildValue) {
    if (value == null) {
      return null;
    }
    if (value instanceof ConfigurationSection section) {
      return section.contains(child)
          ? requiredMapNumber(section.get(child), key + "." + child)
          : missingChildValue;
    }
    if (!(value instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException(key + " must be a section");
    }
    if (!map.containsKey(child)) {
      return missingChildValue;
    }
    return requiredMapNumber(map.get(child), key + "." + child);
  }

  private static Double requiredMapNumber(Object value, String field) {
    if (!(value instanceof Number number)) {
      throw new IllegalArgumentException(field + " must be a number");
    }
    return finite(number.doubleValue(), field);
  }

  private static double mapNumber(Map<?, ?> values, String key, double fallback) {
    Object value = values.get(key);
    if (value == null) {
      return fallback;
    }
    if (value instanceof Number number) {
      return finite(number.doubleValue(), key);
    }
    return parseNumber(String.valueOf(value), key);
  }

  private static double nullable(Double value, double fallback) {
    return value == null ? fallback : value;
  }

  private static LegacyImportIssue error(String id, String message) {
    return new LegacyImportIssue(LegacyImportIssue.Severity.ERROR, id, message);
  }

  private static LegacyImportIssue warning(String id, String message) {
    return new LegacyImportIssue(LegacyImportIssue.Severity.WARNING, id, message);
  }

  private static String safeMessage(Throwable failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
  }

  record LegacyScanResult(boolean sourcePresent, List<LegacyHologramDraft> drafts,
                          List<LegacyImportIssue> issues) {
    LegacyScanResult {
      drafts = List.copyOf(drafts);
      issues = List.copyOf(issues);
    }
  }

  @FunctionalInterface
  private interface FileParser {
    LegacyHologramDraft parse(Path file) throws IOException;
  }
}
