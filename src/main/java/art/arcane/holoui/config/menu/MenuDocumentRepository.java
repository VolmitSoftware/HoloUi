package art.arcane.holoui.config.menu;

import art.arcane.volmlib.util.bukkit.json.BukkitJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.UnaryOperator;

final class MenuDocumentRepository {
  private static final String JSON_EXTENSION = ".json";

  private final Path directory;

  MenuDocumentRepository(File pluginDataDirectory) {
    this(Objects.requireNonNull(pluginDataDirectory, "pluginDataDirectory").toPath());
  }

  MenuDocumentRepository(Path pluginDataDirectory) {
    Path dataDirectory = Objects.requireNonNull(pluginDataDirectory, "pluginDataDirectory")
        .toAbsolutePath().normalize();
    this.directory = dataDirectory.resolve("menus").normalize();
  }

  Path directory() {
    return directory;
  }

  MenuDocument mutate(String id, String expectedRevision,
                      UnaryOperator<JsonObject> mutation) throws IOException {
    String menuId = MenuIds.require(id);
    String requiredRevision = Objects.requireNonNull(expectedRevision, "expectedRevision");
    UnaryOperator<JsonObject> requiredMutation = Objects.requireNonNull(mutation, "mutation");
    Path target = path(menuId);
    byte[] originalBytes = readRegularFile(target);
    String originalSource = new String(originalBytes, StandardCharsets.UTF_8);
    String actualRevision = MenuDocument.revisionOf(originalSource);
    requireRevision(menuId, requiredRevision, actualRevision);

    JsonElement parsed = JsonParser.parseString(originalSource);
    if (!parsed.isJsonObject()) {
      throw new IllegalArgumentException("menu document must be a JSON object");
    }
    JsonObject changed = Objects.requireNonNull(
        requiredMutation.apply(parsed.getAsJsonObject().deepCopy()), "menu mutation result");
    String changedSource = BukkitJson.GSON.toJson(changed) + System.lineSeparator();
    MenuDocument validated = MenuDocumentParser.parse(menuId, changedSource);

    if (changed.equals(parsed)) {
      return MenuDocumentParser.parse(menuId, originalSource);
    }

    prepareParent(target);
    writeReplacement(menuId, target, originalBytes, requiredRevision, changedSource);
    return readPersisted(menuId, target, validated.revision());
  }

  MenuDocument copy(String sourceId, String expectedRevision, String targetId) throws IOException {
    String sourceMenuId = MenuIds.require(sourceId);
    String targetMenuId = MenuIds.require(targetId);
    if (sourceMenuId.equals(targetMenuId)) {
      throw new IllegalArgumentException("new menu id must differ from the source menu id");
    }
    String requiredRevision = Objects.requireNonNull(expectedRevision, "expectedRevision");
    Path source = path(sourceMenuId);
    Path target = path(targetMenuId);
    byte[] originalBytes = readRegularFile(source);
    String originalSource = new String(originalBytes, StandardCharsets.UTF_8);
    String actualRevision = MenuDocument.revisionOf(originalSource);
    requireRevision(sourceMenuId, requiredRevision, actualRevision);
    JsonElement parsed = JsonParser.parseString(originalSource);
    if (!parsed.isJsonObject()) {
      throw new IllegalArgumentException("menu document must be a JSON object");
    }
    String copiedSource = BukkitJson.GSON.toJson(parsed) + System.lineSeparator();
    MenuDocument validated = MenuDocumentParser.parse(targetMenuId, copiedSource);

    prepareParent(target);
    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
      throw new FileAlreadyExistsException(targetMenuId);
    }
    Path temporary = writeTemporary(target, copiedSource);
    try {
      requireSourceRevision(sourceMenuId, source, requiredRevision);
      if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
        throw new FileAlreadyExistsException(targetMenuId);
      }
      publishNewFile(temporary, target);
      forceDirectory(target.getParent());
    } finally {
      Files.deleteIfExists(temporary);
    }
    return readPersisted(targetMenuId, target, validated.revision());
  }

  MenuDocument create(String id, String source) throws IOException {
    String menuId = MenuIds.require(id);
    if (source == null || source.isEmpty()) {
      throw new IllegalArgumentException("menu document must not be empty");
    }
    String normalizedSource = source.endsWith(System.lineSeparator())
        ? source
        : source + System.lineSeparator();
    MenuDocument validated = MenuDocumentParser.parse(menuId, normalizedSource);
    Path target = path(menuId);

    prepareParent(target);
    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
      throw new FileAlreadyExistsException(menuId);
    }
    Path temporary = writeTemporary(target, normalizedSource);
    try {
      if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
        throw new FileAlreadyExistsException(menuId);
      }
      publishNewFile(temporary, target);
      forceDirectory(target.getParent());
    } finally {
      Files.deleteIfExists(temporary);
    }
    return readPersisted(menuId, target, validated.revision());
  }

  private void writeReplacement(String menuId, Path target, byte[] originalBytes,
                                String expectedRevision, String source) throws IOException {
    Path temporary = writeTemporary(target, source);
    try {
      byte[] latestBytes = readRegularFile(target);
      String latestRevision = MenuDocument.revisionOf(new String(latestBytes, StandardCharsets.UTF_8));
      requireRevision(menuId, expectedRevision, latestRevision);
      if (!Arrays.equals(originalBytes, latestBytes)) {
        throw new MenuRevisionConflictException(menuId, expectedRevision, latestRevision);
      }
      Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      forceDirectory(target.getParent());
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private Path writeTemporary(Path target, String source) throws IOException {
    Path parent = target.getParent();
    if (parent == null) {
      throw new IOException("menu file has no parent directory: " + target);
    }
    Path temporary = Files.createTempFile(parent, "." + target.getFileName() + ".", ".tmp");
    boolean success = false;
    try {
      byte[] encoded = source.getBytes(StandardCharsets.UTF_8);
      try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING)) {
        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        while (buffer.hasRemaining()) {
          channel.write(buffer);
        }
        channel.force(true);
      }
      success = true;
      return temporary;
    } finally {
      if (!success) {
        Files.deleteIfExists(temporary);
      }
    }
  }

  private MenuDocument readPersisted(String menuId, Path target, String writtenRevision) throws IOException {
    byte[] persistedBytes = readRegularFile(target);
    String persistedSource = new String(persistedBytes, StandardCharsets.UTF_8);
    String persistedRevision = MenuDocument.revisionOf(persistedSource);
    if (!persistedRevision.equals(writtenRevision)) {
      throw new MenuRevisionConflictException(menuId, writtenRevision, persistedRevision);
    }
    return MenuDocumentParser.parse(menuId, persistedSource);
  }

  private void publishNewFile(Path temporary, Path target) throws IOException {
    try {
      Files.createLink(target, temporary);
    } catch (UnsupportedOperationException failure) {
      throw new IOException("menu storage does not support atomic no-clobber file creation", failure);
    }
  }

  private void requireSourceRevision(String menuId, Path source, String expectedRevision) throws IOException {
    String actualSource = new String(readRegularFile(source), StandardCharsets.UTF_8);
    requireRevision(menuId, expectedRevision, MenuDocument.revisionOf(actualSource));
  }

  private byte[] readRegularFile(Path path) throws IOException {
    ensureDirectory();
    validateAncestors(path);
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      throw new NoSuchFileException(path.toString());
    }
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      throw new IOException("menu path must be a regular non-symbolic file: " + path);
    }
    return Files.readAllBytes(path);
  }

  private void prepareParent(Path target) throws IOException {
    ensureDirectory();
    Path parent = target.getParent();
    if (parent == null) {
      throw new IOException("menu file has no parent directory: " + target);
    }
    Path current = directory;
    for (Path segment : directory.relativize(parent)) {
      current = current.resolve(segment);
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
        if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
          throw new IOException("menu path contains a non-directory or symbolic link: " + current);
        }
      } else {
        Files.createDirectory(current);
        forceDirectory(current.getParent());
      }
    }
    if (Files.isSymbolicLink(target)) {
      throw new IOException("menu file must not be a symbolic link: " + target);
    }
  }

  private void validateAncestors(Path target) throws IOException {
    Path parent = target.getParent();
    if (parent == null || !parent.startsWith(directory)) {
      throw new IOException("menu path escapes the menu directory: " + target);
    }
    Path current = directory;
    for (Path segment : directory.relativize(parent)) {
      current = current.resolve(segment);
      if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("menu path contains a non-directory or symbolic link: " + current);
      }
    }
  }

  private void ensureDirectory() throws IOException {
    Path parent = directory.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException("menu storage path must be a real directory: " + directory);
      }
      return;
    }
    Files.createDirectory(directory);
    forceDirectory(directory.getParent());
  }

  private Path path(String id) {
    Path target = directory.resolve(id + JSON_EXTENSION).normalize();
    if (!target.startsWith(directory)) {
      throw new IllegalArgumentException("menu path escapes the menu directory: " + id);
    }
    return target;
  }

  private static void requireRevision(String menuId, String expectedRevision, String actualRevision) {
    if (!expectedRevision.equals(actualRevision)) {
      throw new MenuRevisionConflictException(menuId, expectedRevision, actualRevision);
    }
  }

  private static void forceDirectory(Path path) throws IOException {
    if (path == null) {
      return;
    }
    try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
      channel.force(true);
    } catch (UnsupportedOperationException ignored) {
    }
  }
}
