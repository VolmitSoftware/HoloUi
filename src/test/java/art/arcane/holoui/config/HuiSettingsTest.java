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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HuiSettingsTest {

  @Rule
  public final TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void generatedFileCarriesEveryRegisteredKeyAtItsDefault() throws IOException {
    File configDir = temp.newFolder("plugin");

    new HuiSettings(configDir);

    JsonObject json = JsonParser.parseString(
        Files.readString(new File(configDir, "settings.json").toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
    assertEquals(Set.of("debugHitbox", "debugPosition", "builderUrl", "previewEnabled",
        "editorSyncEnabled", "editorSyncEndpoint", "editorSyncCreateToken", "editorSyncSessionMinutes",
        "editorSyncPollSeconds", "editorSyncMaxProjectMiB", "previewLookDistance",
        "previewScale", "uiScale", "customItems", "customItemProviders"), json.keySet());
    assertFalse(json.get("debugHitbox").getAsBoolean());
    assertFalse(json.get("debugPosition").getAsBoolean());
    assertEquals(HuiSettings.BUILDER_URL_DEFAULT, json.get("builderUrl").getAsString());
    assertTrue(json.get("editorSyncEnabled").getAsBoolean());
    assertEquals(HuiSettings.EDITOR_SYNC_ENDPOINT_DEFAULT,
        json.get("editorSyncEndpoint").getAsString());
    assertEquals("", json.get("editorSyncCreateToken").getAsString());
    assertTrue(json.get("previewEnabled").getAsBoolean());
    assertEquals(10.00D, json.get("previewLookDistance").getAsDouble(), 0D);
    assertEquals(0.65D, json.get("previewScale").getAsDouble(), 0D);
    assertEquals(1.00D, json.get("uiScale").getAsDouble(), 0D);
    assertTrue(json.get("customItems").getAsBoolean());
    assertEquals("", json.get("customItemProviders").getAsString());
  }

  @Test
  public void syncEndpointsRequireVersionedHttpsOrLoopbackHttp() {
    assertEquals("https://relay.example.net/custom/v1",
        HuiSettings.sanitizeSyncEndpoint("HTTPS://relay.example.net/custom/v1/"));
    assertEquals("http://localhost:8080/v1",
        HuiSettings.sanitizeSyncEndpoint("http://localhost:8080/v1"));
    assertEquals("http://[::1]:8080/v1",
        HuiSettings.sanitizeSyncEndpoint("http://[::1]:8080/v1"));

    String fallback = HuiSettings.EDITOR_SYNC_ENDPOINT_DEFAULT;
    assertEquals(fallback, HuiSettings.sanitizeSyncEndpoint("http://relay.example.net/v1"));
    assertEquals(fallback, HuiSettings.sanitizeSyncEndpoint("https://trusted@evil.example/v1"));
    assertEquals(fallback, HuiSettings.sanitizeSyncEndpoint("https://relay.example/v2"));
    assertEquals(fallback, HuiSettings.sanitizeSyncEndpoint("https://relay.example/v1?token=x"));
    assertEquals(fallback, HuiSettings.sanitizeSyncEndpoint("https://relay.example/v1#fragment"));
    assertEquals(fallback, HuiSettings.sanitizeSyncEndpoint(" https://relay.example/v1"));
    assertEquals(fallback, HuiSettings.sanitizeSyncEndpoint(
        "https://relay.example/" + "x".repeat(1024) + "/v1"));
  }

  @Test
  public void syncCreateTokensUseTheWireCapabilityAlphabetAndBounds() {
    assertEquals("a".repeat(22), HuiSettings.sanitizeSyncCreateToken("a".repeat(22)));
    assertEquals("A_b-9".repeat(20),
        HuiSettings.sanitizeSyncCreateToken("A_b-9".repeat(20)));
    assertEquals("", HuiSettings.sanitizeSyncCreateToken("short"));
    assertEquals("", HuiSettings.sanitizeSyncCreateToken("a".repeat(129)));
    assertEquals("", HuiSettings.sanitizeSyncCreateToken("a".repeat(21) + "/"));
    assertEquals("", HuiSettings.sanitizeSyncCreateToken(" " + "a".repeat(22)));
  }

  @Test
  public void bootLoadAppliesDebugFlagsFromFileWithoutListeners() throws IOException {
    File configDir = temp.newFolder("plugin-debug");
    writeSettings(configDir, true, true);

    new HuiSettings(configDir);

    assertTrue(Boolean.TRUE.equals(HuiSettings.DEBUG_HITBOX.value()));
    assertTrue(Boolean.TRUE.equals(HuiSettings.DEBUG_SPACING.value()));

    File restoreDir = temp.newFolder("plugin-restore");
    writeSettings(restoreDir, false, false);
    new HuiSettings(restoreDir);
    assertFalse(Boolean.TRUE.equals(HuiSettings.DEBUG_HITBOX.value()));
    assertFalse(Boolean.TRUE.equals(HuiSettings.DEBUG_SPACING.value()));
  }

  private static void writeSettings(File configDir, boolean debugHitbox, boolean debugPosition) throws IOException {
    File settings = new File(configDir, "settings.json");
    Files.writeString(settings.toPath(), """
        {
          "debugHitbox": %s,
          "debugPosition": %s,
          "builderUrl": "https://holoui.volmitsoftware.com",
          "editorSyncEnabled": true,
          "editorSyncEndpoint": "https://sync.holoui.volmitsoftware.com/v1",
          "editorSyncCreateToken": "",
          "editorSyncSessionMinutes": 60,
          "editorSyncPollSeconds": 3,
          "editorSyncMaxProjectMiB": 8,
          "previewEnabled": true,
          "previewLookDistance": 10.0,
          "previewScale": 0.65,
          "uiScale": 1.0,
          "customItems": true,
          "customItemProviders": ""
        }
        """.formatted(debugHitbox, debugPosition), StandardCharsets.UTF_8);
  }
}
