package art.arcane.holoui.config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ConfigManagerImagePathTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void resolvesOnlyFilesInsideTheImageDirectory() throws IOException {
    File root = temp.newFolder("images");
    File nested = new File(root, "icons/menu.png");
    assertTrue(nested.getParentFile().mkdirs());
    assertTrue(nested.createNewFile());

    assertEquals(nested.getCanonicalFile(), ConfigManager.resolveImageFile(root, "icons/menu.png"));
  }

  @Test
  public void rejectsMissingBlankAndEscapingPaths() throws IOException {
    File base = temp.newFolder("base");
    File root = new File(base, "images");
    File outside = new File(base, "outside.png");
    assertTrue(root.mkdirs());
    assertTrue(outside.createNewFile());

    assertThrows(FileNotFoundException.class, () -> ConfigManager.resolveImageFile(root, null));
    assertThrows(FileNotFoundException.class, () -> ConfigManager.resolveImageFile(root, "  "));
    assertThrows(FileNotFoundException.class, () -> ConfigManager.resolveImageFile(root, "missing.png"));
    assertThrows(FileNotFoundException.class, () -> ConfigManager.resolveImageFile(root, "../outside.png"));
    assertThrows(FileNotFoundException.class, () -> ConfigManager.resolveImageFile(root, outside.getAbsolutePath()));
  }
}
