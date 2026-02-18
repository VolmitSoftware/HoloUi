package art.arcane.holoui.menu.special.inventories;

import art.arcane.holoui.config.HuiSettings;
import art.arcane.holoui.config.MenuComponentData;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class InventoryPreviewLayout {

  private static final float PANEL_Z = -0.14F;
  private static final float SLOT_ITEM_Z = 0.00F;
  private static final float PANEL_SLOT_MARGIN = 0.34F;
  private static final float SLOT_SIZE_FALLBACK = 0.44F;
  private static final float PANEL_LINE_HEIGHT = 3.5F / 16F;
  private static final float PANEL_CHAR_WIDTH = PANEL_LINE_HEIGHT / 2F;
  private static final float MIN_PANEL_SCALE = 1.00F;
  private static final float MAX_PANEL_SCALE = 8.00F;

  private InventoryPreviewLayout() {
  }

  public static void addSlot(InventoryPreviewMenu<?> menu, List<MenuComponentData> components, Inventory inventory, int slot, String id, float x, float y) {
    components.add(menu.component("slot" + id, x, y, SLOT_ITEM_Z, new InventorySlotComponent.Data(inventory, slot)));
  }

  public static void addSlot(InventoryPreviewMenu<?> menu, List<MenuComponentData> components, ContainerPreviewTheme theme, Inventory inventory, int slot, String id, float x, float y) {
    addSlot(menu, components, inventory, slot, id, x, y);
  }

  public static void addPanel(InventoryPreviewMenu<?> menu, List<MenuComponentData> components, ContainerPreviewTheme theme, String idPrefix, float leftX, float rightX, float topY, float bottomY, int columns, int rows) {
    int panelColumns = Math.max(1, columns);
    int panelRows = Math.max(1, rows);
    String panel = renderPanel(theme, panelColumns, panelRows);
    float centerX = (leftX + rightX) / 2F;
    float centerY = (topY + bottomY) / 2F;
    float panelScale = panelScale(leftX, rightX, topY, bottomY, panel);
    components.add(menu.component("panel_" + idPrefix, centerX, centerY, PANEL_Z, new MapPanelComponent.Data(panel, panelScale)));
  }

  public static List<Integer> visibleSlots(Inventory inventory, int maxSlots) {
    int limit = Math.min(maxSlots, inventory.getSize());
    List<Integer> slots = new ArrayList<>();
    boolean showEmpty = HuiSettings.showPreviewEmptySlots();
    for (int slot = 0; slot < limit; slot++) {
      if (showEmpty || !isEmpty(inventory.getItem(slot))) {
        slots.add(slot);
      }
    }
    return slots;
  }

  private static boolean isEmpty(ItemStack stack) {
    return stack == null || stack.getType() == Material.AIR || stack.getAmount() < 1;
  }

  private static float panelScale(float leftX, float rightX, float topY, float bottomY, String panel) {
    float width = Math.abs(rightX - leftX);
    float height = Math.abs(topY - bottomY);
    float desiredWidth = width + SLOT_SIZE_FALLBACK + PANEL_SLOT_MARGIN;
    float desiredHeight = height + SLOT_SIZE_FALLBACK + PANEL_SLOT_MARGIN;
    String[] lines = panel.split("\n");
    int maxChars = 1;
    for (String line : lines) {
      maxChars = Math.max(maxChars, visibleLength(line));
    }
    float baseWidth = Math.max(0.05F, maxChars * PANEL_CHAR_WIDTH);
    float baseHeight = Math.max(0.05F, lines.length * PANEL_LINE_HEIGHT);
    float widthScale = desiredWidth / baseWidth;
    float heightScale = desiredHeight / baseHeight;
    float blended = (widthScale * 0.72F) + (heightScale * 0.28F);
    if (blended < MIN_PANEL_SCALE) {
      return MIN_PANEL_SCALE;
    }
    return Math.min(MAX_PANEL_SCALE, blended);
  }

  private static String renderPanel(ContainerPreviewTheme theme, int columns, int rows) {
    String trim = theme == null ? "&6" : theme.trimColorCode();
    String panel = theme == null ? "&8" : theme.panelColorCode();
    String slot = theme == null ? "&0" : theme.slotColorCode();
    String accent = trim;
    String light = "&7";
    int slotWidth = 3;
    int slotGap = 1;
    int sidePad = 2;
    int innerWidth = (columns * slotWidth) + ((columns - 1) * slotGap) + (sidePad * 2);
    int totalWidth = innerWidth + 2;
    List<String> lines = new ArrayList<>();
    lines.add(trim + repeat("█", totalWidth));
    lines.add(trim + "█" + panel + repeat("▓", innerWidth) + trim + "█");
    for (int row = 0; row < rows; row++) {
      StringBuilder top = new StringBuilder();
      StringBuilder fill = new StringBuilder();
      top.append(panel).append(repeat("▓", sidePad));
      fill.append(panel).append(repeat("▓", sidePad));
      for (int column = 0; column < columns; column++) {
        top.append(accent).append(repeat("▒", slotWidth));
        fill.append(slot).append(repeat("░", slotWidth));
        if (column < columns - 1) {
          top.append(panel).append(repeat("▓", slotGap));
          fill.append(panel).append(repeat("▓", slotGap));
        }
      }
      top.append(panel).append(repeat("▓", sidePad));
      fill.append(panel).append(repeat("▓", sidePad));
      lines.add(trim + "█" + top + trim + "█");
      lines.add(trim + "█" + fill + trim + "█");
      if (row < rows - 1) {
        lines.add(trim + "█" + panel + repeat("▓", innerWidth) + trim + "█");
      }
    }
    lines.add(trim + "█" + panel + repeat("▓", innerWidth) + trim + "█");
    lines.add(trim + "█" + light + repeat("▄", innerWidth) + trim + "█");
    lines.add(trim + repeat("█", totalWidth));
    return String.join("\n", lines);
  }

  private static int visibleLength(String text) {
    int length = 0;
    boolean color = false;
    char[] chars = text.toCharArray();
    for (int i = 0; i < chars.length; i++) {
      char c = chars[i];
      if (color) {
        color = false;
        continue;
      }
      if (c == '&' && i + 1 < chars.length) {
        color = true;
        continue;
      }
      length++;
    }
    return Math.max(1, length);
  }

  private static String repeat(String token, int count) {
    if (count <= 0) {
      return "";
    }
    String[] pieces = new String[count];
    Arrays.fill(pieces, token);
    return String.join("", pieces);
  }
}
