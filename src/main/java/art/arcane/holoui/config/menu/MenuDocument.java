package art.arcane.holoui.config.menu;

import art.arcane.holoui.config.MenuDefinitionData;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public record MenuDocument(String id, String revision, String source, MenuDefinitionData definition) {
  public MenuDocument {
    id = Objects.requireNonNull(id, "id");
    revision = Objects.requireNonNull(revision, "revision");
    source = Objects.requireNonNull(source, "source");
    definition = Objects.requireNonNull(definition, "definition");
  }

  public static String revisionOf(String source) {
    String requiredSource = Objects.requireNonNull(source, "source");
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(requiredSource.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }
}
