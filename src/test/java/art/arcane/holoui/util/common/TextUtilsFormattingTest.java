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
package art.arcane.holoui.util.common;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TextUtilsFormattingTest {

  private static Map<String, TextDecoration> decorationCodes() {
    Map<String, TextDecoration> codes = new LinkedHashMap<>();
    codes.put("k", TextDecoration.OBFUSCATED);
    codes.put("l", TextDecoration.BOLD);
    codes.put("m", TextDecoration.STRIKETHROUGH);
    codes.put("n", TextDecoration.UNDERLINED);
    codes.put("o", TextDecoration.ITALIC);
    return codes;
  }

  private static Map<String, TextColor> colourCodes() {
    Map<String, TextColor> codes = new LinkedHashMap<>();
    codes.put("0", NamedTextColor.BLACK);
    codes.put("1", NamedTextColor.DARK_BLUE);
    codes.put("2", NamedTextColor.DARK_GREEN);
    codes.put("3", NamedTextColor.DARK_AQUA);
    codes.put("4", NamedTextColor.DARK_RED);
    codes.put("5", NamedTextColor.DARK_PURPLE);
    codes.put("6", NamedTextColor.GOLD);
    codes.put("7", NamedTextColor.GRAY);
    codes.put("8", NamedTextColor.DARK_GRAY);
    codes.put("9", NamedTextColor.BLUE);
    codes.put("a", NamedTextColor.GREEN);
    codes.put("b", NamedTextColor.AQUA);
    codes.put("c", NamedTextColor.RED);
    codes.put("d", NamedTextColor.LIGHT_PURPLE);
    codes.put("e", NamedTextColor.YELLOW);
    codes.put("f", NamedTextColor.WHITE);
    return codes;
  }

  private static boolean decorated(Component component, TextDecoration decoration) {
    if (component.decoration(decoration) == TextDecoration.State.TRUE)
      return true;
    return component.children().stream().anyMatch(c -> decorated(c, decoration));
  }

  private static boolean coloured(Component component, TextColor colour) {
    if (colour.equals(component.color()))
      return true;
    return component.children().stream().anyMatch(c -> coloured(c, colour));
  }

  @Test
  public void everyLegacyFormatCodeSurvivesAsARealDecoration() {
    for (Map.Entry<String, TextDecoration> entry : decorationCodes().entrySet()) {
      Component parsed = TextUtils.parse("&" + entry.getKey() + "Sample");
      assertEquals("&" + entry.getKey(), "Sample", TextUtils.content(parsed));
      assertTrue("&" + entry.getKey(), decorated(parsed, entry.getValue()));
    }
  }

  @Test
  public void everyLegacyColourCodeSurvivesAsTheMatchingNamedColour() {
    for (Map.Entry<String, TextColor> entry : colourCodes().entrySet()) {
      Component parsed = TextUtils.parse("&" + entry.getKey() + "Sample");
      assertEquals("&" + entry.getKey(), "Sample", TextUtils.content(parsed));
      assertTrue("&" + entry.getKey(), coloured(parsed, entry.getValue()));
    }
  }

  @Test
  public void sectionSignCodesFromPlaceholdersAreRewrittenToo() {
    Component parsed = TextUtils.parse("\u00a7nUnderlined");

    assertEquals("Underlined", TextUtils.content(parsed));
    assertTrue(decorated(parsed, TextDecoration.UNDERLINED));
  }

  @Test
  public void resetCodeParsesInsteadOfLeakingLiteralText() {
    Component parsed = TextUtils.parse("&cred&rplain");

    assertEquals("redplain", TextUtils.content(parsed));
  }

  @Test
  public void miniMessageTagsAndUnknownAmpersandsAreLeftAlone() {
    assertEquals("Play", TextUtils.content(TextUtils.parse("<gold><bold>Play")));
    assertEquals("Tom & Jerry", TextUtils.content(TextUtils.parse("Tom & Jerry")));
    assertEquals("&", TextUtils.content(TextUtils.parse("&")));
  }
}
