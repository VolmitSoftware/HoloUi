package art.arcane.holoui.editor.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

public class EditorSyncPublicationValidatorTest {
  @Test
  public void optimisticBaseAndEditedSnapshotRevisionAreIntentionallyDifferent() {
    JsonObject base = menuProject("shop", "{\"components\":[]}");
    EditorSyncStoredSession session = session(base);
    JsonObject changed = menuProject("shop", "{\"offset\":[0,0,1],\"components\":[]}");
    assertNotEquals(session.baseRevision(), changed.get("baseRevision").getAsString());

    EditorSyncPublicationValidator.ValidatedProject validated =
        new EditorSyncPublicationValidator().validate(session,
            new EditorSyncPublication(1L, session.baseRevision(), changed), 1024 * 1024);

    assertEquals("shop", validated.project().subjectId());
    assertEquals(changed.get("baseRevision").getAsString(), validated.project().baseRevision());
  }

  @Test
  public void menuCapabilitiesCannotAddASecondMenuOrChangeTheSubject() {
    JsonObject base = menuProject("shop", "{\"components\":[]}");
    EditorSyncStoredSession session = session(base);
    JsonObject changed = menuProject("shop", "{\"components\":[]}");
    JsonObject additional = new JsonObject();
    additional.addProperty("id", "admin");
    additional.addProperty("json", "{\"components\":[]}");
    changed.getAsJsonArray("menus").add(additional);
    changed.addProperty("baseRevision", EditorSyncJson.revision(changed));

    assertThrows(IllegalArgumentException.class, () ->
        new EditorSyncPublicationValidator().validate(session,
            new EditorSyncPublication(1L, session.baseRevision(), changed), 1024 * 1024));
  }

  @Test
  public void publicationRequestMustUseTheStoredOptimisticBase() {
    JsonObject base = menuProject("shop", "{\"components\":[]}");
    EditorSyncStoredSession session = session(base);
    JsonObject changed = menuProject("shop", "{\"components\":[]}");

    assertThrows(IllegalArgumentException.class, () ->
        new EditorSyncPublicationValidator().validate(session,
            new EditorSyncPublication(1L, "sha256:" + "0".repeat(64), changed),
            1024 * 1024));
  }

  private static EditorSyncStoredSession session(JsonObject project) {
    return new EditorSyncStoredSession(new EditorSyncStoredSession.Capability(
        "session_id_123456789ab", "server_token_123456789", "https://relay.example/v1"),
        new EditorSyncStoredSession.Subject(EditorSyncKind.MENU, "shop"),
        Instant.now().plusSeconds(3600L), 0L, project, null);
  }

  private static JsonObject menuProject(String id, String source) {
    JsonObject project = new JsonObject();
    project.addProperty("format", EditorSyncJson.PROJECT_FORMAT);
    project.addProperty("version", 1);
    project.addProperty("kind", "menu");
    project.addProperty("subjectId", id);
    JsonObject menu = new JsonObject();
    menu.addProperty("id", id);
    menu.addProperty("json", source);
    JsonArray menus = new JsonArray();
    menus.add(menu);
    project.add("menus", menus);
    project.add("images", new JsonArray());
    JsonObject constraints = new JsonObject();
    constraints.addProperty("subjectId", id);
    JsonArray menuIds = new JsonArray();
    menuIds.add(id);
    constraints.add("menuIds", menuIds);
    constraints.add("imagePaths", new JsonArray());
    constraints.addProperty("newImagePrefix", "sync/menus/" + id + "/");
    constraints.addProperty("allowDeletes", false);
    project.add("constraints", constraints);
    project.add("warnings", new JsonArray());
    project.addProperty("baseRevision", EditorSyncJson.revision(project));
    return project;
  }
}
