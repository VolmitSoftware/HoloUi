package art.arcane.holoui.board;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public final class BoardRepository implements BoardStore {
  public static final String DIRECTORY_NAME = "boards";

  private static final String JSON_EXTENSION = ".json";
  private static final Gson GSON = new GsonBuilder()
      .serializeNulls()
      .disableHtmlEscaping()
      .setPrettyPrinting()
      .create();

  private final Path directory;
  private final Map<String, BoardDefinition> boards = new HashMap<>();

  public BoardRepository(File pluginDataDirectory) {
    this(Objects.requireNonNull(pluginDataDirectory, "pluginDataDirectory").toPath());
  }

  public BoardRepository(Path pluginDataDirectory) {
    Path dataDirectory = Objects.requireNonNull(pluginDataDirectory, "pluginDataDirectory").toAbsolutePath().normalize();
    this.directory = dataDirectory.resolve(DIRECTORY_NAME).normalize();
  }

  @Override
  public Path directory() {
    return directory;
  }

  @Override
  public synchronized BoardLoadResult load() throws IOException {
    ensureStorageDirectory();

    Map<String, BoardDefinition> previous = new HashMap<>(boards);
    Map<String, BoardDefinition> loadedBoards = new HashMap<>();
    Map<UUID, String> loadedUuids = new HashMap<>();
    Map<UUID, String> previousUuidOwners = new HashMap<>();
    previous.forEach((id, definition) -> previousUuidOwners.put(definition.uuid(), id));
    Map<String, String> failures = new TreeMap<>();
    int loaded = 0;
    int retained = 0;

    for (Path file : boardFiles()) {
      String relative = relativeName(file);
      String pathId = relative.substring(0, relative.length() - JSON_EXTENSION.length());
      String id;
      try {
        id = BoardIds.canonicalize(pathId);
        if (!pathId.equals(id) || !file.equals(pathForCanonical(id))) {
          throw new IllegalArgumentException("board file path is not canonical");
        }
      } catch (RuntimeException failure) {
        failures.put(relative, message(failure));
        continue;
      }

      try {
        if (Files.isSymbolicLink(file)) {
          throw new IOException("symbolic-link board files are not allowed");
        }
        BoardDefinition definition = read(file);
        if (!definition.id().equals(id)) {
          throw new IllegalArgumentException("file id " + id + " does not match document id " + definition.id());
        }
        validateReload(previous.get(id), definition);
        String previousOwner = previousUuidOwners.get(definition.uuid());
        if (previousOwner != null && !previousOwner.equals(id)) {
          throw new IllegalArgumentException("board uuid is reserved by " + previousOwner);
        }
        String uuidOwner = loadedUuids.putIfAbsent(definition.uuid(), id);
        if (uuidOwner != null) {
          throw new IllegalArgumentException("board uuid is already used by " + uuidOwner);
        }
        loadedBoards.put(id, definition);
        loaded++;
      } catch (IOException | RuntimeException failure) {
        failures.put(relative, message(failure));
        BoardDefinition lastGood = previous.get(id);
        if (lastGood != null && loadedUuids.putIfAbsent(lastGood.uuid(), id) == null) {
          loadedBoards.put(id, lastGood);
          retained++;
        }
      }
    }

    int removed = 0;
    for (String id : previous.keySet()) {
      if (!loadedBoards.containsKey(id)) {
        removed++;
      }
    }

    boards.clear();
    boards.putAll(loadedBoards);
    return new BoardLoadResult(loaded, retained, removed, failures);
  }

  @Override
  public synchronized Optional<BoardDefinition> get(String id) {
    return Optional.ofNullable(boards.get(BoardIds.canonicalize(id)));
  }

  @Override
  public synchronized List<BoardDefinition> list() {
    List<BoardDefinition> snapshot = new ArrayList<>(boards.values());
    snapshot.sort(Comparator.comparing(BoardDefinition::id));
    return List.copyOf(snapshot);
  }

  @Override
  public synchronized BoardDefinition create(BoardDefinition definition) throws IOException {
    BoardDefinition created = Objects.requireNonNull(definition, "definition");
    if (created.revision() != BoardDefinition.INITIAL_REVISION) {
      throw new IllegalArgumentException("new boards must start at revision " + BoardDefinition.INITIAL_REVISION);
    }
    if (boards.containsKey(created.id())) {
      throw new FileAlreadyExistsException(created.id());
    }
    ensureUniqueUuid(created.uuid(), created.id());

    Path target = pathForCanonical(created.id());
    prepareTarget(target);
    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
      throw new FileAlreadyExistsException(target.toString());
    }

    writeAtomic(target, created, false);
    boards.put(created.id(), created);
    return created;
  }

  @Override
  public synchronized BoardDefinition update(String id, long expectedRevision,
                                             UnaryOperator<BoardDefinition> update) throws IOException {
    String canonicalId = BoardIds.canonicalize(id);
    BoardDefinition current = boards.get(canonicalId);
    if (current == null) {
      throw new NoSuchElementException("unknown board: " + canonicalId);
    }
    if (current.revision() != expectedRevision) {
      throw new BoardRevisionConflictException(canonicalId, expectedRevision, current.revision());
    }
    if (current.revision() == BoardDefinition.MAX_SAFE_REVISION) {
      throw new IllegalStateException("board revision overflow: " + canonicalId);
    }

    BoardDefinition changed = Objects.requireNonNull(
        Objects.requireNonNull(update, "update").apply(current),
        "update result");
    validateUpdateIdentity(current, changed);
    BoardDefinition next = changed.withRevision(current.revision() + 1L);

    Path target = pathForCanonical(canonicalId);
    prepareTarget(target);
    writeAtomic(target, next, true);
    boards.put(canonicalId, next);
    return next;
  }

  @Override
  public synchronized BoardDefinition rename(String id, String newId, long expectedRevision) throws IOException {
    String canonicalId = BoardIds.canonicalize(id);
    String canonicalNewId = BoardIds.canonicalize(newId);
    BoardDefinition current = boards.get(canonicalId);
    if (current == null) {
      throw new NoSuchElementException("unknown board: " + canonicalId);
    }
    if (current.revision() != expectedRevision) {
      throw new BoardRevisionConflictException(canonicalId, expectedRevision, current.revision());
    }
    if (canonicalId.equals(canonicalNewId)) {
      throw new IllegalArgumentException("new board id must differ from the current id");
    }
    if (current.revision() == BoardDefinition.MAX_SAFE_REVISION) {
      throw new IllegalStateException("board revision overflow: " + canonicalId);
    }
    if (boards.containsKey(canonicalNewId)) {
      throw new FileAlreadyExistsException(canonicalNewId);
    }

    BoardDefinition renamed = new BoardDefinition(current.schemaVersion(), canonicalNewId, current.uuid(),
        current.revision() + 1L, current.rootMenuId(), current.transform(), current.follow(), current.visibility());
    Path source = pathForCanonical(canonicalId);
    Path target = pathForCanonical(canonicalNewId);
    prepareTarget(source);
    prepareTarget(target);
    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
      throw new FileAlreadyExistsException(target.toString());
    }
    if (Files.isSymbolicLink(source)) {
      throw new IOException("refusing to rename symbolic-link board file: " + source);
    }

    writeAtomic(target, renamed, false);
    boolean sourceDeleted = false;
    try {
      Files.delete(source);
      sourceDeleted = true;
      forceDirectory(source.getParent());
    } catch (IOException failure) {
      try {
        if (sourceDeleted) {
          writeAtomic(source, current, false);
        }
        Files.deleteIfExists(target);
        forceDirectory(target.getParent());
      } catch (IOException rollbackFailure) {
        failure.addSuppressed(rollbackFailure);
      }
      throw failure;
    }

    boards.remove(canonicalId);
    boards.put(canonicalNewId, renamed);
    return renamed;
  }

  @Override
  public synchronized BoardDefinition delete(String id, long expectedRevision) throws IOException {
    String canonicalId = BoardIds.canonicalize(id);
    BoardDefinition current = boards.get(canonicalId);
    if (current == null) {
      throw new NoSuchElementException("unknown board: " + canonicalId);
    }
    if (current.revision() != expectedRevision) {
      throw new BoardRevisionConflictException(canonicalId, expectedRevision, current.revision());
    }

    Path target = pathForCanonical(canonicalId);
    validateAncestors(target);
    if (Files.isSymbolicLink(target)) {
      throw new IOException("refusing to delete symbolic-link board file: " + target);
    }
    Files.deleteIfExists(target);
    forceDirectory(target.getParent());
    boards.remove(canonicalId);
    return current;
  }

  @Override
  public synchronized BoardDefinition publishExternalCreate(BoardDefinition created) throws IOException {
    BoardDefinition requiredCreated = Objects.requireNonNull(created, "created");
    if (requiredCreated.revision() != BoardDefinition.INITIAL_REVISION) {
      throw new IllegalArgumentException(
          "external board creation must start at revision " + BoardDefinition.INITIAL_REVISION);
    }
    if (boards.containsKey(requiredCreated.id())) {
      throw new FileAlreadyExistsException(requiredCreated.id());
    }
    ensureUniqueUuid(requiredCreated.uuid(), requiredCreated.id());
    Path target = pathForCanonical(requiredCreated.id());
    validateAncestors(target);
    BoardDefinition persisted = read(target);
    if (!persisted.equals(requiredCreated)) {
      throw new IOException("external board creation does not match the persisted document");
    }
    boards.put(requiredCreated.id(), requiredCreated);
    return requiredCreated;
  }

  @Override
  public synchronized BoardDefinition recoverExternalCreate(BoardDefinition created) throws IOException {
    BoardDefinition requiredCreated = Objects.requireNonNull(created, "created");
    BoardDefinition current = boards.get(requiredCreated.id());
    if (current == null) {
      return requiredCreated;
    }
    if (!requiredCreated.equals(current)) {
      throw new IOException("cannot recover board creation after an unrelated publication");
    }
    Path target = pathForCanonical(requiredCreated.id());
    validateAncestors(target);
    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("recovered board creation file still exists: " + target);
    }
    boards.remove(requiredCreated.id());
    return requiredCreated;
  }

  @Override
  public synchronized BoardDefinition publishExternal(BoardDefinition expected,
                                                       BoardDefinition updated) throws IOException {
    BoardDefinition requiredExpected = Objects.requireNonNull(expected, "expected");
    BoardDefinition requiredUpdated = Objects.requireNonNull(updated, "updated");
    BoardDefinition current = boards.get(requiredExpected.id());
    if (!requiredExpected.equals(current)) {
      long actualRevision = current == null ? 0L : current.revision();
      throw new BoardRevisionConflictException(requiredExpected.id(),
          requiredExpected.revision(), actualRevision);
    }
    validateUpdateIdentity(current, new BoardDefinition(
        requiredUpdated.schemaVersion(), requiredUpdated.id(), requiredUpdated.uuid(),
        current.revision(), requiredUpdated.rootMenuId(), requiredUpdated.transform(),
        requiredUpdated.follow(), requiredUpdated.visibility()));
    if (requiredUpdated.revision() != current.revision() + 1L) {
      throw new IllegalArgumentException("external board publication must advance revision exactly once");
    }
    Path target = pathForCanonical(current.id());
    validateAncestors(target);
    BoardDefinition persisted = read(target);
    if (!persisted.equals(requiredUpdated)) {
      throw new IOException("external board publication does not match the persisted document");
    }
    boards.put(requiredUpdated.id(), requiredUpdated);
    return requiredUpdated;
  }

  @Override
  public synchronized BoardDefinition recoverExternal(BoardDefinition applied,
                                                       BoardDefinition restored) throws IOException {
    BoardDefinition requiredApplied = Objects.requireNonNull(applied, "applied");
    BoardDefinition requiredRestored = Objects.requireNonNull(restored, "restored");
    BoardDefinition current = boards.get(requiredApplied.id());
    if (requiredRestored.equals(current)) {
      return requiredRestored;
    }
    if (!requiredApplied.equals(current)) {
      throw new IOException("cannot recover board state after an unrelated publication");
    }
    Path target = pathForCanonical(requiredRestored.id());
    validateAncestors(target);
    BoardDefinition persisted = read(target);
    if (!persisted.equals(requiredRestored)) {
      throw new IOException("recovered board file does not match the transaction backup");
    }
    boards.put(requiredRestored.id(), requiredRestored);
    return requiredRestored;
  }

  private List<Path> boardFiles() throws IOException {
    try (Stream<Path> paths = Files.walk(directory)) {
      return paths
          .filter(path -> !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
          .filter(this::hasJsonExtension)
          .sorted(Comparator.comparing(this::relativeName))
          .toList();
    }
  }

  private boolean hasJsonExtension(Path path) {
    Path fileName = path.getFileName();
    return fileName != null && fileName.toString().toLowerCase(Locale.ROOT).endsWith(JSON_EXTENSION);
  }

  private String relativeName(Path path) {
    return directory.relativize(path.toAbsolutePath().normalize()).toString().replace(File.separatorChar, '/');
  }

  private BoardDefinition read(Path file) throws IOException {
    String json = Files.readString(file, StandardCharsets.UTF_8);
    JsonElement element = JsonParser.parseString(json);
    if (!element.isJsonObject()) {
      throw new IllegalArgumentException("board document must be a JSON object");
    }
    BoardDefinition definition = GSON.fromJson(element, BoardDefinition.class);
    if (definition == null) {
      throw new IllegalArgumentException("board document must not be null");
    }
    return definition;
  }

  private void validateReload(BoardDefinition previous, BoardDefinition loaded) {
    if (previous == null) {
      return;
    }
    if (!previous.uuid().equals(loaded.uuid())) {
      throw new IllegalArgumentException("board uuid cannot change across reloads");
    }
    if (loaded.revision() < previous.revision()) {
      throw new IllegalArgumentException("board revision cannot move backwards");
    }
    if (loaded.revision() == previous.revision() && !loaded.equals(previous)) {
      throw new IllegalArgumentException("board content changed without a revision increment");
    }
  }

  private void validateUpdateIdentity(BoardDefinition current, BoardDefinition changed) {
    if (!changed.id().equals(current.id())) {
      throw new IllegalArgumentException("an update cannot change the board id");
    }
    if (!changed.uuid().equals(current.uuid())) {
      throw new IllegalArgumentException("an update cannot change the board uuid");
    }
    if (changed.schemaVersion() != current.schemaVersion()) {
      throw new IllegalArgumentException("an update cannot change schemaVersion");
    }
    if (changed.revision() != current.revision()) {
      throw new IllegalArgumentException("the repository owns revision increments");
    }
  }

  private void ensureUniqueUuid(UUID uuid, String id) {
    for (BoardDefinition board : boards.values()) {
      if (board.uuid().equals(uuid) && !board.id().equals(id)) {
        throw new IllegalArgumentException("board uuid is already used by " + board.id());
      }
    }
  }

  private Path pathForCanonical(String id) {
    Path target = directory.resolve(id + JSON_EXTENSION).normalize();
    if (!target.startsWith(directory)) {
      throw new IllegalArgumentException("board path escapes the board directory: " + id);
    }
    return target;
  }

  private void ensureStorageDirectory() throws IOException {
    Path parent = directory.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("board storage path must be a real directory: " + directory);
      }
      return;
    }
    Files.createDirectory(directory);
    forceDirectory(directory.getParent());
  }

  private void prepareTarget(Path target) throws IOException {
    ensureStorageDirectory();
    Path parent = target.getParent();
    if (parent == null) {
      throw new IOException("board file has no parent directory: " + target);
    }

    Path current = directory;
    for (Path segment : directory.relativize(parent)) {
      current = current.resolve(segment);
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
        if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
          throw new IOException("board path contains a non-directory or symbolic link: " + current);
        }
      } else {
        Files.createDirectory(current);
        forceDirectory(current.getParent());
      }
    }

    if (Files.isSymbolicLink(target)) {
      throw new IOException("board file must not be a symbolic link: " + target);
    }
  }

  private void validateAncestors(Path target) throws IOException {
    Path parent = target.getParent();
    if (parent == null || !parent.startsWith(directory)) {
      throw new IOException("board path escapes the board directory: " + target);
    }
    Path current = directory;
    if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("board storage path must be a real directory: " + current);
    }
    for (Path segment : directory.relativize(parent)) {
      current = current.resolve(segment);
      if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("board path contains a non-directory or symbolic link: " + current);
      }
    }
  }

  private void writeAtomic(Path target, BoardDefinition definition, boolean replaceExisting) throws IOException {
    Path parent = target.getParent();
    if (parent == null) {
      throw new IOException("board file has no parent directory: " + target);
    }
    Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
    try {
      byte[] encoded = (GSON.toJson(definition) + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
      try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING)) {
        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
        channel.force(true);
      }

      if (replaceExisting) {
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } else {
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
      }
      forceDirectory(parent);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private void forceDirectory(Path path) throws IOException {
    if (path == null) {
      return;
    }
    try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
      channel.force(true);
    } catch (UnsupportedOperationException ignored) {
    }
  }

  private static String message(Throwable failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
  }
}
