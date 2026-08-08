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
package art.arcane.holoui;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class HoloUiPermissionDeclarationTest {

  @Test
  public void theRootGateNodeIsDeclaredAsOp() throws IOException {
    ConfigurationSection node = permissions().getConfigurationSection(HoloCommand.ROOT_PERM);

    assertNotNull("holoui.command must be declared in plugin.yml", node);
    assertEquals("op", node.getString("default"));
    assertFalse("holoui.command needs a description", node.getString("description", "").isBlank());
  }

  @Test
  public void everyDeclaredSubcommandNodeStaysDeclaredAsOp() throws IOException {
    ConfigurationSection permissions = permissions();
    for (String key : permissions.getKeys(false)) {
      assertEquals(key, "op", permissions.getConfigurationSection(key).getString("default"));
    }

    assertTrue(permissions.contains(HoloCommand.ROOT_PERM + ".list"));
    assertTrue(permissions.contains(HoloCommand.ROOT_PERM + ".previews.dump"));
    assertTrue(permissions.contains("holoui.preview"));
  }

  @Test
  public void theUncheckedBareOpenNodeIsNotDeclared() throws IOException {
    assertFalse("holoui.open is checked by no code path", permissions().contains("holoui.open"));
  }

  private static ConfigurationSection permissions() throws IOException {
    YamlConfiguration config = new YamlConfiguration();
    config.options().pathSeparator(Character.MIN_VALUE);

    try (InputStream stream = HoloUiPermissionDeclarationTest.class.getResourceAsStream("/plugin.yml")) {
      assertNotNull("plugin.yml missing from the resource output", stream);
      try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
        config.load(reader);
      } catch (InvalidConfigurationException e) {
        throw new IOException(e);
      }
    }

    ConfigurationSection permissions = config.getConfigurationSection("permissions");
    assertNotNull("plugin.yml declares no permissions", permissions);
    return permissions;
  }
}
