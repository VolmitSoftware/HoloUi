package art.arcane.holoui.editor.sync;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.board.BoardDefinition;
import art.arcane.holoui.config.menu.MenuIds;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.Imaging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EditorSyncSnapshotBuilder implements EditorSyncSnapshotSource {
  static final int MAX_MENU_COUNT = 256;
  static final int MAX_IMAGE_COUNT = 512;
  static final int MAX_IMAGE_BYTES = 512 * 1024;
  static final int MAX_MENU_SOURCE_BYTES = 2 * 1024 * 1024;
  static final int MAX_WARNING_COUNT = 256;
  static final int MAX_WARNING_CHARACTERS = 512;
  static final int MAX_IMAGE_DIMENSION = 64;
  static final long MAX_IMAGE_PIXELS = 4_096L;
  static final long MAX_PROJECT_IMAGE_PIXELS = 262_144L;
  static final long MAX_PROJECT_IMAGE_ROWS = 4_096L;
  private static final Pattern PLAYER_OPEN_COMMAND = Pattern.compile(
      "^\\s*/?(?:holoui|holo|hui|holou|hu)\\s+open\\s+(?:menu=)?([^\\s]+)\\s*$",
      Pattern.CASE_INSENSITIVE);
  private static final Gson GSON = new GsonBuilder()
      .serializeNulls()
      .disableHtmlEscaping()
      .create();

  private final HoloUI plugin;

  public EditorSyncSnapshotBuilder(HoloUI plugin) {
    this.plugin = Objects.requireNonNull(plugin, "plugin");
  }

  public EditorSyncProject menu(String menuId, int maximumBytes) {
    String id = MenuIds.require(menuId);
    String source = plugin.getConfigManager().getSource(id)
        .orElseThrow(() -> new IllegalArgumentException("unknown menu: " + id));
    TreeMap<String, String> menus = new TreeMap<>();
    menus.put(id, source);
    return project(EditorSyncKind.MENU, id, null, menus, maximumBytes, new ArrayList<>());
  }

  public EditorSyncProject board(String boardId, int maximumBytes) {
    BoardDefinition board = plugin.getBoardService().get(boardId)
        .orElseThrow(() -> new IllegalArgumentException("unknown board: " + boardId));
    List<String> warnings = new ArrayList<>();
    TreeMap<String, String> menus = reachableMenus(board.rootMenuId(), warnings);
    JsonObject boardJson = GSON.toJsonTree(board).getAsJsonObject();
    return project(EditorSyncKind.BOARD, board.id(), boardJson, menus, maximumBytes, warnings);
  }

  @Override
  public EditorSyncProject fromContent(EditorSyncKind kind, String subjectId,
                                BoardDefinition board, Map<String, String> menuSources,
                                Map<String, byte[]> imageContents, JsonObject immutableConstraints,
                                int maximumBytes) {
    TreeMap<String, String> menus = new TreeMap<>(menuSources);
    validateMenuSources(menus);
    if (imageContents.size() > MAX_IMAGE_COUNT) {
      throw new IllegalArgumentException("images exceed " + MAX_IMAGE_COUNT + " assets");
    }
    JsonObject boardJson = board == null ? null : GSON.toJsonTree(board).getAsJsonObject();
    JsonObject project = baseProject(kind, subjectId, boardJson, menus);
    JsonArray images = new JsonArray();
    Map<String, ValidatedImage> validatedImages = new TreeMap<>();
    List<Map.Entry<String, byte[]>> orderedImages = imageContents.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .toList();
    for (Map.Entry<String, byte[]> entry : orderedImages) {
      if (entry.getValue().length > MAX_IMAGE_BYTES) {
        throw new IllegalArgumentException("image exceeds the sync v1 per-asset limit: "
            + entry.getKey());
      }
      ValidatedImage validated = validateImage(entry.getKey(), entry.getValue());
      String normalizedPath = normalizeRelative(entry.getKey());
      validatedImages.put(normalizedPath, validated);
      JsonObject image = new JsonObject();
      image.addProperty("path", normalizedPath);
      image.addProperty("data", "data:" + validated.mediaType() + ";base64,"
          + Base64.getEncoder().encodeToString(entry.getValue()));
      images.add(image);
    }
    validateImageBudgets(menus.values(), validatedImages);
    project.add("images", images);
    project.add("constraints", Objects.requireNonNull(immutableConstraints, "immutableConstraints")
        .deepCopy());
    JsonArray warnings = new JsonArray();
    Set<String> missingTargets = new java.util.TreeSet<>();
    for (String source : menus.values()) {
      Set<String> targets = new HashSet<>();
      collectTargets(JsonParser.parseString(source), targets);
      targets.stream().filter(target -> !menus.containsKey(target)).forEach(missingTargets::add);
    }
    List<String> generatedWarnings = missingTargets.stream()
        .map(target -> "Referenced menu is not loaded: " + target)
        .toList();
    validateWarnings(generatedWarnings);
    generatedWarnings.forEach(warnings::add);
    project.add("warnings", warnings);
    project.addProperty("baseRevision", EditorSyncJson.revision(project));
    return EditorSyncProject.validated(project, maximumBytes);
  }

  private TreeMap<String, String> reachableMenus(String rootMenuId, List<String> warnings) {
    TreeMap<String, String> menus = new TreeMap<>();
    ArrayDeque<String> pending = new ArrayDeque<>();
    Set<String> visited = new HashSet<>();
    pending.add(MenuIds.require(rootMenuId));
    while (!pending.isEmpty()) {
      String id = pending.removeFirst();
      if (!visited.add(id)) {
        continue;
      }
      if (visited.size() > MAX_MENU_COUNT) {
        throw new IllegalArgumentException("board menu graph exceeds " + MAX_MENU_COUNT + " menus");
      }
      String source = plugin.getConfigManager().getSource(id).orElse(null);
      if (source == null) {
        warnings.add("Referenced menu is not loaded: " + id);
        continue;
      }
      menus.put(id, source);
      JsonElement root = JsonParser.parseString(source);
      Set<String> targets = new HashSet<>();
      collectTargets(root, targets);
      List<String> ordered = new ArrayList<>(targets);
      ordered.sort(String::compareTo);
      pending.addAll(ordered);
    }
    if (!menus.containsKey(rootMenuId)) {
      throw new IllegalArgumentException("board root menu is not loaded: " + rootMenuId);
    }
    return menus;
  }

  private EditorSyncProject project(EditorSyncKind kind, String subjectId, JsonObject board,
                                    TreeMap<String, String> menus, int maximumBytes,
                                    List<String> warnings) {
    validateMenuSources(menus);
    JsonObject project = baseProject(kind, subjectId, board, menus);
    project.add("images", images(menus.values(), maximumBytes, warnings));
    project.add("constraints", constraints(kind, subjectId, board, menus,
        project.getAsJsonArray("images")));
    JsonArray warningArray = new JsonArray();
    List<String> orderedWarnings = warnings.stream().sorted().toList();
    validateWarnings(orderedWarnings);
    orderedWarnings.forEach(warningArray::add);
    project.add("warnings", warningArray);
    project.addProperty("baseRevision", EditorSyncJson.revision(project));
    return EditorSyncProject.validated(project, maximumBytes);
  }

  private JsonObject baseProject(EditorSyncKind kind, String subjectId, JsonObject board,
                                 TreeMap<String, String> menus) {
    JsonObject project = new JsonObject();
    project.addProperty("format", EditorSyncJson.PROJECT_FORMAT);
    project.addProperty("version", EditorSyncJson.PROTOCOL_VERSION);
    project.addProperty("kind", kind.wireName());
    project.addProperty("subjectId", subjectId);
    JsonArray menuArray = new JsonArray();
    for (java.util.Map.Entry<String, String> entry : menus.entrySet()) {
      JsonObject menu = new JsonObject();
      menu.addProperty("id", entry.getKey());
      menu.addProperty("json", entry.getValue());
      menuArray.add(menu);
    }
    project.add("menus", menuArray);
    if (board != null) {
      project.add("board", board);
    }
    return project;
  }

  private JsonObject constraints(EditorSyncKind kind, String subjectId, JsonObject board,
                                 TreeMap<String, String> menus, JsonArray images) {
    JsonObject constraints = new JsonObject();
    constraints.addProperty("subjectId", subjectId);
    JsonArray menuIds = new JsonArray();
    menus.keySet().forEach(menuIds::add);
    constraints.add("menuIds", menuIds);
    JsonArray imagePaths = new JsonArray();
    for (JsonElement image : images) {
      imagePaths.add(EditorSyncJson.requireString(image.getAsJsonObject(), "path"));
    }
    constraints.add("imagePaths", imagePaths);
    if (kind == EditorSyncKind.BOARD) {
      String rootMenuId = EditorSyncJson.requireString(
          Objects.requireNonNull(board, "board"), "rootMenuId");
      int separator = rootMenuId.lastIndexOf('/');
      constraints.addProperty("newMenuPrefix", separator >= 0
          ? rootMenuId.substring(0, separator + 1)
          : rootMenuId + "/");
      constraints.addProperty("newImagePrefix", "sync/" + subjectId + "/");
    } else {
      constraints.addProperty("newImagePrefix", "sync/menus/" + subjectId + "/");
    }
    constraints.addProperty("allowDeletes", false);
    return constraints;
  }

  private JsonArray images(Iterable<String> menuSources, int maximumBytes, List<String> warnings) {
    Set<String> paths = new HashSet<>();
    for (String source : menuSources) {
      collectImagePaths(JsonParser.parseString(source), paths);
    }
    List<String> ordered = new ArrayList<>(paths);
    ordered.sort(String::compareTo);
    if (ordered.size() > MAX_IMAGE_COUNT) {
      throw new IllegalArgumentException("images exceed " + MAX_IMAGE_COUNT + " assets");
    }
    JsonArray images = new JsonArray();
    int totalBytes = 0;
    Map<String, ValidatedImage> validatedImages = new TreeMap<>();
    Path root = plugin.getDataFolder().toPath().toAbsolutePath().normalize().resolve("images");
    for (String relative : ordered) {
      try {
        Path file = resolveConfinedFile(root, relative, true);
        byte[] content = Files.readAllBytes(file);
        if (content.length > MAX_IMAGE_BYTES) {
          throw new EditorSyncProjectTooLargeException(content.length, MAX_IMAGE_BYTES);
        }
        ValidatedImage validated = validateImage(relative, content);
        validatedImages.put(relative, validated);
        totalBytes += content.length;
        if (totalBytes > maximumBytes) {
          throw new EditorSyncProjectTooLargeException(totalBytes, maximumBytes);
        }
        JsonObject image = new JsonObject();
        image.addProperty("path", normalizeRelative(relative));
        image.addProperty("data", "data:" + validated.mediaType() + ";base64,"
            + Base64.getEncoder().encodeToString(content));
        images.add(image);
      } catch (EditorSyncProjectTooLargeException failure) {
        throw failure;
      } catch (IOException | IllegalArgumentException failure) {
        throw new IllegalArgumentException("referenced image cannot be synchronized: "
            + relative + " (" + safeMessage(failure) + ")", failure);
      }
    }
    validateImageBudgets(menuSources, validatedImages);
    return images;
  }

  static void collectTargets(JsonElement element, Set<String> targets) {
    if (element == null || element.isJsonNull()) {
      return;
    }
    if (element.isJsonArray()) {
      for (JsonElement child : element.getAsJsonArray()) {
        collectTargets(child, targets);
      }
      return;
    }
    if (!element.isJsonObject()) {
      return;
    }
    JsonObject object = element.getAsJsonObject();
    String type = primitiveString(object.get("type"));
    if ("navigate".equals(type)) {
      String mode = primitiveString(object.get("mode"));
      String target = primitiveString(object.get("target"));
      if ((mode == null || mode.equals("push") || mode.equals("replace")) && target != null) {
        addMenuId(targets, target);
      }
    } else if ("command".equals(type)) {
      String source = primitiveString(object.get("source"));
      String command = primitiveString(object.get("command"));
      if ((source == null || source.equals("player")) && command != null) {
        Matcher matcher = PLAYER_OPEN_COMMAND.matcher(command);
        if (matcher.matches()) {
          addMenuId(targets, matcher.group(1));
        }
      }
    }
    for (java.util.Map.Entry<String, JsonElement> entry : object.entrySet()) {
      collectTargets(entry.getValue(), targets);
    }
  }

  static void collectImagePaths(JsonElement element, Set<String> paths) {
    List<String> usages = new ArrayList<>();
    collectImageUsages(element, usages);
    paths.addAll(usages);
  }

  static void collectImageUsages(JsonElement element, List<String> paths) {
    if (element == null || element.isJsonNull()) {
      return;
    }
    if (element.isJsonArray()) {
      for (JsonElement child : element.getAsJsonArray()) {
        collectImageUsages(child, paths);
      }
      return;
    }
    if (!element.isJsonObject()) {
      return;
    }
    JsonObject object = element.getAsJsonObject();
    String type = primitiveString(object.get("type"));
    if ("textImage".equals(type)) {
      addImagePath(paths, primitiveString(object.get("path")));
    } else if ("animatedTextImage".equals(type)) {
      JsonElement source = object.get("source");
      if (source != null && source.isJsonArray()) {
        for (JsonElement frame : source.getAsJsonArray()) {
          addImagePath(paths, primitiveString(frame));
        }
      } else {
        addImagePath(paths, primitiveString(source));
      }
    }
    for (java.util.Map.Entry<String, JsonElement> entry : object.entrySet()) {
      collectImageUsages(entry.getValue(), paths);
    }
  }

  static Path resolveConfinedFile(Path root, String relative, boolean requireExisting) throws IOException {
    String normalizedRelative = normalizeRelative(relative);
    Path canonicalRoot = root.toAbsolutePath().normalize();
    if (Files.exists(canonicalRoot, LinkOption.NOFOLLOW_LINKS)
        && (Files.isSymbolicLink(canonicalRoot)
        || !Files.isDirectory(canonicalRoot, LinkOption.NOFOLLOW_LINKS))) {
      throw new IOException("asset root is not a real directory");
    }
    Path candidate = canonicalRoot.resolve(normalizedRelative).normalize();
    if (!candidate.startsWith(canonicalRoot) || candidate.equals(canonicalRoot)) {
      throw new IOException("asset path escapes the image directory");
    }
    Path current = canonicalRoot;
    Path parent = candidate.getParent();
    if (parent == null) {
      throw new IOException("asset path has no parent");
    }
    for (Path segment : canonicalRoot.relativize(parent)) {
      current = current.resolve(segment);
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
        throw new IOException("asset path contains a symbolic link");
      }
    }
    if (Files.isSymbolicLink(candidate)) {
      throw new IOException("asset file is a symbolic link");
    }
    if (requireExisting && !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("asset file does not exist");
    }
    return candidate;
  }

  static String normalizeRelative(String relative) {
    if (relative == null || relative.isBlank()) {
      throw new IllegalArgumentException("asset path must not be blank");
    }
    String normalized = relative.strip().replace('\\', '/');
    if (normalized.startsWith("/") || normalized.contains("//")) {
      throw new IllegalArgumentException("asset path must be relative");
    }
    String[] segments = normalized.split("/", -1);
    for (String segment : segments) {
      if (segment.isBlank() || segment.equals(".") || segment.equals("..") || segment.startsWith(".")) {
        throw new IllegalArgumentException("asset path contains an invalid segment");
      }
    }
    return String.join("/", segments);
  }

  private static void addMenuId(Set<String> targets, String target) {
    try {
      targets.add(MenuIds.require(target));
    } catch (IllegalArgumentException ignored) {
    }
  }

  private static void addImagePath(java.util.Collection<String> paths, String path) {
    if (path == null) {
      return;
    }
    try {
      paths.add(normalizeRelative(path));
    } catch (IllegalArgumentException ignored) {
    }
  }

  private static String primitiveString(JsonElement element) {
    return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
        ? element.getAsString()
        : null;
  }

  static String detectMediaType(byte[] data) {
    if (data.length >= 8 && (data[0] & 0xff) == 0x89 && data[1] == 'P'
        && data[2] == 'N' && data[3] == 'G' && data[4] == 0x0d && data[5] == 0x0a
        && data[6] == 0x1a && data[7] == 0x0a) {
      return "image/png";
    }
    if (data.length >= 3 && (data[0] & 0xff) == 0xff && (data[1] & 0xff) == 0xd8
        && (data[2] & 0xff) == 0xff) {
      return "image/jpeg";
    }
    if (data.length >= 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F'
        && data[3] == '8' && (data[4] == '7' || data[4] == '9') && data[5] == 'a') {
      return "image/gif";
    }
    if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F'
        && data[3] == 'F' && data[8] == 'W' && data[9] == 'E' && data[10] == 'B'
        && data[11] == 'P') {
      return "image/webp";
    }
    if (data.length >= 2 && data[0] == 'B' && data[1] == 'M') {
      return "image/bmp";
    }
    throw new IllegalArgumentException("unsupported image encoding");
  }

  static ValidatedImage validateImage(String path, byte[] data) {
    String mediaType = detectMediaType(data);
    try {
      ImageInfo information = Imaging.getImageInfo(data);
      if (information == null || information.getWidth() < 1 || information.getHeight() < 1
          || information.getWidth() > MAX_IMAGE_DIMENSION
          || information.getHeight() > MAX_IMAGE_DIMENSION
          || (long) information.getWidth() * information.getHeight() > MAX_IMAGE_PIXELS) {
        throw new IllegalArgumentException("image dimensions exceed the sync v1 limit: " + path);
      }
      if (Imaging.getBufferedImage(data) == null) {
        throw new IllegalArgumentException("image data is not decodable: " + path);
      }
      return new ValidatedImage(mediaType,
          information.getWidth(), information.getHeight());
    } catch (IOException failure) {
      throw new IllegalArgumentException("image data is not decodable: " + path, failure);
    }
  }

  static void validateImageBudgets(Iterable<String> menuSources,
                                   Map<String, ValidatedImage> images) {
    long storedPixels = 0L;
    for (ValidatedImage image : images.values()) {
      storedPixels += image.pixels();
    }
    requirePixelBudget(storedPixels, "stored image entries");

    List<String> usages = new ArrayList<>();
    for (String source : menuSources) {
      collectImageUsages(JsonParser.parseString(source), usages);
    }
    long runtimePixels = 0L;
    long runtimeRows = 0L;
    for (String path : usages) {
      ValidatedImage image = images.get(path);
      if (image == null) {
        throw new IllegalArgumentException("referenced image is absent from sync project: " + path);
      }
      runtimePixels += image.pixels();
      runtimeRows += image.height();
      requirePixelBudget(runtimePixels, "runtime image usages");
      if (runtimeRows > MAX_PROJECT_IMAGE_ROWS) {
        throw new IllegalArgumentException(
            "runtime image usages exceed the sync v1 aggregate row limit");
      }
    }
  }

  private static void requirePixelBudget(long pixels, String label) {
    if (pixels > MAX_PROJECT_IMAGE_PIXELS) {
      throw new IllegalArgumentException(label + " exceed the sync v1 aggregate pixel limit");
    }
  }

  record ValidatedImage(String mediaType, int width, int height) {
    long pixels() {
      return (long) width * height;
    }
  }

  private static void validateMenuSources(Map<String, String> menus) {
    if (menus.isEmpty() || menus.size() > MAX_MENU_COUNT) {
      throw new IllegalArgumentException("menus must contain between 1 and "
          + MAX_MENU_COUNT + " documents");
    }
    for (Map.Entry<String, String> entry : menus.entrySet()) {
      int bytes = entry.getValue().getBytes(StandardCharsets.UTF_8).length;
      if (bytes > MAX_MENU_SOURCE_BYTES) {
        throw new IllegalArgumentException("menu document exceeds "
            + MAX_MENU_SOURCE_BYTES + " bytes: " + entry.getKey());
      }
    }
  }

  private static void validateWarnings(Iterable<String> warnings) {
    int count = 0;
    for (String warning : warnings) {
      count++;
      if (warning == null || warning.length() > MAX_WARNING_CHARACTERS) {
        throw new IllegalArgumentException("sync project warning is invalid");
      }
    }
    if (count > MAX_WARNING_COUNT) {
      throw new IllegalArgumentException("sync project contains too many warnings");
    }
  }

  private static String safeMessage(Throwable failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
  }
}
