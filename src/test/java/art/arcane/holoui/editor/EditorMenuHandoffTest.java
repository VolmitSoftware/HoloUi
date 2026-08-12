package art.arcane.holoui.editor;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Random;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EditorMenuHandoffTest {
  @Test
  public void emitsTheVersionedEditorEnvelopeAsAPrivateFragment() throws IOException {
    String menuJson = "{\n  \"offset\": {\"x\": 0},\n  \"components\": []\n}";

    String url = EditorMenuHandoff.createUrl(
        "https://example.com/editor?ignored=yes#old",
        "shops/tools/main",
        menuJson
    );

    assertTrue(url.startsWith("https://example.com/editor/#/import/menu/"));
    assertFalse(url.contains("ignored"));
    String payload = url.substring(url.lastIndexOf('/') + 1);
    JsonObject envelope = decode(payload);
    assertEquals(1, envelope.get("version").getAsInt());
    assertEquals("menu", envelope.get("kind").getAsString());
    assertEquals("shops/tools/main", envelope.get("runtimeId").getAsString());
    assertEquals(menuJson, envelope.get("json").getAsString());
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsTraversalRuntimeIds() {
    EditorMenuHandoff.encode("shops/../admin", "{}");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsNonHttpEditorUrls() {
    EditorMenuHandoff.createUrl("file:///tmp/editor", "menu", "{}");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsCredentialBearingEditorUrls() {
    EditorMenuHandoff.createUrl("https://operator@example.com/editor", "menu", "{}");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsANonObjectMenuPayload() {
    EditorMenuHandoff.encode("menu", "[]");
  }

  @Test(expected = EditorMenuHandoff.PayloadTooLargeException.class)
  public void reportsWhenTheCompressedPayloadCannotFitInTheLink() {
    byte[] randomBytes = new byte[60_000];
    new Random(41L).nextBytes(randomBytes);
    String menuJson = "{\"data\":\"" + Base64.getEncoder().encodeToString(randomBytes) + "\"}";

    EditorMenuHandoff.createUrl("https://example.com", "large", menuJson);
  }

  private static JsonObject decode(String payload) throws IOException {
    byte[] compressed = Base64.getUrlDecoder().decode(payload);
    try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
      String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
      return JsonParser.parseString(json).getAsJsonObject();
    }
  }
}
