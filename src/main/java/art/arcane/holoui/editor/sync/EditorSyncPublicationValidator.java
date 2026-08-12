package art.arcane.holoui.editor.sync;

import art.arcane.holoui.board.BoardDefinition;
import art.arcane.holoui.board.BoardIds;
import art.arcane.holoui.config.menu.MenuDocumentParser;
import art.arcane.holoui.config.menu.MenuIds;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class EditorSyncPublicationValidator {
  private static final Gson GSON = new GsonBuilder()
      .serializeNulls()
      .disableHtmlEscaping()
      .create();
  private static final Set<String> ACCEPTED_MEDIA_TYPES = Set.of(
      "image/png", "image/jpeg", "image/gif", "image/webp", "image/bmp");

  ValidatedProject validate(EditorSyncStoredSession session, EditorSyncPublication publication,
                            int maximumBytes) {
    if (!session.baseRevision().equals(publication.baseRevision())) {
      throw new IllegalArgumentException("publication baseRevision does not match the session base");
    }
    JsonObject snapshot = publication.snapshot();
    EditorSyncProject project = EditorSyncProject.validated(snapshot, maximumBytes);
    validateTopLevel(project);
    if (project.kind() != session.kind()) {
      throw new IllegalArgumentException("publication kind does not match the session");
    }
    if (!project.subjectId().equals(session.subjectId())) {
      throw new IllegalArgumentException("publication subject does not match the session");
    }
    Map<String, String> menus = parseMenus(project.json());
    requireImmutableConstraints(session, project.json());
    BoardDefinition board = parseBoard(project, menus);
    Map<String, byte[]> images = parseImages(project.json(), maximumBytes);
    enforceConstraints(session, project.json(), menus, board, images);
    validateReferences(session, board, menus, images);
    return new ValidatedProject(project, menus, board, images);
  }

  ValidatedProject validateBase(EditorSyncStoredSession session, int maximumBytes) {
    return validate(session, new EditorSyncPublication(1L, session.baseRevision(),
        session.baseProject()), maximumBytes);
  }

  private void validateTopLevel(EditorSyncProject project) {
    Set<String> expected = project.kind() == EditorSyncKind.BOARD
        ? Set.of("format", "version", "kind", "subjectId", "menus", "board", "images",
        "constraints", "warnings", "baseRevision")
        : Set.of("format", "version", "kind", "subjectId", "menus", "images",
        "constraints", "warnings", "baseRevision");
    requireExactKeys(project.json(), expected, "sync project");
    JsonArray warnings = EditorSyncJson.requireArray(project.json(), "warnings");
    if (warnings.size() > EditorSyncSnapshotBuilder.MAX_WARNING_COUNT) {
      throw new IllegalArgumentException("sync project contains too many warnings");
    }
    for (JsonElement warning : warnings) {
      if (!warning.isJsonPrimitive() || !warning.getAsJsonPrimitive().isString()
          || warning.getAsString().length() > EditorSyncSnapshotBuilder.MAX_WARNING_CHARACTERS) {
        throw new IllegalArgumentException("sync project warning is invalid");
      }
    }
  }

  private void requireImmutableConstraints(EditorSyncStoredSession session, JsonObject project) {
    JsonObject stored = EditorSyncJson.requireObject(session.baseProject(), "constraints");
    JsonObject published = EditorSyncJson.requireObject(project, "constraints");
    if (!EditorSyncJson.canonical(stored).equals(EditorSyncJson.canonical(published))) {
      throw new IllegalArgumentException("sync publication cannot change its capability constraints");
    }
    validateConstraintShape(session, stored);
  }

  private void validateConstraintShape(EditorSyncStoredSession session, JsonObject constraints) {
    Set<String> expected = session.kind() == EditorSyncKind.BOARD
        ? Set.of("subjectId", "menuIds", "imagePaths", "newMenuPrefix", "newImagePrefix",
        "allowDeletes")
        : Set.of("subjectId", "menuIds", "imagePaths", "newImagePrefix", "allowDeletes");
    requireExactKeys(constraints, expected, "sync constraints");
    JsonElement deletes = constraints.get("allowDeletes");
    if (deletes == null || !deletes.isJsonPrimitive()
        || !deletes.getAsJsonPrimitive().isBoolean() || deletes.getAsBoolean()) {
      throw new IllegalArgumentException("sync v1 constraints must prohibit deletes");
    }
    Set<String> capturedMenus = stringSet(EditorSyncJson.requireArray(constraints, "menuIds"), true);
    Set<String> capturedImages = stringSet(EditorSyncJson.requireArray(constraints, "imagePaths"), false);
    if (!menuIds(session.baseProject()).containsAll(capturedMenus)
        || !imagePaths(session.baseProject()).containsAll(capturedImages)) {
      throw new IllegalArgumentException("sync constraints capture resources outside the base project");
    }
    String newImagePrefix = EditorSyncJson.requireString(constraints, "newImagePrefix");
    String expectedImagePrefix = session.kind() == EditorSyncKind.BOARD
        ? "sync/" + session.subjectId() + "/"
        : "sync/menus/" + session.subjectId() + "/";
    if (!newImagePrefix.equals(expectedImagePrefix)) {
      throw new IllegalArgumentException("sync image creation prefix is invalid");
    }
    if (session.kind() == EditorSyncKind.MENU) {
      if (!capturedMenus.equals(Set.of(MenuIds.require(session.subjectId())))) {
        throw new IllegalArgumentException("menu sync constraints must capture only their subject");
      }
      return;
    }
    JsonObject board = EditorSyncJson.requireObject(session.baseProject(), "board");
    String root = EditorSyncJson.requireString(board, "rootMenuId");
    int separator = root.lastIndexOf('/');
    String expectedMenuPrefix = separator >= 0
        ? root.substring(0, separator + 1)
        : root + "/";
    if (!EditorSyncJson.requireString(constraints, "newMenuPrefix").equals(expectedMenuPrefix)
        || !capturedMenus.contains(root)) {
      throw new IllegalArgumentException("board sync menu creation prefix is invalid");
    }
  }

  private Set<String> reachableMenus(BoardDefinition board, Map<String, String> menus) {
    java.util.ArrayDeque<String> pending = new java.util.ArrayDeque<>();
    Set<String> visited = new HashSet<>();
    pending.add(board.rootMenuId());
    while (!pending.isEmpty()) {
      String id = pending.removeFirst();
      if (!visited.add(id)) {
        continue;
      }
      String source = menus.get(id);
      if (source == null) {
        continue;
      }
      Set<String> targets = new HashSet<>();
      EditorSyncSnapshotBuilder.collectTargets(JsonParser.parseString(source), targets);
      targets.stream().sorted().forEach(pending::addLast);
    }
    return Set.copyOf(visited);
  }

  private void enforceConstraints(EditorSyncStoredSession session, JsonObject project,
                                  Map<String, String> menus, BoardDefinition board,
                                  Map<String, byte[]> images) {
    JsonObject constraints = EditorSyncJson.requireObject(session.baseProject(), "constraints");
    if (!session.subjectId().equals(EditorSyncJson.requireString(constraints, "subjectId"))) {
      throw new IllegalArgumentException("stored sync constraints do not match the session subject");
    }
    Set<String> capturedMenus = stringSet(EditorSyncJson.requireArray(constraints, "menuIds"), true);
    Set<String> capturedImages = stringSet(EditorSyncJson.requireArray(constraints, "imagePaths"), false);
    Set<String> currentMenus = menuIds(session.baseProject());
    Set<String> currentImages = imagePaths(session.baseProject());
    if (!menus.keySet().containsAll(currentMenus)) {
      throw new IllegalArgumentException("sync v1 cannot delete a menu from the current session base");
    }
    if (session.kind() == EditorSyncKind.MENU) {
      if (!menus.keySet().equals(capturedMenus)) {
        throw new IllegalArgumentException("menu sync cannot create additional menu documents");
      }
    } else {
      String newMenuPrefix = EditorSyncJson.requireString(constraints, "newMenuPrefix");
      for (String id : menus.keySet()) {
        if (!capturedMenus.contains(id) && !id.startsWith(newMenuPrefix)) {
          throw new IllegalArgumentException("new menu is outside the session prefix: " + id);
        }
      }
      JsonObject baseBoard = EditorSyncJson.requireObject(session.baseProject(), "board");
      String originalUuid = EditorSyncJson.requireString(baseBoard, "uuid");
      if (board == null || !board.uuid().toString().equals(originalUuid)) {
        throw new IllegalArgumentException("board uuid cannot change during sync");
      }
      long originalRevision = EditorSyncJson.requireSafeLong(baseBoard, "revision");
      long publishedRevision = EditorSyncJson.requireSafeLong(
          EditorSyncJson.requireObject(project, "board"), "revision");
      int originalSchema = EditorSyncJson.requireInt(baseBoard, "schemaVersion");
      if (publishedRevision != originalRevision || board.revision() != originalRevision
          || board.schemaVersion() != originalSchema) {
        throw new IllegalArgumentException("board revision and schemaVersion are server-owned");
      }
    }
    String newImagePrefix = EditorSyncJson.requireString(constraints, "newImagePrefix");
    if (!images.keySet().containsAll(currentImages)) {
      throw new IllegalArgumentException("sync v1 cannot delete an image from the current session base");
    }
    for (String path : images.keySet()) {
      if (!capturedImages.contains(path) && !path.startsWith(newImagePrefix)) {
        throw new IllegalArgumentException("new image is outside the session prefix: " + path);
      }
    }
  }

  private Set<String> stringSet(JsonArray values, boolean menuIds) {
    Set<String> output = new HashSet<>();
    for (JsonElement value : values) {
      if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
        throw new IllegalArgumentException("sync constraints must contain strings");
      }
      String string = value.getAsString();
      output.add(menuIds ? MenuIds.require(string) : EditorSyncSnapshotBuilder.normalizeRelative(string));
    }
    return Set.copyOf(output);
  }

  private Map<String, String> parseMenus(JsonObject project) {
    JsonArray values = EditorSyncJson.requireArray(project, "menus");
    if (values.isEmpty() || values.size() > EditorSyncSnapshotBuilder.MAX_MENU_COUNT) {
      throw new IllegalArgumentException("menus must contain between 1 and "
          + EditorSyncSnapshotBuilder.MAX_MENU_COUNT + " documents");
    }
    Map<String, String> menus = new LinkedHashMap<>();
    for (JsonElement value : values) {
      if (!value.isJsonObject()) {
        throw new IllegalArgumentException("menu entry must be an object");
      }
      JsonObject entry = value.getAsJsonObject();
      requireExactKeys(entry, Set.of("id", "json"), "menu entry");
      String id = MenuIds.require(EditorSyncJson.requireString(entry, "id"));
      String source = EditorSyncJson.requireString(entry, "json");
      if (source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
          > EditorSyncSnapshotBuilder.MAX_MENU_SOURCE_BYTES) {
        throw new IllegalArgumentException("menu document exceeds "
            + EditorSyncSnapshotBuilder.MAX_MENU_SOURCE_BYTES + " bytes: " + id);
      }
      if (menus.putIfAbsent(id, source) != null) {
        throw new IllegalArgumentException("duplicate menu id: " + id);
      }
      MenuDocumentParser.parse(id, source);
    }
    return Map.copyOf(menus);
  }

  private BoardDefinition parseBoard(EditorSyncProject project, Map<String, String> menus) {
    if (project.kind() == EditorSyncKind.MENU) {
      if (menus.size() != 1 || !menus.containsKey(MenuIds.require(project.subjectId()))) {
        throw new IllegalArgumentException("menu sync sessions may publish only their subject menu");
      }
      if (project.json().has("board") && !project.json().get("board").isJsonNull()) {
        throw new IllegalArgumentException("menu sync sessions cannot publish a board");
      }
      return null;
    }

    JsonObject boardJson = EditorSyncJson.requireObject(project.json(), "board");
    validateBoardShape(boardJson);
    BoardDefinition board = GSON.fromJson(boardJson, BoardDefinition.class);
    if (board == null) {
      throw new IllegalArgumentException("board document must not be null");
    }
    String subjectId = BoardIds.canonicalize(project.subjectId());
    if (!board.id().equals(subjectId)) {
      throw new IllegalArgumentException("board id cannot change during sync");
    }
    if (!menus.containsKey(board.rootMenuId())) {
      throw new IllegalArgumentException("board root menu is absent from the publication");
    }
    return board;
  }

  private Map<String, byte[]> parseImages(JsonObject project, int maximumBytes) {
    JsonArray values = EditorSyncJson.requireArray(project, "images");
    if (values.size() > EditorSyncSnapshotBuilder.MAX_IMAGE_COUNT) {
      throw new IllegalArgumentException("images exceed "
          + EditorSyncSnapshotBuilder.MAX_IMAGE_COUNT + " assets");
    }
    Map<String, byte[]> images = new LinkedHashMap<>();
    int totalBytes = 0;
    Map<String, EditorSyncSnapshotBuilder.ValidatedImage> validatedImages = new LinkedHashMap<>();
    for (JsonElement value : values) {
      if (!value.isJsonObject()) {
        throw new IllegalArgumentException("image entry must be an object");
      }
      JsonObject entry = value.getAsJsonObject();
      requireExactKeys(entry, Set.of("path", "data"), "image entry");
      String path = EditorSyncSnapshotBuilder.normalizeRelative(
          EditorSyncJson.requireString(entry, "path"));
      if (images.containsKey(path)) {
        throw new IllegalArgumentException("duplicate image path: " + path);
      }
      DecodedImage decoded = decodeDataUrl(EditorSyncJson.requireString(entry, "data"));
      byte[] data = decoded.data();
      if (data.length > EditorSyncSnapshotBuilder.MAX_IMAGE_BYTES) {
        throw new IllegalArgumentException("image exceeds the sync v1 per-asset limit: " + path);
      }
      totalBytes += data.length;
      if (totalBytes > maximumBytes) {
        throw new EditorSyncProjectTooLargeException(totalBytes, maximumBytes);
      }
      validatedImages.put(path, validateImage(path, decoded.mediaType(), data));
      images.put(path, data);
    }
    EditorSyncSnapshotBuilder.validateImageBudgets(List.of(), validatedImages);
    return Map.copyOf(images);
  }

  private void validateReferences(EditorSyncStoredSession session, BoardDefinition board,
                                  Map<String, String> menus, Map<String, byte[]> images) {
    Set<String> referenced = new HashSet<>();
    for (String source : menus.values()) {
      EditorSyncSnapshotBuilder.collectImagePaths(JsonParser.parseString(source), referenced);
    }
    for (String path : referenced) {
      if (!images.containsKey(path)) {
        throw new IllegalArgumentException("referenced image is absent from publication: " + path);
      }
    }
    Map<String, EditorSyncSnapshotBuilder.ValidatedImage> validatedImages = new LinkedHashMap<>();
    for (Map.Entry<String, byte[]> entry : images.entrySet()) {
      validatedImages.put(entry.getKey(),
          EditorSyncSnapshotBuilder.validateImage(entry.getKey(), entry.getValue()));
    }
    EditorSyncSnapshotBuilder.validateImageBudgets(menus.values(), validatedImages);
    Set<String> currentImages = imagePaths(session.baseProject());
    for (String path : images.keySet()) {
      if (!referenced.contains(path) && !currentImages.contains(path)) {
        throw new IllegalArgumentException("new unreferenced image is not allowed: " + path);
      }
    }
    if (board != null) {
      Set<String> reachable = reachableMenus(board, menus);
      Set<String> currentMenus = menuIds(session.baseProject());
      for (String id : menus.keySet()) {
        if (!reachable.contains(id) && !currentMenus.contains(id)) {
          throw new IllegalArgumentException("new menu is unreachable from the board root: " + id);
        }
      }
    }
  }

  private DecodedImage decodeDataUrl(String value) {
    if (!value.startsWith("data:")) {
      throw new IllegalArgumentException("image data must be a data URL");
    }
    int separator = value.indexOf(",");
    if (separator < 6 || separator >= value.length() - 1) {
      throw new IllegalArgumentException("image data URL is malformed");
    }
    String descriptor = value.substring(5, separator);
    String[] parts = descriptor.split(";", -1);
    String mediaType = parts[0].toLowerCase(Locale.ROOT);
    if (!ACCEPTED_MEDIA_TYPES.contains(mediaType) || parts.length != 2
        || !parts[1].equalsIgnoreCase("base64")) {
      throw new IllegalArgumentException("image data URL must use an accepted base64 media type");
    }
    try {
      return new DecodedImage(mediaType,
          Base64.getDecoder().decode(value.substring(separator + 1)));
    } catch (IllegalArgumentException failure) {
      throw new IllegalArgumentException("image data URL contains invalid base64", failure);
    }
  }

  private EditorSyncSnapshotBuilder.ValidatedImage validateImage(
      String path, String declaredMediaType, byte[] data) {
    String detected = EditorSyncSnapshotBuilder.detectMediaType(data);
    if (!detected.equals(declaredMediaType)) {
      throw new IllegalArgumentException("image media type does not match its bytes: " + path);
    }
    return EditorSyncSnapshotBuilder.validateImage(path, data);
  }

  private void validateBoardShape(JsonObject board) {
    requireExactKeys(board, Set.of("schemaVersion", "id", "uuid", "revision", "rootMenuId",
        "transform", "follow", "visibility"), "board");
    EditorSyncJson.requireInt(board, "schemaVersion");
    EditorSyncJson.requireSafeLong(board, "revision");
    requireExactKeys(EditorSyncJson.requireObject(board, "transform"),
        Set.of("worldKey", "worldUuid", "x", "y", "z", "yaw", "pitch", "roll", "scale"),
        "board transform");
    requireExactKeys(EditorSyncJson.requireObject(board, "follow"),
        Set.of("mode", "targetPlayerUuid", "rotation"), "board follow");
    requireExactKeys(EditorSyncJson.requireObject(board, "visibility"),
        Set.of("mode", "viewPermission", "interactPermission", "viewRange", "interactionRange"),
        "board visibility");
  }

  private Set<String> menuIds(JsonObject project) {
    Set<String> ids = new HashSet<>();
    for (JsonElement value : EditorSyncJson.requireArray(project, "menus")) {
      if (!value.isJsonObject()) {
        throw new IllegalArgumentException("base project menu entry must be an object");
      }
      if (!ids.add(MenuIds.require(EditorSyncJson.requireString(value.getAsJsonObject(), "id")))) {
        throw new IllegalArgumentException("base project contains a duplicate menu id");
      }
    }
    return Set.copyOf(ids);
  }

  private Set<String> imagePaths(JsonObject project) {
    Set<String> paths = new HashSet<>();
    for (JsonElement value : EditorSyncJson.requireArray(project, "images")) {
      if (!value.isJsonObject()) {
        throw new IllegalArgumentException("base project image entry must be an object");
      }
      String path = EditorSyncSnapshotBuilder.normalizeRelative(
          EditorSyncJson.requireString(value.getAsJsonObject(), "path"));
      if (!paths.add(path)) {
        throw new IllegalArgumentException("base project contains a duplicate image path");
      }
    }
    return Set.copyOf(paths);
  }

  private void requireExactKeys(JsonObject object, Set<String> expected, String label) {
    if (!object.keySet().equals(expected)) {
      throw new IllegalArgumentException(label + " contains missing or unsupported fields");
    }
  }

  record ValidatedProject(EditorSyncProject project, Map<String, String> menus,
                          BoardDefinition board, Map<String, byte[]> images) {
    ValidatedProject {
      project = Objects.requireNonNull(project, "project");
      menus = Map.copyOf(menus);
      images = Map.copyOf(images);
    }
  }

  private record DecodedImage(String mediaType, byte[] data) {
  }
}
