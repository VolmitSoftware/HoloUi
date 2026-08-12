package art.arcane.holoui.editor;

import art.arcane.holoui.board.BoardIds;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

public final class EditorMenuHandoff {
  public static final int FORMAT_VERSION = 1;
  public static final int MAX_PAYLOAD_CHARACTERS = 48_000;

  private static final Gson GSON = new Gson();

  private EditorMenuHandoff() {
  }

  public static String createUrl(String editorUrl, String runtimeId, String menuJson) {
    String payload = encode(runtimeId, menuJson);
    if (payload.length() > MAX_PAYLOAD_CHARACTERS) {
      throw new PayloadTooLargeException(payload.length());
    }
    return editorBase(editorUrl) + "#/import/menu/" + payload;
  }

  public static String encode(String runtimeId, String menuJson) {
    String canonicalRuntimeId = BoardIds.requireMenuReference(runtimeId);
    String requiredJson = Objects.requireNonNull(menuJson, "menuJson");
    JsonElement parsedMenu = JsonParser.parseString(requiredJson);
    if (!parsedMenu.isJsonObject()) {
      throw new IllegalArgumentException("menuJson must contain a JSON object");
    }
    JsonObject envelope = new JsonObject();
    envelope.addProperty("version", FORMAT_VERSION);
    envelope.addProperty("kind", "menu");
    envelope.addProperty("runtimeId", canonicalRuntimeId);
    envelope.addProperty("json", requiredJson);
    byte[] compressed = gzip(GSON.toJson(envelope).getBytes(StandardCharsets.UTF_8));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(compressed);
  }

  public static String editorBase(String editorUrl) {
    URI parsed;
    try {
      parsed = new URI(Objects.requireNonNull(editorUrl, "editorUrl"));
    } catch (URISyntaxException failure) {
      throw new IllegalArgumentException("editorUrl must be a valid HTTP(S) URL", failure);
    }
    String scheme = parsed.getScheme();
    if (scheme == null || parsed.getHost() == null || parsed.getUserInfo() != null
        || !(scheme.toLowerCase(Locale.ROOT).equals("http")
        || scheme.toLowerCase(Locale.ROOT).equals("https"))) {
      throw new IllegalArgumentException("editorUrl must be a valid HTTP(S) URL");
    }
    String path = parsed.getPath();
    if (path == null || path.isEmpty()) {
      path = "/";
    } else if (!path.endsWith("/")) {
      path += "/";
    }
    try {
      return new URI(parsed.getScheme().toLowerCase(Locale.ROOT), null,
          parsed.getHost().toLowerCase(Locale.ROOT), parsed.getPort(), path, null, null)
          .toASCIIString();
    } catch (URISyntaxException failure) {
      throw new IllegalArgumentException("editorUrl could not be normalized", failure);
    }
  }

  private static byte[] gzip(byte[] source) {
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream(source.length);
      try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
        gzip.write(source);
      }
      return output.toByteArray();
    } catch (IOException failure) {
      throw new IllegalStateException("failed to compress the editor menu handoff", failure);
    }
  }

  public static final class PayloadTooLargeException extends IllegalArgumentException {
    private final int payloadCharacters;

    private PayloadTooLargeException(int payloadCharacters) {
      super("compressed menu handoff exceeds " + MAX_PAYLOAD_CHARACTERS + " URL characters");
      this.payloadCharacters = payloadCharacters;
    }

    public int payloadCharacters() {
      return payloadCharacters;
    }
  }
}
