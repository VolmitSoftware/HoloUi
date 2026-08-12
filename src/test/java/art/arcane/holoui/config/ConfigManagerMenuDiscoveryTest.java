/*
 * HoloUI is a holographic user interface for Minecraft Bukkit Servers
 * Copyright (c) 2025 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package art.arcane.holoui.config;

import art.arcane.volmlib.util.io.FolderWatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

/**
 * What {@code menus/} hands to the parser, at boot and on every watcher pass. The scan and the
 * watcher share one predicate and canonical relative-id rule. {@code ConfigManager} itself is not
 * constructed here because its constructor arms scheduler tasks.
 */
public class ConfigManagerMenuDiscoveryTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  private long stamp = System.currentTimeMillis();

  @Test
  public void bootScanLoadsNestedJsonWithCanonicalSlashIds() throws IOException {
    File menus = temp.newFolder("menus");
    write(new File(menus, "shop.json"), "{}");
    write(new File(menus, "notes.txt"), "not json");
    File nested = new File(menus, "archive");
    assertTrue(nested.mkdirs());
    write(new File(nested, "old.json"), "{}");

    assertEquals(List.of("archive/old", "shop"), menuIds(menus, ConfigManager.discoverMenuFiles(menus)));
  }

  @Test
  public void createdNestedJsonFilesAreRegisteredAndNonJsonFilesAreSkipped() throws IOException {
    File menus = temp.newFolder("menus");
    write(new File(menus, "shop.json"), "{}");
    File nested = new File(menus, "archive");
    assertTrue(nested.mkdirs());
    FolderWatcher watcher = new FolderWatcher(menus);

    write(new File(menus, "quests.json"), "{}");
    write(new File(menus, "notes.txt"), "not json");
    write(new File(nested, "old.json"), "{}");

    assertTrue(watcher.checkModified());
    assertEquals(List.of("archive/old", "quests"), menuIds(menus, watcher.getCreated()));
  }

  @Test
  public void changedNestedFilesKeepTheirRelativeIds() throws IOException {
    File menus = temp.newFolder("menus");
    File top = new File(menus, "shop.json");
    write(top, "{}");
    File nested = new File(menus, "archive");
    assertTrue(nested.mkdirs());
    File nestedMenu = new File(nested, "old.json");
    write(nestedMenu, "{}");
    FolderWatcher watcher = new FolderWatcher(menus);

    write(top, "{\"components\":[]}");
    write(nestedMenu, "{\"components\":[]}");

    assertTrue(watcher.checkModifiedFast());
    assertEquals(List.of("archive/old", "shop"), menuIds(menus, watcher.getChanged()));
  }

  @Test
  public void extensionMatchIsCaseInsensitive() throws IOException {
    File menus = temp.newFolder("menus");
    File upper = new File(menus, "Shop.JSON");
    write(upper, "{}");

    assertTrue(ConfigManager.isMenuFile(menus, upper));
    assertEquals("Shop", ConfigManager.menuId(menus, upper));
    assertFalse(ConfigManager.isMenuFile(menus, new File(menus, "shop.json.bak")));
    assertFalse(ConfigManager.isMenuFile(menus, menus));
    assertFalse(ConfigManager.isMenuFile(menus, null));
  }

  @Test
  public void hiddenFoldersAndFilesOutsideTheRootAreRejected() throws IOException {
    File menus = temp.newFolder("menus");
    File hidden = new File(menus, ".drafts/secret.json");
    assertTrue(hidden.getParentFile().mkdirs());
    write(hidden, "{}");
    File outside = temp.newFile("outside.json");

    assertFalse(ConfigManager.isMenuFile(menus, hidden));
    assertFalse(ConfigManager.isMenuFile(menus, outside));
    assertTrue(ConfigManager.discoverMenuFiles(menus).isEmpty());
  }

  @Test
  public void symbolicLinkFilesAndDirectoriesAreNeverDiscovered() throws IOException {
    File menus = temp.newFolder("menus");
    File outsideDirectory = temp.newFolder("outside-menus");
    File outsideMenu = new File(outsideDirectory, "outside.json");
    write(outsideMenu, "{}");
    Path fileLink = new File(menus, "linked.json").toPath();
    Path directoryLink = new File(menus, "linked-directory").toPath();
    try {
      Files.createSymbolicLink(fileLink, outsideMenu.toPath());
      Files.createSymbolicLink(directoryLink, outsideDirectory.toPath());
    } catch (UnsupportedOperationException | SecurityException exception) {
      assumeNoException(exception);
    } catch (IOException exception) {
      assumeNoException(exception);
    }

    assertFalse(ConfigManager.isMenuFile(menus, fileLink.toFile()));
    assertTrue(ConfigManager.discoverMenuFiles(menus).isEmpty());
  }

  private static List<String> menuIds(File root, Iterable<File> candidates) {
    List<String> names = new ArrayList<>();
    for (File candidate : candidates) {
      if (ConfigManager.isMenuFile(root, candidate)) {
        names.add(ConfigManager.menuId(root, candidate));
      }
    }
    names.sort(String::compareTo);
    return names;
  }

  private void write(File file, String json) throws IOException {
    Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
    stamp += 5000L;
    assertTrue(file.setLastModified(stamp));
  }
}
