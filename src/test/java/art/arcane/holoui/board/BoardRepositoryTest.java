package art.arcane.holoui.board;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class BoardRepositoryTest {
  private static final UUID WORLD_UUID = UUID.fromString("00000000-0000-0000-0000-000000000111");

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void nestedBoardsRoundTripWithoutResolvingTheWorld() throws IOException {
    File pluginData = temp.newFolder("plugin");
    BoardRepository repository = new BoardRepository(pluginData);
    BoardLoadResult empty = repository.load();
    BoardDefinition created = repository.create(board("Lobbies/Shop/Main", "example:not_loaded"));

    assertTrue(empty.successful());
    assertEquals("lobbies/shop/main", created.id());
    assertTrue(pluginData.toPath().resolve("boards/lobbies/shop/main.json").toFile().isFile());
    assertEquals(List.of(created), repository.list());

    BoardRepository reopened = new BoardRepository(pluginData);
    BoardLoadResult loaded = reopened.load();
    BoardDefinition restored = reopened.get("LOBBIES/SHOP/MAIN").orElseThrow();

    assertEquals(1, loaded.loaded());
    assertTrue(loaded.successful());
    assertEquals(created, restored);
    assertEquals(created.uuid(), restored.uuid());
  }

  @Test
  public void pathTraversalNeverCreatesAFileOutsideBoardStorage() throws IOException {
    File pluginData = temp.newFolder("paths");
    BoardRepository repository = new BoardRepository(pluginData);
    repository.load();

    String[] invalid = {"../outside", "nested/../../outside", "/absolute", "nested\\outside"};
    for (String id : invalid) {
      assertThrows(id, IllegalArgumentException.class,
          () -> BoardDefinition.create(id, "Shop", transform("example:world")));
      assertThrows(id, IllegalArgumentException.class, () -> repository.get(id));
    }

    assertFalse(pluginData.toPath().resolve("outside.json").toFile().exists());
    assertFalse(pluginData.toPath().resolveSibling("outside.json").toFile().exists());
  }

  @Test
  public void symbolicNestedDirectoriesCannotEscapeBoardStorage() throws IOException {
    File pluginData = temp.newFolder("symlink-plugin");
    Path outside = temp.newFolder("symlink-outside").toPath();
    BoardRepository repository = new BoardRepository(pluginData);
    repository.load();
    Path link = repository.directory().resolve("linked");
    try {
      Files.createSymbolicLink(link, outside);
    } catch (IOException | UnsupportedOperationException failure) {
      Assume.assumeNoException(failure);
    }

    assertThrows(IOException.class, () -> repository.create(board("linked/escape", "example:world")));
    assertFalse(outside.resolve("escape.json").toFile().exists());
  }

  @Test
  public void deletionCannotEscapeThroughAReplacedAncestorDirectory() throws IOException {
    File pluginData = temp.newFolder("delete-symlink-plugin");
    Path outside = temp.newFolder("delete-symlink-outside").toPath();
    BoardRepository repository = new BoardRepository(pluginData);
    repository.load();
    BoardDefinition created = repository.create(board("linked/escape", "example:world"));
    Path linkedDirectory = repository.directory().resolve("linked");
    Path outsideFile = outside.resolve("escape.json");
    Files.writeString(outsideFile, "outside", StandardCharsets.UTF_8);
    Files.delete(linkedDirectory.resolve("escape.json"));
    Files.delete(linkedDirectory);
    try {
      Files.createSymbolicLink(linkedDirectory, outside);
    } catch (IOException | UnsupportedOperationException failure) {
      Assume.assumeNoException(failure);
    }

    assertThrows(IOException.class, () -> repository.delete(created.id(), created.revision()));
    assertEquals("outside", Files.readString(outsideFile, StandardCharsets.UTF_8));
    assertEquals(created, repository.get(created.id()).orElseThrow());
  }

  @Test
  public void malformedReloadRetainsTheLastGoodDefinition() throws IOException {
    File pluginData = temp.newFolder("last-good");
    BoardRepository repository = new BoardRepository(pluginData);
    repository.load();
    BoardDefinition created = repository.create(board("spawn/info", "example:world"));
    Path file = repository.directory().resolve("spawn/info.json");
    String goodJson = Files.readString(file, StandardCharsets.UTF_8);

    Files.writeString(file, "{not-json", StandardCharsets.UTF_8);
    BoardLoadResult failed = repository.load();

    assertEquals(0, failed.loaded());
    assertEquals(1, failed.retained());
    assertEquals(1, failed.failures().size());
    assertEquals(created, repository.get(created.id()).orElseThrow());

    BoardRepository coldStart = new BoardRepository(pluginData);
    BoardLoadResult coldFailure = coldStart.load();
    assertEquals(0, coldFailure.retained());
    assertTrue(coldStart.list().isEmpty());

    Files.writeString(file, goodJson, StandardCharsets.UTF_8);
    BoardLoadResult recovered = repository.load();
    assertEquals(1, recovered.loaded());
    assertEquals(0, recovered.retained());
    assertTrue(recovered.successful());
  }

  @Test
  public void newlyDiscoveredFilesCannotClaimAnExistingBoardUuid() throws IOException {
    File pluginData = temp.newFolder("uuid-owner");
    BoardRepository repository = new BoardRepository(pluginData);
    repository.load();
    BoardDefinition owner = repository.create(board("z/owner", "example:world"));
    Path ownerFile = repository.directory().resolve("z/owner.json");
    Path hijackFile = repository.directory().resolve("a/hijack.json");
    Files.createDirectories(hijackFile.getParent());
    String hijackJson = Files.readString(ownerFile, StandardCharsets.UTF_8)
        .replace("\"id\": \"z/owner\"", "\"id\": \"a/hijack\"");
    Files.writeString(hijackFile, hijackJson, StandardCharsets.UTF_8);

    BoardLoadResult loaded = repository.load();

    assertEquals(1, loaded.loaded());
    assertEquals(1, loaded.failures().size());
    assertTrue(loaded.failures().containsKey("a/hijack.json"));
    assertEquals(owner, repository.get(owner.id()).orElseThrow());
    assertTrue(repository.get("a/hijack").isEmpty());
  }

  @Test
  public void updateIsAtomicAndRejectsStaleRevisions() throws IOException {
    File pluginData = temp.newFolder("update");
    BoardRepository repository = new BoardRepository(pluginData);
    repository.load();
    BoardDefinition created = repository.create(board("spawn/main", "example:world"));
    BoardTransform moved = BoardTransform.at("example:world", WORLD_UUID, 20.0D, 80.0D, -4.0D, 90.0D);

    BoardDefinition updated = repository.update(created.id(), created.revision(), board -> board.withTransform(moved));

    assertEquals(created.revision() + 1L, updated.revision());
    assertEquals(created.uuid(), updated.uuid());
    assertEquals(moved, updated.transform());
    try (Stream<Path> files = Files.walk(repository.directory())) {
      assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
    }

    AtomicBoolean invoked = new AtomicBoolean(false);
    BoardRevisionConflictException conflict = assertThrows(BoardRevisionConflictException.class,
        () -> repository.update(created.id(), created.revision(), board -> {
          invoked.set(true);
          return board.withRootMenu("Other");
        }));
    assertFalse(invoked.get());
    assertEquals(created.revision(), conflict.expectedRevision());
    assertEquals(updated.revision(), conflict.actualRevision());
    assertEquals(updated, repository.get(created.id()).orElseThrow());

    BoardRepository reopened = new BoardRepository(pluginData);
    reopened.load();
    assertEquals(updated, reopened.get(created.id()).orElseThrow());
  }

  @Test
  public void updateCannotReplaceStableIdentityOrRevision() throws IOException {
    File pluginData = temp.newFolder("identity");
    BoardRepository repository = new BoardRepository(pluginData);
    repository.load();
    BoardDefinition created = repository.create(board("spawn/main", "example:world"));

    assertThrows(IllegalArgumentException.class, () -> repository.update(created.id(), created.revision(), board ->
        new BoardDefinition(board.schemaVersion(), "spawn/other", board.uuid(), board.revision(), board.rootMenuId(),
            board.transform(), board.follow(), board.visibility())));
    assertThrows(IllegalArgumentException.class, () -> repository.update(created.id(), created.revision(), board ->
        new BoardDefinition(board.schemaVersion(), board.id(), UUID.randomUUID(), board.revision(), board.rootMenuId(),
            board.transform(), board.follow(), board.visibility())));
    assertThrows(IllegalArgumentException.class, () -> repository.update(created.id(), created.revision(), board ->
        new BoardDefinition(board.schemaVersion(), board.id(), board.uuid(), board.revision() + 1L, board.rootMenuId(),
            board.transform(), board.follow(), board.visibility())));
    assertEquals(created, repository.get(created.id()).orElseThrow());
  }

  @Test
  public void missingWorldIdentityIsRejectedButAnUnavailableWorldIsNot() throws IOException {
    File pluginData = temp.newFolder("missing-world");
    BoardRepository repository = new BoardRepository(pluginData);
    repository.load();
    Files.writeString(repository.directory().resolve("missing-uuid.json"), missingWorldJson(
        "missing-uuid", "00000000-0000-0000-0000-000000000301", "\"worldKey\": \"example:not_loaded\""),
        StandardCharsets.UTF_8);
    Files.writeString(repository.directory().resolve("missing-key.json"), missingWorldJson(
        "missing-key", "00000000-0000-0000-0000-000000000302", "\"worldUuid\": \"" + WORLD_UUID + "\""),
        StandardCharsets.UTF_8);

    BoardLoadResult failed = repository.load();
    assertEquals(2, failed.failures().size());
    assertTrue(repository.list().isEmpty());

    BoardDefinition unavailableWorld = repository.create(board("valid", "example:not_loaded"));
    BoardRepository reopened = new BoardRepository(pluginData);
    BoardLoadResult reloaded = reopened.load();
    assertEquals(1, reloaded.loaded());
    assertEquals(unavailableWorld, reopened.get("valid").orElseThrow());
  }

  @Test
  public void deletionIsRevisionCheckedAndMissingFilesAreRemovedOnLoad() throws IOException {
    File pluginData = temp.newFolder("delete");
    BoardRepository repository = new BoardRepository(pluginData);
    repository.load();
    BoardDefinition first = repository.create(board("a/first", "example:world"));
    BoardDefinition second = repository.create(board("b/second", "example:world"));

    assertThrows(BoardRevisionConflictException.class,
        () -> repository.delete(first.id(), first.revision() + 1L));
    assertTrue(repository.get(first.id()).isPresent());
    assertEquals(first, repository.delete(first.id(), first.revision()));
    assertFalse(repository.directory().resolve("a/first.json").toFile().exists());

    Files.delete(repository.directory().resolve("b/second.json"));
    BoardLoadResult reloaded = repository.load();
    assertEquals(1, reloaded.removed());
    assertTrue(repository.list().isEmpty());
    assertFalse(repository.get(second.id()).isPresent());
  }

  @Test
  public void renamePreservesIdentityAndContentWhileAdvancingRevision() throws IOException {
    File pluginData = temp.newFolder("rename");
    BoardRepository repository = new BoardRepository(pluginData);
    repository.load();
    BoardDefinition created = repository.create(board("spawn/main", "example:world"));

    BoardDefinition renamed = repository.rename(created.id(), "lobbies/primary", created.revision());

    assertEquals("lobbies/primary", renamed.id());
    assertEquals(created.uuid(), renamed.uuid());
    assertEquals(created.revision() + 1L, renamed.revision());
    assertEquals(created.rootMenuId(), renamed.rootMenuId());
    assertEquals(created.transform(), renamed.transform());
    assertFalse(repository.get(created.id()).isPresent());
    assertEquals(renamed, repository.get(renamed.id()).orElseThrow());
    assertFalse(repository.directory().resolve("spawn/main.json").toFile().exists());
    assertTrue(repository.directory().resolve("lobbies/primary.json").toFile().isFile());

    BoardRepository reopened = new BoardRepository(pluginData);
    BoardLoadResult loaded = reopened.load();
    assertTrue(loaded.successful());
    assertEquals(List.of(renamed), reopened.list());
  }

  @Test
  public void renameRejectsConflictsAndRollsBackWhenTheSourceCannotBeDeleted() throws IOException {
    File pluginData = temp.newFolder("rename-rollback");
    BoardRepository repository = new BoardRepository(pluginData);
    repository.load();
    BoardDefinition source = repository.create(board("source", "example:world"));
    BoardDefinition occupied = repository.create(board("occupied", "example:world"));

    assertThrows(BoardRevisionConflictException.class,
        () -> repository.rename(source.id(), "renamed", source.revision() + 1L));
    assertThrows(FileAlreadyExistsException.class,
        () -> repository.rename(source.id(), occupied.id(), source.revision()));
    assertThrows(IllegalArgumentException.class,
        () -> repository.rename(source.id(), source.id(), source.revision()));

    Path sourceFile = repository.directory().resolve("source.json");
    Files.delete(sourceFile);
    Files.createDirectory(sourceFile);
    Files.writeString(sourceFile.resolve("blocker"), "not empty", StandardCharsets.UTF_8);

    assertThrows(IOException.class,
        () -> repository.rename(source.id(), "renamed", source.revision()));
    assertEquals(source, repository.get(source.id()).orElseThrow());
    assertFalse(repository.get("renamed").isPresent());
    assertFalse(repository.directory().resolve("renamed.json").toFile().exists());
  }

  private static String missingWorldJson(String id, String uuid, String worldIdentity) {
    return """
        {
          "schemaVersion": 1,
          "id": "%s",
          "uuid": "%s",
          "revision": 1,
          "rootMenuId": "Shop",
          "transform": {
            %s,
            "x": 0.0,
            "y": 64.0,
            "z": 0.0,
            "yaw": 0.0,
            "pitch": 0.0,
            "roll": 0.0,
            "scale": 1.0
          },
          "follow": {"mode": "none", "targetPlayerUuid": null, "rotation": "fixed"},
          "visibility": {
            "mode": "public",
            "viewPermission": null,
            "interactPermission": null,
            "viewRange": 64.0,
            "interactionRange": 8.0
          }
        }
        """.formatted(id, uuid, worldIdentity);
  }

  private static BoardDefinition board(String id, String worldKey) {
    return BoardDefinition.create(id, "Shop", transform(worldKey));
  }

  private static BoardTransform transform(String worldKey) {
    return BoardTransform.at(worldKey, WORLD_UUID, 1.5D, 65.0D, -2.5D, 30.0D);
  }
}
