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

import art.arcane.holoui.menu.special.inventories.PreviewElement;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Measures a document's content and wraps it in the card chrome: outer frame, panel, optional
 * tray behind the cell grid, title bar, and title label.
 *
 * <p>The arithmetic, the constants, the integer division, and the emitted element order are all
 * frozen: the golden snapshots in {@code src/test/resources/golden} are the regression record for
 * the whole JSON preview engine and they pin every one of these numbers. Nothing here may be
 * "cleaned up": {@code (panelTop + titleBarBottom) / 2} truncates towards zero and the snapshots
 * depend on it.
 *
 * <p>The accent colour arrives as an int and the minimum panel half-width as a parameter, which the
 * parser defaults to {@code MIN_PANEL_HALF_WIDTH} when a document omits {@code card.minHalfWidth}.
 * The title arrives as a {@link Supplier} because {@link PreviewElement.Label} holds one, even
 * though a card title is evaluated once per build rather than per frame.
 */
public final class CardFramer {

  private static final int WELL = 18;
  private static final int LINE = 12;

  private static final int TRAY_PAD = 4;
  private static final int PANEL_PAD = 7;
  private static final int TITLE_BAR_HEIGHT = 17;
  private static final int FRAME_BORDER = 3;
  private static final int GAP = 6;

  private static final int Z_FRAME = 0;
  private static final int Z_PANEL = 1;
  private static final int Z_TRAY = 2;
  private static final int Z_TITLE_BAR = 3;
  private static final int Z_LABEL = 6;

  private static final int PANEL_COLOR = 0xF21B1B22;
  private static final int TRAY_COLOR = 0xFF33333E;
  private static final int FRAME_ALPHA = 0xCC;
  private static final int TITLE_BAR_ALPHA = 0xE6;

  private CardFramer() {
  }

  public static List<PreviewElement> frame(
      List<PreviewElement> content,
      Supplier<Component> title,
      int accentColor,
      int minHalfWidth
  ) {
    boolean hasGrid = false;
    int gridLeft = 0;
    int gridRight = 0;
    int gridBottom = 0;
    int gridTop = 0;
    int contentTop = Integer.MIN_VALUE;
    int contentBottom = Integer.MAX_VALUE;
    for (PreviewElement element : content) {
      int halfHeight = element instanceof PreviewElement.Label ? LINE / 2 : WELL / 2;
      contentTop = Math.max(contentTop, element.y() + halfHeight);
      contentBottom = Math.min(contentBottom, element.y() - halfHeight);
      boolean isCell = element instanceof PreviewElement.Slot || element instanceof PreviewElement.Cell;
      if (isCell) {
        int left = element.x() - WELL / 2;
        int right = element.x() + WELL / 2;
        int bottom = element.y() - WELL / 2;
        int top = element.y() + WELL / 2;
        if (!hasGrid) {
          gridLeft = left;
          gridRight = right;
          gridBottom = bottom;
          gridTop = top;
          hasGrid = true;
        } else {
          gridLeft = Math.min(gridLeft, left);
          gridRight = Math.max(gridRight, right);
          gridBottom = Math.min(gridBottom, bottom);
          gridTop = Math.max(gridTop, top);
        }
      }
    }
    if (contentTop == Integer.MIN_VALUE) {
      contentTop = WELL / 2;
      contentBottom = -WELL / 2;
    }

    int panelHalfWidth = Math.max(minHalfWidth, (hasGrid ? (gridRight - gridLeft) / 2 : WELL / 2) + PANEL_PAD);
    int titleBarBottom = contentTop + GAP;
    int panelTop = titleBarBottom + TITLE_BAR_HEIGHT;
    int panelBottom = contentBottom - PANEL_PAD;
    int panelCenterY = (panelTop + panelBottom) / 2;
    int panelWidth = panelHalfWidth * 2;
    int panelHeight = panelTop - panelBottom;

    int accent = accentColor;
    int frameColor = (FRAME_ALPHA << 24) | (accent & 0xFFFFFF);
    int titleBarColor = (TITLE_BAR_ALPHA << 24) | (accent & 0xFFFFFF);

    List<PreviewElement> styled = new ArrayList<>();
    styled.add(new PreviewElement.Panel(0, panelCenterY, Z_FRAME, panelWidth + FRAME_BORDER * 2, panelHeight + FRAME_BORDER * 2, frameColor));
    styled.add(new PreviewElement.Panel(0, panelCenterY, Z_PANEL, panelWidth, panelHeight, PANEL_COLOR));
    if (hasGrid) {
      int trayWidth = (gridRight - gridLeft) + TRAY_PAD * 2;
      int trayHeight = (gridTop - gridBottom) + TRAY_PAD * 2;
      int trayCenterX = (gridRight + gridLeft) / 2;
      int trayCenterY = (gridTop + gridBottom) / 2;
      styled.add(new PreviewElement.Panel(trayCenterX, trayCenterY, Z_TRAY, trayWidth, trayHeight, TRAY_COLOR));
    }
    int titleBarCenterY = (panelTop + titleBarBottom) / 2;
    styled.add(new PreviewElement.Panel(0, titleBarCenterY, Z_TITLE_BAR, panelWidth, TITLE_BAR_HEIGHT, titleBarColor));
    styled.add(new PreviewElement.Label(0, titleBarCenterY, Z_LABEL, title, 0));
    styled.addAll(content);
    return styled;
  }
}
