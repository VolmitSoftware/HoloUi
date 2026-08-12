package art.arcane.holoui.config.menu;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class MenuBaselines {
  public static final String BLANK_HOLOGRAM_RESOURCE = "/baselines/blank-hologram.json";

  private MenuBaselines() {
  }

  public static String blankHologramSource() {
    try (InputStream stream = MenuBaselines.class.getResourceAsStream(BLANK_HOLOGRAM_RESOURCE)) {
      InputStream required = Objects.requireNonNull(stream, "missing " + BLANK_HOLOGRAM_RESOURCE);
      return new String(required.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException failure) {
      throw new IllegalStateException("failed to read " + BLANK_HOLOGRAM_RESOURCE, failure);
    }
  }
}
