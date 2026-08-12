package art.arcane.holoui.editor.sync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class EditorSyncJsonTest {
  @Test
  public void canonicalHashMatchesTheCrossSurfaceFixture() throws Exception {
    InputStream resource = getClass().getResourceAsStream("/editor-sync-canonical-v1.json");
    if (resource == null) {
      throw new IllegalStateException("missing editor sync canonical fixture");
    }
    JsonObject fixture;
    try (InputStream input = resource;
         InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
      fixture = JsonParser.parseReader(reader).getAsJsonObject();
    }
    JsonObject project = fixture.getAsJsonObject("project");
    String expected = project.get("baseRevision").getAsString().substring("sha256:".length());

    assertEquals("7e322c580c650ebd93ab4e19dc4b550bbd3f11436d694b38b96143996f8727d0",
        expected);
    assertEquals("sha256:" + expected, EditorSyncJson.revision(project));
    JsonObject withoutRevision = project.deepCopy();
    withoutRevision.remove("baseRevision");
    assertEquals(fixture.get("canonicalWithoutBaseRevision").getAsString(),
        EditorSyncJson.canonical(withoutRevision));
    assertEquals("sha256:" + expected, EditorSyncProject.validated(project, 1024 * 1024).baseRevision());
  }

  @Test
  public void canonicalNumbersUseFiniteIeee754ValuesAcrossRuntimes() {
    JsonObject probes = JsonParser.parseString("""
        {"integer":1,"decimalInteger":1.0,"negativeZero":-0.0,"fraction":0.1,
         "smallExponent":1e-7,"largeFixed":1e20}
        """).getAsJsonObject();

    assertEquals("{\"decimalInteger\":1,\"fraction\":0.1,\"integer\":1,"
        + "\"largeFixed\":100000000000000000000,\"negativeZero\":0,"
        + "\"smallExponent\":1e-7}", EditorSyncJson.canonical(probes));
  }

  @Test
  public void onlyTheRootBaseRevisionIsExcludedFromTheHash() {
    JsonObject project = JsonParser.parseString("""
        {"nested":{"baseRevision":"inner"},"baseRevision":"old"}
        """).getAsJsonObject();
    String first = EditorSyncJson.revision(project);
    project.addProperty("baseRevision", "changed-root");
    assertEquals(first, EditorSyncJson.revision(project));
    project.getAsJsonObject("nested").addProperty("baseRevision", "changed-inner");
    org.junit.Assert.assertNotEquals(first, EditorSyncJson.revision(project));
  }

  @Test
  public void projectLimitsRejectDeepOrHugeDocumentsBeforeDomainParsing() {
    JsonObject deep = new JsonObject();
    JsonObject current = deep;
    for (int index = 0; index < 70; index++) {
      JsonObject child = new JsonObject();
      current.add("child", child);
      current = child;
    }
    assertThrows(IllegalArgumentException.class,
        () -> EditorSyncProject.validated(deep, 1024 * 1024));
  }
}
