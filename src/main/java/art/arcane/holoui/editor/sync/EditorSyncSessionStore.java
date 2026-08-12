package art.arcane.holoui.editor.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class EditorSyncSessionStore implements EditorSyncSessionPersistence {
  private static final Gson GSON = new GsonBuilder()
      .serializeNulls()
      .disableHtmlEscaping()
      .setPrettyPrinting()
      .create();

  private final Path file;
  private final Path dataDirectory;
  private final java.util.logging.Logger logger;
  private final List<JsonElement> quarantinedEntries;
  private final DirectoryForceProbe directoryForceProbe;

  EditorSyncSessionStore(Path pluginDataDirectory, java.util.logging.Logger logger) {
    this(pluginDataDirectory, logger, directory -> {
    });
  }

  EditorSyncSessionStore(Path pluginDataDirectory, java.util.logging.Logger logger,
                         DirectoryForceProbe directoryForceProbe) {
    this.dataDirectory = canonicalDataDirectory(pluginDataDirectory);
    this.file = dataDirectory.resolve("editor-sync-sessions.json");
    this.logger = java.util.Objects.requireNonNull(logger, "logger");
    this.quarantinedEntries = new ArrayList<>();
    this.directoryForceProbe = java.util.Objects.requireNonNull(
        directoryForceProbe, "directoryForceProbe");
  }

  @Override
  public synchronized Map<String, EditorSyncStoredSession> load(Instant now) throws IOException {
    validatePath();
    quarantinedEntries.clear();
    if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
      return Map.of();
    }
    if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("editor sync session store must be a regular non-symbolic file");
    }
    long fileBytes = Files.size(file);
    if (fileBytes > EditorSyncStorageLimits.MAX_STORE_FILE_BYTES) {
      throw new IOException("editor sync session store exceeds "
          + EditorSyncStorageLimits.MAX_STORE_FILE_BYTES + " bytes");
    }
    JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
    if (!root.isJsonObject()) {
      throw new IOException("editor sync session store must be a JSON object");
    }
    JsonObject object = root.getAsJsonObject();
    if (!object.keySet().equals(Set.of("version", "sessions"))) {
      throw new IOException("editor sync session store contains unsupported fields");
    }
    if (EditorSyncJson.requireInt(object, "version") != 1) {
      throw new IOException("unsupported editor sync session store version");
    }
    JsonArray entries = EditorSyncJson.requireArray(object, "sessions");
    Map<String, EditorSyncStoredSession> sessions = new LinkedHashMap<>();
    int entryIndex = 0;
    for (JsonElement entry : entries) {
      try {
        if (!entry.isJsonObject()) {
          throw new IllegalArgumentException("session entry must be an object");
        }
        EditorSyncStoredSession session = parse(entry.getAsJsonObject());
        if (!session.expired(now) && sessions.putIfAbsent(session.sessionId(), session) != null) {
          throw new IllegalArgumentException("duplicate editor sync session id");
        }
      } catch (IllegalArgumentException | DateTimeParseException failure) {
        quarantinedEntries.add(entry.deepCopy());
        logger.warning("Quarantined invalid editor sync session entry " + entryIndex
            + ": " + safeMessage(failure));
      }
      entryIndex++;
    }
    return Map.copyOf(sessions);
  }

  @Override
  public synchronized void quarantine(EditorSyncStoredSession session, String reason) {
    quarantinedEntries.add(serialize(session));
    logger.warning("Quarantined editor sync session "
        + EditorSyncService.abbreviate(session.sessionId()) + ": " + reason);
  }

  @Override
  public synchronized void save(Iterable<EditorSyncStoredSession> sessions) throws IOException {
    validatePath();
    JsonObject root = new JsonObject();
    root.addProperty("version", 1);
    List<EditorSyncStoredSession> ordered = new ArrayList<>();
    sessions.forEach(ordered::add);
    ordered.sort((left, right) -> left.sessionId().compareTo(right.sessionId()));
    JsonArray entries = new JsonArray();
    for (EditorSyncStoredSession session : ordered) {
      entries.add(serialize(session));
    }
    for (JsonElement quarantined : quarantinedEntries) {
      entries.add(quarantined.deepCopy());
    }
    root.add("sessions", entries);
    writeAtomic(GSON.toJson(root) + System.lineSeparator());
  }

  private EditorSyncStoredSession parse(JsonObject object) {
    if (!object.keySet().equals(Set.of("sessionId", "serverToken", "endpoint", "kind",
        "subjectId", "expiresAt", "lastPublicationRevision", "baseProject", "pendingAck"))
        && !object.keySet().equals(Set.of("sessionId", "serverToken", "endpoint", "kind",
        "subjectId", "expiresAt", "lastPublicationRevision", "baseProject"))) {
      throw new IllegalArgumentException("session entry contains missing or unsupported fields");
    }
    return new EditorSyncStoredSession(
        new EditorSyncStoredSession.Capability(
            EditorSyncJson.requireString(object, "sessionId"),
            EditorSyncJson.requireString(object, "serverToken"),
            EditorSyncJson.requireString(object, "endpoint")),
        new EditorSyncStoredSession.Subject(
            EditorSyncKind.parse(EditorSyncJson.requireString(object, "kind")),
            EditorSyncJson.requireString(object, "subjectId")),
        Instant.parse(EditorSyncJson.requireString(object, "expiresAt")),
        object.has("lastPublicationRevision")
            ? object.get("lastPublicationRevision").getAsLong()
            : 0L,
        EditorSyncJson.requireObject(object, "baseProject"),
        object.has("pendingAck") && object.get("pendingAck").isJsonObject()
            ? parsePendingAck(object.getAsJsonObject("pendingAck"))
            : null
    );
  }

  private JsonObject serialize(EditorSyncStoredSession session) {
    JsonObject object = new JsonObject();
    object.addProperty("sessionId", session.sessionId());
    object.addProperty("serverToken", session.serverToken());
    object.addProperty("endpoint", session.endpoint());
    object.addProperty("kind", session.kind().wireName());
    object.addProperty("subjectId", session.subjectId());
    object.addProperty("expiresAt", session.expiresAt().toString());
    object.addProperty("lastPublicationRevision", session.lastPublicationRevision());
    object.add("baseProject", session.baseProject());
    if (session.pendingAck() != null) {
      object.add("pendingAck", serializePendingAck(session.pendingAck()));
    }
    return object;
  }

  private EditorSyncPendingAck parsePendingAck(JsonObject object) {
    Set<String> expected = object.has("serverProject")
        ? Set.of("publicationRevision", "status", "message", "serverProject")
        : Set.of("publicationRevision", "status", "message");
    if (!object.keySet().equals(expected)) {
      throw new IllegalArgumentException("pending acknowledgement contains unsupported fields");
    }
    return new EditorSyncPendingAck(
        object.get("publicationRevision").getAsLong(),
        EditorSyncJson.requireString(object, "status"),
        EditorSyncJson.requireString(object, "message"),
        object.has("serverProject") && object.get("serverProject").isJsonObject()
            ? object.getAsJsonObject("serverProject")
            : null
    );
  }

  private JsonObject serializePendingAck(EditorSyncPendingAck acknowledgement) {
    JsonObject object = new JsonObject();
    object.addProperty("publicationRevision", acknowledgement.publicationRevision());
    object.addProperty("status", acknowledgement.status());
    object.addProperty("message", acknowledgement.message());
    if (acknowledgement.serverProject() != null) {
      object.add("serverProject", acknowledgement.serverProject());
    }
    return object;
  }

  private void writeAtomic(String source) throws IOException {
    byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
    if (bytes.length > EditorSyncStorageLimits.MAX_STORE_FILE_BYTES) {
      throw new IOException("editor sync session store exceeds "
          + EditorSyncStorageLimits.MAX_STORE_FILE_BYTES + " bytes");
    }
    Path parent = file.getParent();
    if (parent == null) {
      throw new IOException("editor sync session store has no parent directory");
    }
    validatePath();
    Path temporary = Files.createTempFile(parent, ".editor-sync-sessions.", ".tmp");
    try {
      try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING)) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
        channel.force(true);
      }
      restrictPermissions(temporary);
      Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      try {
        restrictPermissions(file);
        forceFile(file);
        forceDirectoryAfterReplace(parent);
      } catch (IOException durabilityFailure) {
        throw new EditorSyncPersistenceUncertainException(
            "editor sync session store replacement is visible but its durability is uncertain",
            durabilityFailure);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private void forceFile(Path target) throws IOException {
    try (FileChannel channel = FileChannel.open(target, StandardOpenOption.READ)) {
      channel.force(true);
    }
  }

  private void forceDirectory(Path target) throws IOException {
    directoryForceProbe.beforeForce(target);
    try (FileChannel channel = FileChannel.open(target, StandardOpenOption.READ)) {
      channel.force(true);
    } catch (UnsupportedOperationException ignored) {
    }
  }

  private void forceDirectoryAfterReplace(Path target) throws IOException {
    IOException failure = null;
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        forceDirectory(target);
        return;
      } catch (IOException attemptFailure) {
        if (failure == null) {
          failure = attemptFailure;
        } else {
          failure.addSuppressed(attemptFailure);
        }
      }
    }
    throw java.util.Objects.requireNonNull(failure);
  }

  @FunctionalInterface
  interface DirectoryForceProbe {
    void beforeForce(Path directory) throws IOException;
  }

  private void restrictPermissions(Path target) {
    try {
      Set<PosixFilePermission> permissions = EnumSet.of(
          PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
      Files.setPosixFilePermissions(target, permissions);
    } catch (UnsupportedOperationException ignored) {
    } catch (IOException failure) {
      if (Files.getFileAttributeView(target,
          java.nio.file.attribute.PosixFileAttributeView.class) != null) {
        logger.warning("Unable to restrict editor sync session token permissions: " + failure.getMessage());
      }
    }
  }

  private void validatePath() throws IOException {
    Path root = dataDirectory.getRoot();
    if (root == null) {
      throw new IOException("plugin data directory must be absolute");
    }
    Path current = root;
    for (Path segment : root.relativize(dataDirectory)) {
      current = current.resolve(segment);
      if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)
          || Files.isSymbolicLink(current)
          || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("plugin data path contains a missing, non-directory, or symbolic ancestor: "
            + current);
      }
    }
    Path parent = file.getParent();
    if (parent == null || !parent.equals(dataDirectory)) {
      throw new IOException("editor sync session store escapes the plugin data directory");
    }
    if (Files.isSymbolicLink(file)) {
      throw new IOException("editor sync session store must not be a symbolic link");
    }
  }

  private static String safeMessage(Throwable failure) {
    String message = failure.getMessage();
    if (message == null || message.isBlank()) {
      return failure.getClass().getSimpleName();
    }
    return message.substring(0, Math.min(message.length(), 512));
  }

  private static Path canonicalDataDirectory(Path directory) {
    try {
      return directory.toFile().getCanonicalFile().toPath().toAbsolutePath().normalize();
    } catch (IOException failure) {
      throw new IllegalArgumentException("unable to resolve the plugin data directory", failure);
    }
  }
}
