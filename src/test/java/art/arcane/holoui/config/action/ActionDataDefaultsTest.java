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
package art.arcane.holoui.config.action;

import art.arcane.holoui.api.HoloClickTrigger;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.enums.MenuActionCommandSource;
import art.arcane.holoui.enums.NavigationMode;
import art.arcane.holoui.enums.SoundSource;
import art.arcane.volmlib.util.bukkit.json.BukkitJson;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ActionDataDefaultsTest {

  private static MenuActionData action(String json) {
    return BukkitJson.GSON.fromJson(json, MenuActionData.class);
  }

  private static String menu(String soundKey) {
    return "{\"offset\":[0,0,0],\"components\":[{\"id\":\"buy\",\"offset\":[0,0,0],"
        + "\"data\":{\"type\":\"button\",\"icon\":{\"type\":\"text\",\"text\":\"Buy\"},"
        + "\"actions\":[{\"type\":\"sound\",\"sound\":\"" + soundKey + "\"}]}}]}";
  }

  @Test
  public void aMenuDeclaringAnUnknownSoundKeyStillLoads() {
    MenuDefinitionData decoded = BukkitJson.GSON.fromJson(menu("ui.button.nonexistent"), MenuDefinitionData.class);

    assertNotNull("an unresolvable sound key must not discard the menu file", decoded);
    assertEquals(1, decoded.getComponents().size());
  }

  @Test
  public void aMenuDeclaringAMalformedSoundKeyStillLoads() {
    MenuDefinitionData decoded = BukkitJson.GSON.fromJson(menu("UI_BUTTON_CLICK"), MenuDefinitionData.class);

    assertNotNull("a malformed sound key must not discard the menu file", decoded);
    assertEquals(1, decoded.getComponents().size());
  }

  @Test
  public void anOmittedCommandSourceDefaultsToThePlayer() {
    CommandActionData decoded = (CommandActionData) action("{\"type\":\"command\",\"command\":\"/spawn\"}");

    assertNull(decoded.source());
    assertEquals(MenuActionCommandSource.PLAYER, decoded.sourceOrDefault());
  }

  @Test
  public void anExplicitCommandSourceIsKept() {
    assertEquals(MenuActionCommandSource.GLOBAL,
        ((CommandActionData) action("{\"type\":\"command\",\"source\":\"server\",\"command\":\"/say hi\"}")).sourceOrDefault());
    assertEquals(MenuActionCommandSource.PLAYER,
        ((CommandActionData) action("{\"type\":\"command\",\"source\":\"player\",\"command\":\"/spawn\"}")).sourceOrDefault());
  }

  @Test
  public void gsonAlsoAcceptsJavaCommandSourceNames() {
    assertEquals(MenuActionCommandSource.GLOBAL,
        ((CommandActionData) action("{\"type\":\"command\",\"source\":\"GLOBAL\",\"command\":\"/say hi\"}")).sourceOrDefault());
    assertEquals(MenuActionCommandSource.PLAYER,
        ((CommandActionData) action("{\"type\":\"command\",\"source\":\"PLAYER\",\"command\":\"/spawn\"}")).sourceOrDefault());
  }

  @Test
  public void navigationDefaultsToPushingTheTargetPage() {
    NavigationActionData decoded = (NavigationActionData) action(
        "{\"type\":\"navigate\",\"target\":\"shops/confirm\"}");

    assertEquals("shops/confirm", decoded.target());
    assertEquals(NavigationMode.PUSH, decoded.modeOrDefault());
  }

  @Test
  public void navigationModesUseTheirSerializedNames() {
    NavigationActionData decoded = (NavigationActionData) action(
        "{\"type\":\"navigate\",\"mode\":\"back\"}");

    assertEquals(NavigationMode.BACK, decoded.modeOrDefault());
    assertNull(decoded.target());
  }

  @Test
  public void interactionActionsDecodeIntoTheirTypedRecords() {
    MessageActionData message = (MessageActionData) action(
        "{\"type\":\"message\",\"message\":\"<green>Hello</green>\"}");
    TeleportActionData teleport = (TeleportActionData) action(
        "{\"type\":\"teleport\",\"world\":\"minecraft:overworld\","
            + "\"x\":1.5,\"y\":64,\"z\":-2,\"yaw\":90,\"pitch\":0}");
    ConnectActionData connect = (ConnectActionData) action(
        "{\"type\":\"connect\",\"server\":\"lobby-1\"}");

    assertEquals("<green>Hello</green>", message.message());
    assertEquals("minecraft:overworld", teleport.world());
    assertEquals(1.5D, teleport.x(), 0.0D);
    assertEquals(90.0F, teleport.yaw(), 0.0F);
    assertTrue(teleport.hasValidDestination());
    assertEquals("lobby-1", connect.server());
    assertTrue(connect.hasValidServer());
  }

  @Test
  public void teleportAndConnectValidationRejectUnsafeValues() {
    assertFalse(new TeleportActionData("world", 0D, 64D, 0D, 0F, 0F, null).hasValidDestination());
    assertFalse(new TeleportActionData("minecraft:overworld", Double.NaN, 64D, 0D, 0F, 0F, null)
        .hasValidDestination());
    assertFalse(new TeleportActionData("minecraft:overworld", 0D, 64D, 0D, Float.POSITIVE_INFINITY, 0F, null)
        .hasValidDestination());
    assertFalse(new ConnectActionData("lobby\nConnect\nevil", null).hasValidServer());
    assertFalse(new ConnectActionData("lobby west", null).hasValidServer());
  }

  @Test
  public void anOmittedSoundVolumePitchAndSourceFallBackToAudibleDefaults() {
    SoundActionData decoded = (SoundActionData) action("{\"type\":\"sound\",\"sound\":\"ui.button.click\"}");

    assertEquals("ui.button.click", decoded.sound());
    assertNull(decoded.volume());
    assertNull(decoded.pitch());
    assertEquals(1F, decoded.volumeOrDefault(), 0F);
    assertEquals(1F, decoded.pitchOrDefault(), 0F);
    assertEquals(SoundSource.MASTER, decoded.sourceOrDefault());
  }

  @Test
  public void anExplicitZeroVolumeStaysZeroAndIsNotTreatedAsOmitted() {
    SoundActionData decoded = (SoundActionData) action(
        "{\"type\":\"sound\",\"sound\":\"ui.button.click\",\"source\":\"block\",\"volume\":0,\"pitch\":0.75}");

    assertEquals(0F, decoded.volumeOrDefault(), 0F);
    assertEquals(0.75F, decoded.pitchOrDefault(), 0F);
    assertEquals(SoundSource.BLOCK, decoded.sourceOrDefault());
  }

  @Test
  public void gsonAlsoAcceptsJavaSoundSourceNames() {
    SoundActionData decoded = (SoundActionData) action(
        "{\"type\":\"sound\",\"sound\":\"ui.button.click\",\"source\":\"MUSIC\"}");

    assertEquals(SoundSource.MUSIC, decoded.sourceOrDefault());
  }

  @Test
  public void anInvalidSoundKeyResolvesToNothingInsteadOfThrowing() {
    assertNull(new SoundActionData("ui.button.nonexistent", null, null, null, null).resolveSound());
    assertNull(new SoundActionData("UI_BUTTON_CLICK", null, null, null, null).resolveSound());
    assertNull(new SoundActionData(null, null, null, null, null).resolveSound());
  }

  @Test
  public void actionTriggersDecodeStrictlyAndDefaultToAny() {
    assertEquals(HoloClickTrigger.ANY,
        ((CommandActionData) action("{\"type\":\"command\",\"command\":\"spawn\"}")).triggerOrDefault());
    assertEquals(HoloClickTrigger.ANY,
        ((CommandActionData) action("{\"type\":\"command\",\"command\":\"spawn\",\"trigger\":null}")).triggerOrDefault());
    assertEquals(HoloClickTrigger.SHIFT_RIGHT_CLICK,
        ((CommandActionData) action("{\"type\":\"command\",\"command\":\"spawn\","
            + "\"trigger\":\"shift_right_click\"}")).triggerOrDefault());
    assertThrows(RuntimeException.class, () -> action(
        "{\"type\":\"command\",\"command\":\"spawn\",\"trigger\":\"middle_click\"}"));

    String encoded = BukkitJson.GSON.toJson(
        new MessageActionData("Hello", HoloClickTrigger.SHIFT_LEFT_CLICK)
    );
    assertTrue(encoded, encoded.contains("\"trigger\": \"shift_left_click\""));
  }
}
