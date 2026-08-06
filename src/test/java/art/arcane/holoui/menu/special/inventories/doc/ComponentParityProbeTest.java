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
package art.arcane.holoui.menu.special.inventories.doc;

import art.arcane.holoui.util.common.TextUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Pins how far {@link TextUtils#parse} can reproduce a programmatically assembled component,
 * measured the way {@link GoldenSerializer} measures them: {@code compact()} on both sides, then
 * the Gson serializer.
 *
 * <p>Preview documents render every label through {@code TextUtils.parse}, so this is what says a
 * plain string label can rebuild each golden snapshot. It can: every label shape in the goldens is
 * reproducible from a string.
 *
 * <table>
 *   <caption>Reproducibility of the old label shapes</caption>
 *   <tr><th>Old shape</th><th>Parse form</th></tr>
 *   <tr><td>One styled run</td><td>legacy {@code &7x} or MiniMessage {@code <gray>x}</td></tr>
 *   <tr><td>Bold white title run</td><td>{@code &f&lChest}</td></tr>
 *   <tr><td>Hex-coloured run</td><td>{@code <#f2a535>...}</td></tr>
 *   <tr><td>Two appended runs (state + surge suffix)</td><td>a plain nested tag</td></tr>
 *   <tr><td>Three appended runs (furnace and brewing stat lines)</td>
 *       <td>open outer tag, closed inner tags</td></tr>
 * </table>
 *
 * <p>The only shape that needs care is three or more appended runs, because {@code append} produces
 * siblings under a <em>non-empty</em> root. Leaving every tag open nests each run inside the last
 * instead, and closing every tag leaves the empty root MiniMessage always builds, which
 * {@code compact()} only folds away when it has exactly one child. Leaving the <em>outer</em> tag
 * open and closing the <em>inner</em> ones gives both at once: the leading text stays in the root
 * run, and the closed runs become its siblings. Two appended runs coincide with the naive nested
 * form only because one sibling and one nest level are the same tree.
 */
public class ComponentParityProbeTest {

  private static String serialize(Component component) {
    return GsonComponentSerializer.gson().serialize(component.compact());
  }

  // ---------------------------------------------------------------------
  // Reproducible
  // ---------------------------------------------------------------------

  /** Case (a): a single named-colour run, the shape of every state label without a suffix. */
  @Test
  public void singleNamedColourRunIsReproducibleFromBothLegacyAndMiniMessage() {
    String expected = "{\"color\":\"gray\",\"text\":\"x\"}";

    assertEquals(expected, serialize(Component.text("x").color(NamedTextColor.GRAY)));
    assertEquals(expected, serialize(TextUtils.parse("<gray>x")));
    assertEquals(expected, serialize(TextUtils.parse("&7x")));
  }

  /** The default card title: white and bold, which {@code blockTitle} builds by decorating. */
  @Test
  public void boldWhiteTitleIsReproducibleFromLegacy() {
    String expected = "{\"bold\":true,\"color\":\"white\",\"text\":\"Chest\"}";

    assertEquals(expected, serialize(Component.text("Chest").color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD)));
    assertEquals(expected, serialize(TextUtils.parse("&f&lChest")));
  }

  /** Cook and brew state labels colour themselves from the style's fill, which is a hex value. */
  @Test
  public void hexColouredRunIsReproducibleFromMiniMessage() {
    String expected = "{\"color\":\"#F2A535\",\"text\":\"Smelting 90%\"}";

    assertEquals(expected, serialize(Component.text("Smelting 90%").color(TextColor.color(0xF2A535))));
    assertEquals(expected, serialize(TextUtils.parse("<#f2a535>Smelting 90%")));
  }

  /** {@code withSurgeSuffix} appends exactly one run, and one append is one nest level. */
  @Test
  public void stateWithSurgeSuffixIsReproducibleAsANestedTag() {
    String expected = "{\"color\":\"#F2A535\",\"extra\":[{\"color\":\"#FFD978\",\"text\":\"  +2.5s\"}],\"text\":\"Smelting 90%\"}";

    assertEquals(expected, serialize(Component.text("Smelting 90%").color(TextColor.color(0xF2A535))
        .append(Component.text("  +2.5s").color(TextColor.color(0xFFD978)))));
    assertEquals(expected, serialize(TextUtils.parse("<#f2a535>Smelting 90%<#ffd978>  +2.5s")));
  }

  /**
   * Case (b): the real shape of {@code furnaceStats} and {@code brewStats} — a fuel run, a
   * {@code "  •  "} separator and a trailing stat, appended as three siblings. This is the shape
   * that needs the open-outer/closed-inner idiom, and it is the one Task 7 must write by hand; the
   * legacy-prefixed variant is the readable form, since only the inner runs need explicit tags.
   *
   * <p>It appears in five captured scenarios: {@code furnace_smelting}, {@code furnace_surging},
   * {@code blast_furnace_smelting}, {@code smoker_smelting} and {@code brewing_active}. The two
   * idle snapshots do not need the idiom at all, because there all three runs are dark grey and
   * {@code compact()} merges them back into a single run.
   */
  @Test
  public void threeAppendedRunsAreReproducibleWhenTheInnerTagsAreClosed() {
    String expected = "{\"color\":\"yellow\",\"extra\":[{\"color\":\"dark_gray\",\"text\":\"  •  \"},"
        + "{\"color\":\"green\",\"text\":\"XP +1.5\"}],\"text\":\"Fuel 15s\"}";

    assertEquals(expected, serialize(Component.text("Fuel 15s").color(NamedTextColor.YELLOW)
        .append(Component.text("  •  ").color(NamedTextColor.DARK_GRAY))
        .append(Component.text("XP +1.5").color(NamedTextColor.GREEN))));
    assertEquals(expected, serialize(TextUtils.parse(
        "<yellow>Fuel 15s<dark_gray>  •  </dark_gray><green>XP +1.5</green>")));
    assertEquals(expected, serialize(TextUtils.parse(
        "&eFuel 15s<dark_gray>  •  </dark_gray><green>XP +1.5</green>")));
  }

  /** The same idiom on {@code brewStats}, whose trailing run is light purple rather than green. */
  @Test
  public void brewingStatLineIsReproducibleWithTheSameIdiom() {
    String expected = "{\"color\":\"yellow\",\"extra\":[{\"color\":\"dark_gray\",\"text\":\"  •  \"},"
        + "{\"color\":\"light_purple\",\"text\":\"Bottles 2/3\"}],\"text\":\"Fuel 10/20\"}";

    assertEquals(expected, serialize(Component.text("Fuel 10/20").color(NamedTextColor.YELLOW)
        .append(Component.text("  •  ").color(NamedTextColor.DARK_GRAY))
        .append(Component.text("Bottles 2/3").color(NamedTextColor.LIGHT_PURPLE))));
    assertEquals(expected, serialize(TextUtils.parse(
        "&eFuel 10/20<dark_gray>  •  </dark_gray><light_purple>Bottles 2/3</light_purple>")));
  }

  // ---------------------------------------------------------------------
  // The two forms that look right and are not
  // ---------------------------------------------------------------------

  /**
   * Pinned so nobody reaches for either of these when writing a three-run label. Leaving all tags
   * open nests the third run inside the second; closing all of them flattens the runs but keeps
   * MiniMessage's empty root, which {@code compact()} will not fold away with more than one child.
   */
  @Test
  public void allOpenAndAllClosedTagsBothMissTheThreeRunShape() {
    String programmatic = serialize(Component.text("Fuel 15s").color(NamedTextColor.YELLOW)
        .append(Component.text("  •  ").color(NamedTextColor.DARK_GRAY))
        .append(Component.text("XP +1.5").color(NamedTextColor.GREEN)));

    String allOpen = serialize(TextUtils.parse("&eFuel 15s&8  •  &aXP +1.5"));
    assertEquals(
        "{\"color\":\"yellow\",\"extra\":[{\"color\":\"dark_gray\",\"extra\":"
            + "[{\"color\":\"green\",\"text\":\"XP +1.5\"}],\"text\":\"  •  \"}],\"text\":\"Fuel 15s\"}",
        allOpen
    );
    assertNotEquals(programmatic, allOpen);

    String allClosed = serialize(TextUtils.parse(
        "<yellow>Fuel 15s</yellow><dark_gray>  •  </dark_gray><green>XP +1.5</green>"));
    assertEquals(
        "{\"extra\":[{\"color\":\"yellow\",\"text\":\"Fuel 15s\"},{\"color\":\"dark_gray\",\"text\":\"  •  \"},"
            + "{\"color\":\"green\",\"text\":\"XP +1.5\"}],\"text\":\"\"}",
        allClosed
    );
    assertNotEquals(programmatic, allClosed);
  }
}
