package art.arcane.holoui.config.menu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
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
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class MenuDocumentRepositoryTest {
  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void nestedMutationIsAtomicAndPreservesUnknownFields() throws IOException {
    File pluginData = temp.newFolder("plugin");
    Path menu = writeMenu(pluginData, "folders/shop", source("Original"));
    MenuDocumentRepository repository = new MenuDocumentRepository(pluginData);
    String original = Files.readString(menu, StandardCharsets.UTF_8);

    MenuDocument changed = repository.mutate("folders/shop", MenuDocument.revisionOf(original),
        document -> MenuRowMutations.setTextRow(document, 1, "Changed"));

    JsonObject persisted = JsonParser.parseString(changed.source()).getAsJsonObject();
    assertEquals("Changed", persisted.getAsJsonArray("components").get(0).getAsJsonObject()
        .getAsJsonObject("data").getAsJsonObject("icon").get("text").getAsString());
    assertEquals("preserved", persisted.get("unknownRoot").getAsString());
    assertEquals(changed.revision(), MenuDocument.revisionOf(Files.readString(menu)));
    try (Stream<Path> files = Files.walk(pluginData.toPath().resolve("menus"))) {
      assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
    }
  }

  @Test
  public void staleRevisionNeverInvokesTheMutationOrOverwritesExternalChanges() throws IOException {
    File pluginData = temp.newFolder("revision");
    Path menu = writeMenu(pluginData, "shop", source("Original"));
    MenuDocumentRepository repository = new MenuDocumentRepository(pluginData);
    String stale = MenuDocument.revisionOf(Files.readString(menu));
    Files.writeString(menu, source("External"), StandardCharsets.UTF_8);
    boolean[] invoked = {false};

    MenuRevisionConflictException conflict = assertThrows(MenuRevisionConflictException.class,
        () -> repository.mutate("shop", stale, document -> {
          invoked[0] = true;
          return document;
        }));

    assertFalse(invoked[0]);
    assertEquals(stale, conflict.expectedRevision());
    assertEquals("External", text(Files.readString(menu)));
  }

  @Test
  public void externalChangeDuringMutationWinsTheFinalRevisionCheck() throws IOException {
    File pluginData = temp.newFolder("mid-write-conflict");
    Path menu = writeMenu(pluginData, "shop", source("Original"));
    MenuDocumentRepository repository = new MenuDocumentRepository(pluginData);
    String original = Files.readString(menu);

    assertThrows(MenuRevisionConflictException.class, () -> repository.mutate(
        "shop", MenuDocument.revisionOf(original), document -> {
          replace(menu, source("External"));
          return MenuRowMutations.setTextRow(document, 1, "Command");
        }));

    assertEquals("External", text(Files.readString(menu)));
  }

  @Test
  public void copyPreservesTheDocumentAndRejectsExistingTargets() throws IOException {
    File pluginData = temp.newFolder("copy");
    Path source = writeMenu(pluginData, "source/main", source("Source"));
    MenuDocumentRepository repository = new MenuDocumentRepository(pluginData);
    String revision = MenuDocument.revisionOf(Files.readString(source));

    MenuDocument copied = repository.copy("source/main", revision, "target/copy");

    assertEquals("target/copy", copied.id());
    assertEquals("Source", text(copied.source()));
    assertTrue(pluginData.toPath().resolve("menus/target/copy.json").toFile().isFile());
    assertThrows(FileAlreadyExistsException.class,
        () -> repository.copy("source/main", revision, "target/copy"));
  }

  @Test
  public void traversalAndSymbolicDirectoriesCannotEscapeMenuStorage() throws IOException {
    File pluginData = temp.newFolder("paths");
    writeMenu(pluginData, "source", source("Source"));
    MenuDocumentRepository repository = new MenuDocumentRepository(pluginData);
    String revision = MenuDocument.revisionOf(Files.readString(
        pluginData.toPath().resolve("menus/source.json")));

    assertThrows(IllegalArgumentException.class,
        () -> repository.copy("source", revision, "../outside"));
    Path outside = temp.newFolder("outside").toPath();
    Path link = pluginData.toPath().resolve("menus/linked");
    try {
      Files.createSymbolicLink(link, outside);
    } catch (IOException | UnsupportedOperationException failure) {
      Assume.assumeNoException(failure);
    }
    assertThrows(IOException.class,
        () -> repository.copy("source", revision, "linked/escape"));
    assertFalse(outside.resolve("escape.json").toFile().exists());
  }

  @Test
  public void invalidMutationIsValidatedBeforeTheOriginalFileChanges() throws IOException {
    File pluginData = temp.newFolder("validation");
    Path menu = writeMenu(pluginData, "shop", source("Original"));
    MenuDocumentRepository repository = new MenuDocumentRepository(pluginData);
    String original = Files.readString(menu);

    assertThrows(JsonParseException.class, () -> repository.mutate(
        "shop", MenuDocument.revisionOf(original), document -> {
          document.addProperty("components", "invalid");
          return document;
        }));
    assertEquals(original, Files.readString(menu));
  }

  private static Path writeMenu(File pluginData, String id, String source) throws IOException {
    Path path = pluginData.toPath().resolve("menus").resolve(id + ".json");
    Files.createDirectories(path.getParent());
    Files.writeString(path, source, StandardCharsets.UTF_8);
    return path;
  }

  private static String source(String text) {
    return """
        {
          "offset": [0, 0, 1],
          "unknownRoot": "preserved",
          "components": [{
            "id": "row",
            "offset": [0, 0, 0],
            "data": {
              "type": "decoration",
              "icon": {"type": "text", "text": "%s"}
            }
          }]
        }
        """.formatted(text);
  }

  private static String text(String source) {
    return JsonParser.parseString(source).getAsJsonObject().getAsJsonArray("components")
        .get(0).getAsJsonObject().getAsJsonObject("data").getAsJsonObject("icon")
        .get("text").getAsString();
  }

  private static void replace(Path path, String source) {
    try {
      Files.writeString(path, source, StandardCharsets.UTF_8);
    } catch (IOException failure) {
      throw new IllegalStateException("unable to replace test menu", failure);
    }
  }
}
