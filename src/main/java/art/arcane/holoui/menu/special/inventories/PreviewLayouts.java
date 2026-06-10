package art.arcane.holoui.menu.special.inventories;

import art.arcane.holoui.util.common.TextUtils;
import com.google.common.collect.Lists;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Beehive;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.block.Container;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Dropper;
import org.bukkit.block.Furnace;
import org.bukkit.block.Hopper;
import org.bukkit.block.Jukebox;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class PreviewLayouts {

  private static final int PITCH = 20;
  private static final int WELL = 18;
  private static final int LINE = 12;

  private static final int TRAY_PAD = 4;
  private static final int PANEL_PAD = 7;
  private static final int TITLE_BAR_HEIGHT = 17;
  private static final int FRAME_BORDER = 3;
  private static final int GAP = 3;
  private static final int MIN_PANEL_HALF_WIDTH = 82;

  private static final int Z_FRAME = 0;
  private static final int Z_PANEL = 1;
  private static final int Z_TRAY = 2;
  private static final int Z_TITLE_BAR = 2;
  private static final int Z_WELL = 3;
  private static final int Z_LABEL = 4;

  private static final int PANEL_COLOR = 0xF21B1B22;
  private static final int TRAY_COLOR = 0xFF33333E;
  private static final int WELL_COLOR = 0xFF15151B;
  private static final int FRAME_ALPHA = 0xCC;
  private static final int TITLE_BAR_ALPHA = 0xE6;

  private PreviewLayouts() {
  }

  public static List<PreviewElement> forBlock(Block block) {
    BlockState state = block.getState();
    Material type = block.getType();
    if (type == Material.CHISELED_BOOKSHELF || type == Material.BOOKSHELF || type.name().endsWith("_SHELF")) {
      return bookshelf(state, type);
    }
    if (state instanceof Container container) {
      return container(container, type);
    }
    if (state instanceof Jukebox jukebox) {
      return jukebox(block, jukebox);
    }
    if (type == Material.BEEHIVE || type == Material.BEE_NEST) {
      return beehive(block);
    }
    if (isCauldron(type)) {
      return cauldron(block);
    }
    return null;
  }

  public static List<PreviewElement> forEntity(Entity entity) {
    if (!(entity instanceof InventoryHolder holder)) {
      return null;
    }
    Inventory inventory = holder.getInventory();
    ContainerPreviewTheme theme = ContainerPreviewTheme.minecart(material(entity), inventory.getSize());
    Component title = entityTitle(entity, theme);
    if (entity instanceof HopperMinecart) {
      return row(inventory, Math.min(5, inventory.getSize()), theme, title);
    }
    int rows = clamp(1, 6, (int) Math.ceil(inventory.getSize() / 9.0));
    return grid(inventory, 9, rows, theme, title);
  }

  private static List<PreviewElement> container(Container container, Material type) {
    Inventory inventory = container.getInventory();
    ContainerPreviewTheme theme = ContainerPreviewTheme.resolve(type);
    Component title = blockTitle(theme, container.getCustomName());
    if (inventory instanceof FurnaceInventory) {
      return furnace(inventory, theme, title);
    }
    if (container instanceof Hopper) {
      return row(inventory, 5, theme, title);
    }
    if (container instanceof Dispenser || container instanceof Dropper) {
      return grid(inventory, 3, 3, theme, title);
    }
    int rows = clamp(1, 6, (int) Math.ceil(inventory.getSize() / 9.0));
    return grid(inventory, 9, rows, theme, title);
  }

  private static List<PreviewElement> grid(Inventory inventory, int columns, int rows, ContainerPreviewTheme theme, Component title) {
    List<PreviewElement> elements = Lists.newArrayList();
    int limit = Math.min(columns * rows, inventory.getSize());
    for (int slot = 0; slot < limit; slot++) {
      int column = slot % columns;
      int rowIndex = slot / columns;
      int x = (int) Math.round((column - (columns - 1) / 2.0) * PITCH);
      int y = (int) Math.round(((rows - 1) / 2.0 - rowIndex) * PITCH);
      elements.add(new PreviewElement.Slot(x, y, Z_WELL, WELL, WELL_COLOR, inventory, slot));
    }
    return styled(elements, theme, title);
  }

  private static List<PreviewElement> row(Inventory inventory, int slots, ContainerPreviewTheme theme, Component title) {
    List<PreviewElement> elements = Lists.newArrayList();
    for (int slot = 0; slot < Math.min(slots, inventory.getSize()); slot++) {
      int x = (int) Math.round((slot - (slots - 1) / 2.0) * PITCH);
      elements.add(new PreviewElement.Slot(x, 0, Z_WELL, WELL, WELL_COLOR, inventory, slot));
    }
    return styled(elements, theme, title);
  }

  private static List<PreviewElement> furnace(Inventory inventory, ContainerPreviewTheme theme, Component title) {
    List<PreviewElement> elements = Lists.newArrayList();
    elements.add(new PreviewElement.Slot(-PITCH, PITCH / 2, Z_WELL, WELL, WELL_COLOR, inventory, 0));
    elements.add(new PreviewElement.Slot(-PITCH, -PITCH / 2, Z_WELL, WELL, WELL_COLOR, inventory, 1));
    elements.add(new PreviewElement.Slot(PITCH, 0, Z_WELL, WELL, WELL_COLOR, inventory, 2));
    FurnaceInventory furnaceInventory = (FurnaceInventory) inventory;
    elements.add(new PreviewElement.Label(0, -(PITCH + LINE), Z_LABEL, () -> furnaceState(furnaceInventory), 0));
    return styled(elements, theme, title);
  }

  private static List<PreviewElement> bookshelf(BlockState state, Material type) {
    ContainerPreviewTheme theme = ContainerPreviewTheme.resolve(type);
    Component title = blockTitle(theme, null);
    Inventory inventory;
    if (state instanceof ChiseledBookshelf bookshelf) {
      inventory = bookshelf.getInventory();
    } else {
      inventory = Bukkit.createInventory(null, 9);
      if (type == Material.BOOKSHELF) {
        for (int slot = 0; slot < 6; slot++) {
          inventory.setItem(slot, new ItemStack(Material.BOOK));
        }
      }
    }
    return grid(inventory, 3, 2, theme, title);
  }

  private static List<PreviewElement> jukebox(Block block, Jukebox jukebox) {
    ContainerPreviewTheme theme = ContainerPreviewTheme.resolve(Material.JUKEBOX);
    List<PreviewElement> elements = Lists.newArrayList();
    elements.add(new PreviewElement.Slot(0, 0, Z_WELL, WELL, WELL_COLOR, jukebox.getInventory(), 0));
    elements.add(new PreviewElement.Label(0, -(WELL / 2 + LINE), Z_LABEL, () -> jukeboxStatus(block), 0));
    return styled(elements, theme, blockTitle(theme, null));
  }

  private static List<PreviewElement> beehive(Block block) {
    ContainerPreviewTheme theme = ContainerPreviewTheme.resolve(Material.BEEHIVE);
    List<PreviewElement> elements = Lists.newArrayList();
    int cells = 3;
    for (int index = 0; index < cells; index++) {
      int slotIndex = index;
      int x = (int) Math.round((index - (cells - 1) / 2.0) * PITCH);
      elements.add(new PreviewElement.Cell(x, 0, Z_WELL, WELL, () -> beeCellColor(block, slotIndex)));
    }
    elements.add(new PreviewElement.Label(0, WELL / 2 + LINE, Z_LABEL, () -> beeText(block), 0));
    return styled(elements, theme, blockTitle(theme, null));
  }

  private static List<PreviewElement> cauldron(Block block) {
    ContainerPreviewTheme theme = ContainerPreviewTheme.resolve(block.getType());
    List<PreviewElement> elements = Lists.newArrayList();
    int cells = 3;
    for (int index = 0; index < cells; index++) {
      int slotIndex = index;
      int x = (int) Math.round((index - (cells - 1) / 2.0) * PITCH);
      elements.add(new PreviewElement.Cell(x, 0, Z_WELL, WELL, () -> cauldronCellColor(block, slotIndex)));
    }
    elements.add(new PreviewElement.Label(0, WELL / 2 + LINE, Z_LABEL, () -> cauldronText(block), 0));
    return styled(elements, theme, blockTitle(theme, null));
  }

  private static List<PreviewElement> styled(List<PreviewElement> content, ContainerPreviewTheme theme, Component title) {
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

    int panelHalfWidth = Math.max(MIN_PANEL_HALF_WIDTH, (hasGrid ? (gridRight - gridLeft) / 2 : WELL / 2) + PANEL_PAD);
    int titleBarBottom = contentTop + GAP;
    int panelTop = titleBarBottom + TITLE_BAR_HEIGHT;
    int panelBottom = contentBottom - PANEL_PAD;
    int panelCenterY = (panelTop + panelBottom) / 2;
    int panelWidth = panelHalfWidth * 2;
    int panelHeight = panelTop - panelBottom;

    int accent = theme.accentColor();
    int frameColor = (FRAME_ALPHA << 24) | (accent & 0xFFFFFF);
    int titleBarColor = (TITLE_BAR_ALPHA << 24) | (accent & 0xFFFFFF);

    List<PreviewElement> styled = Lists.newArrayList();
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
    styled.add(new PreviewElement.Label(0, titleBarCenterY, Z_LABEL, () -> title, 0));
    styled.addAll(content);
    return styled;
  }

  private static Component blockTitle(ContainerPreviewTheme theme, String customName) {
    if (customName != null && !customName.isBlank()) {
      return TextUtils.parse("&f&l" + customName);
    }
    return Component.text(theme.plainTitle()).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD);
  }

  private static Component entityTitle(Entity entity, ContainerPreviewTheme theme) {
    String name = entity.getCustomName();
    if (name != null && !name.isBlank()) {
      return TextUtils.parse("&f&l" + name);
    }
    return Component.text(theme.plainTitle()).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD);
  }

  private static Component furnaceState(FurnaceInventory inventory) {
    Furnace furnace = inventory.getHolder() instanceof Furnace holder ? holder : null;
    if (furnace == null) {
      return Component.text("Idle").color(NamedTextColor.GRAY);
    }
    boolean hasInput = !empty(inventory.getSmelting());
    int cookTime = furnace.getCookTime();
    int cookTimeTotal = furnace.getCookTimeTotal();
    int burnTime = furnace.getBurnTime();
    if (cookTime > 0 && cookTimeTotal > 0) {
      int percent = (int) Math.round((cookTime * 100.0) / cookTimeTotal);
      return Component.text("Cooking " + percent + "%").color(NamedTextColor.GOLD);
    }
    if (burnTime > 0 && hasInput) {
      return Component.text("Heating").color(NamedTextColor.YELLOW);
    }
    if (hasInput && empty(inventory.getFuel())) {
      return Component.text("Needs fuel").color(NamedTextColor.RED);
    }
    if (!hasInput) {
      return Component.text("No input").color(NamedTextColor.GRAY);
    }
    return Component.text("Waiting").color(NamedTextColor.GRAY);
  }

  private static Component jukeboxStatus(Block block) {
    if (block.getState() instanceof Jukebox jukebox && jukebox.hasRecord()) {
      String name = ContainerPreviewTheme.toReadableName(jukebox.getRecord().getType().name());
      if (jukebox.isPlaying()) {
        return Component.text("Playing " + name).color(NamedTextColor.GREEN);
      }
      return Component.text("Loaded " + name).color(NamedTextColor.GRAY);
    }
    return Component.text("No disc").color(NamedTextColor.DARK_GRAY);
  }

  private static Component beeText(Block block) {
    int[] hive = hiveState(block);
    return Component.text("Bees " + hive[0] + "/" + hive[1] + "   Honey " + hive[2] + "/" + hive[3]).color(NamedTextColor.GOLD);
  }

  private static int beeCellColor(Block block, int index) {
    return index < hiveState(block)[0] ? 0xFF8A6618 : WELL_COLOR;
  }

  private static int[] hiveState(Block block) {
    int bees = 0;
    int maxBees = 3;
    int honey = 0;
    int maxHoney = 5;
    BlockState state = block.getState();
    if (state instanceof Beehive beehive) {
      bees = Math.max(0, beehive.getEntityCount());
      maxBees = Math.max(1, beehive.getMaxEntities());
    }
    BlockData data = state.getBlockData();
    if (data instanceof org.bukkit.block.data.type.Beehive beehiveData) {
      honey = beehiveData.getHoneyLevel();
      maxHoney = Math.max(1, beehiveData.getMaximumHoneyLevel());
    }
    return new int[]{bees, maxBees, honey, maxHoney};
  }

  private static Component cauldronText(Block block) {
    int[] level = cauldronLevel(block);
    if (level[0] <= 0) {
      return Component.text("Empty " + level[0] + "/" + level[1]).color(NamedTextColor.GRAY);
    }
    return Component.text("Level " + level[0] + "/" + level[1]).color(NamedTextColor.AQUA);
  }

  private static int cauldronCellColor(Block block, int index) {
    int[] level = cauldronLevel(block);
    return index < level[2] ? cauldronFill(block.getType()) : WELL_COLOR;
  }

  private static int[] cauldronLevel(Block block) {
    Material type = block.getType();
    if (type == Material.CAULDRON) {
      return new int[]{0, 3, 0};
    }
    BlockData data = block.getBlockData();
    if (data instanceof Levelled levelled) {
      int max = Math.max(1, levelled.getMaximumLevel());
      int current = Math.max(0, levelled.getLevel());
      int visible = (int) Math.ceil((current / (double) max) * 3.0);
      return new int[]{current, max, visible};
    }
    return new int[]{3, 3, 3};
  }

  private static int cauldronFill(Material type) {
    return switch (type) {
      case LAVA_CAULDRON -> 0xFFA14C16;
      case POWDER_SNOW_CAULDRON -> 0xFFD8E5EF;
      default -> 0xFF2E5E8C;
    };
  }

  private static boolean isCauldron(Material type) {
    return type == Material.CAULDRON || type == Material.WATER_CAULDRON || type == Material.LAVA_CAULDRON || type == Material.POWDER_SNOW_CAULDRON;
  }

  private static Material material(Entity entity) {
    Material material = Material.matchMaterial(entity.getType().name());
    return material == null ? Material.MINECART : material;
  }

  private static boolean empty(ItemStack stack) {
    return stack == null || stack.getType() == Material.AIR || stack.getAmount() < 1;
  }

  private static int clamp(int min, int max, int value) {
    return Math.max(min, Math.min(max, value));
  }
}
