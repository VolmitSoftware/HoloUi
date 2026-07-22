package art.arcane.holoui.menu.special.inventories;

import art.arcane.holoui.localization.HoloLocalization;
import art.arcane.holoui.localization.HoloMessages;
import art.arcane.holoui.util.common.TextUtils;
import art.arcane.volmlib.util.localization.MessageArgs;
import art.arcane.volmlib.util.localization.TextKey;
import com.google.common.collect.Lists;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.block.Barrel;
import org.bukkit.block.Beehive;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.block.Chest;
import org.bukkit.block.ChiseledBookshelf;
import org.bukkit.block.Container;
import org.bukkit.block.Dispenser;
import org.bukkit.block.Dropper;
import org.bukkit.block.Furnace;
import org.bukkit.block.Hopper;
import org.bukkit.block.Jukebox;
import org.bukkit.block.Shelf;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PreviewLayouts {

  private static final int PITCH = 20;
  private static final int WELL = 18;
  private static final int LINE = 12;

  private static final int TRAY_PAD = 4;
  private static final int PANEL_PAD = 7;
  private static final int TITLE_BAR_HEIGHT = 17;
  private static final int FRAME_BORDER = 3;
  private static final int GAP = 6;
  private static final int MIN_PANEL_HALF_WIDTH = 82;

  private static final int Z_FRAME = 0;
  private static final int Z_PANEL = 1;
  private static final int Z_TRAY = 2;
  private static final int Z_TITLE_BAR = 3;
  private static final int Z_WELL = 4;
  private static final int Z_LABEL = 6;

  private static final int PANEL_COLOR = 0xF21B1B22;
  private static final int TRAY_COLOR = 0xFF33333E;
  private static final int WELL_COLOR = 0xFF15151B;
  private static final int FRAME_ALPHA = 0xCC;
  private static final int TITLE_BAR_ALPHA = 0xE6;

  private static final int PROGRESS_SEGMENTS = 8;
  private static final int PROGRESS_SEGMENT_SIZE = 5;
  private static final int PROGRESS_SEGMENT_PITCH = 7;
  private static final int PROGRESS_START_X = -24;
  private static final int FLAME_SIZE = 12;
  private static final int VENT_COUNT = 3;
  private static final int VENT_SIZE = 6;
  private static final int VENT_PITCH = 8;
  private static final int[] SMOKE_WISP_X = {-8, 2};
  private static final int[] SMOKE_WISP_SIZE = {8, 6};
  private static final int[] SMOKER_SMOKE_COLORS = {0xFF5E5E66, 0xFF8A8A92, 0xFFB8B8C0};
  private static final boolean RECIPES_USED_AVAILABLE = hasRecipesUsed();

  private static final int SURGE_HOLD_TICKS = 60;

  private static final int BREW_TOTAL_TICKS = 400;
  private static final int BREW_SEGMENTS = 6;
  private static final int BREW_COLUMN_X = 44;
  private static final int BREW_COLUMN_TOP_Y = 18;
  private static final int BOTTLE_PITCH = 24;
  private static final int BOTTLE_Y = -12;
  private static final int INGREDIENT_Y = 16;
  private static final int BREW_FUEL_X = -44;
  private static final int FUEL_GAUGE_CELLS = 3;
  private static final int FUEL_GAUGE_BOTTOM_Y = -12;
  private static final int MAX_FUEL_LEVEL = 20;
  private static final int BREW_FILL_COLOR = 0xFFB152DA;
  private static final int BREW_PULSE_BRIGHT = 0xFFE8A6F2;
  private static final int BREW_PULSE_DIM = 0xFF5E2E78;
  private static final int BREW_BUBBLE_COLOR = 0xFFD98AE8;
  private static final int BREW_FUEL_COLOR = 0xFFF2A535;

  private record CookStyle(TextKey activeItemText, TextKey activeText, int fill, int pulseBright, int pulseDim, int chase, int idle, int[] flames) {
  }

  private static final CookStyle FURNACE_STYLE = new CookStyle(
      HoloMessages.PREVIEW_SMELTING_ITEM, HoloMessages.PREVIEW_SMELTING,
      0xFFF2A535, 0xFFFFD978, 0xFF8A5E1E, 0xFF9A5E22, 0xFF2A2A33,
      new int[]{0xFFE2641E, 0xFFF2A535, 0xFFF7D14C});
  private static final CookStyle BLAST_STYLE = new CookStyle(
      HoloMessages.PREVIEW_BLASTING_ITEM, HoloMessages.PREVIEW_BLASTING,
      0xFF6FB8E8, 0xFFE8F7FF, 0xFF2E5E80, 0xFF4FA8D8, 0xFF23262E,
      new int[]{0xFF4FA8E8, 0xFF8ED4FF, 0xFFE8F7FF});
  private static final CookStyle SMOKER_STYLE = new CookStyle(
      HoloMessages.PREVIEW_SMOKING_ITEM, HoloMessages.PREVIEW_SMOKING,
      0xFFC8893A, 0xFFF2C878, 0xFF6E4A1E, 0xFF8A6234, 0xFF2A2A33,
      new int[]{0xFFE25822, 0xFFF2A535, 0xFFC23B22});

  private PreviewLayouts() {
  }

  public static List<PreviewElement> forBlock(Block block, Player player) {
    BlockState state = block.getState();
    Material type = block.getType();
    if (type == Material.ENDER_CHEST) {
      return enderChest(player);
    }
    if (state instanceof ChiseledBookshelf bookshelf) {
      return chiseledBookshelf(bookshelf, type);
    }
    if (state instanceof Shelf shelf) {
      return shelf(shelf, type);
    }
    if (state instanceof BrewingStand stand) {
      return brewingStand(stand);
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
    ContainerPreviewTheme theme = ContainerPreviewTheme.mobileInventory(material(entity), inventory.getSize());
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
      return furnace(inventory, theme, title, type);
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

  private static List<PreviewElement> furnace(Inventory inventory, ContainerPreviewTheme theme, Component title, Material type) {
    FurnaceInventory furnaceInventory = (FurnaceInventory) inventory;
    CookStyle style = cookStyle(type);
    TimeFlowTracker flow = new TimeFlowTracker(false);
    List<PreviewElement> elements = Lists.newArrayList();
    elements.add(new PreviewElement.Slot(-2 * PITCH, PITCH / 2, Z_WELL, WELL, WELL_COLOR, inventory, 0));
    elements.add(new PreviewElement.Slot(-2 * PITCH, -PITCH / 2, Z_WELL, WELL, WELL_COLOR, inventory, 1));
    elements.add(new PreviewElement.Slot(2 * PITCH, PITCH / 2, Z_WELL, WELL, WELL_COLOR, inventory, 2));
    for (int segment = 0; segment < PROGRESS_SEGMENTS; segment++) {
      int segmentIndex = segment;
      int x = PROGRESS_START_X + segment * PROGRESS_SEGMENT_PITCH;
      elements.add(new PreviewElement.Cell(x, PITCH / 2, Z_WELL, PROGRESS_SEGMENT_SIZE, () -> progressSegmentColor(furnaceInventory, style, flow, segmentIndex)));
    }
    switch (type) {
      case BLAST_FURNACE -> {
        for (int vent = 0; vent < VENT_COUNT; vent++) {
          int phase = vent;
          int x = -PITCH + vent * VENT_PITCH;
          elements.add(new PreviewElement.Cell(x, -PITCH / 2, Z_WELL, VENT_SIZE, () -> burnCycleColor(furnaceInventory, style, flow, style.flames(), phase)));
        }
      }
      case SMOKER -> {
        elements.add(new PreviewElement.Cell(-PITCH, -PITCH / 2, Z_WELL, FLAME_SIZE, () -> burnCycleColor(furnaceInventory, style, flow, style.flames(), 0)));
        for (int wisp = 0; wisp < SMOKE_WISP_X.length; wisp++) {
          int phase = wisp + 1;
          elements.add(new PreviewElement.Cell(SMOKE_WISP_X[wisp], -PITCH / 2, Z_WELL, SMOKE_WISP_SIZE[wisp], () -> burnCycleColor(furnaceInventory, style, flow, SMOKER_SMOKE_COLORS, phase)));
        }
      }
      default -> elements.add(new PreviewElement.Cell(-PITCH, -PITCH / 2, Z_WELL, FLAME_SIZE, () -> burnCycleColor(furnaceInventory, style, flow, style.flames(), 0)));
    }
    elements.add(new PreviewElement.Label(0, -(PITCH + LINE), Z_LABEL, () -> furnaceState(furnaceInventory, style, flow), 0));
    elements.add(new PreviewElement.Label(0, -(PITCH + LINE * 2 + 2), Z_LABEL, () -> furnaceStats(furnaceInventory), 0));
    return styled(elements, theme, title);
  }

  private static List<PreviewElement> enderChest(Player player) {
    ContainerPreviewTheme theme = ContainerPreviewTheme.resolve(Material.ENDER_CHEST);
    Inventory inventory = player.getEnderChest();
    int rows = clamp(1, 6, (int) Math.ceil(inventory.getSize() / 9.0));
    return grid(inventory, 9, rows, theme, blockTitle(theme, null));
  }

  private static List<PreviewElement> chiseledBookshelf(ChiseledBookshelf bookshelf, Material type) {
    ContainerPreviewTheme theme = ContainerPreviewTheme.resolve(type);
    Component title = blockTitle(theme, null);
    return grid(bookshelf.getInventory(), 3, 2, theme, title);
  }

  private static List<PreviewElement> shelf(Shelf shelf, Material type) {
    ContainerPreviewTheme theme = ContainerPreviewTheme.resolve(type);
    Inventory inventory = shelf.getInventory();
    return row(inventory, inventory.getSize(), theme, blockTitle(theme, null));
  }

  private static List<PreviewElement> brewingStand(BrewingStand stand) {
    ContainerPreviewTheme theme = ContainerPreviewTheme.resolve(Material.BREWING_STAND);
    Inventory inventory = stand.getInventory();
    TimeFlowTracker flow = new TimeFlowTracker(true);
    List<PreviewElement> elements = Lists.newArrayList();
    elements.add(new PreviewElement.Slot(0, INGREDIENT_Y, Z_WELL, WELL, WELL_COLOR, inventory, 3));
    elements.add(new PreviewElement.Slot(BREW_FUEL_X, INGREDIENT_Y, Z_WELL, WELL, WELL_COLOR, inventory, 4));
    for (int bottle = 0; bottle < 3; bottle++) {
      elements.add(new PreviewElement.Slot((bottle - 1) * BOTTLE_PITCH, BOTTLE_Y, Z_WELL, WELL, WELL_COLOR, inventory, bottle));
    }
    for (int segment = 0; segment < BREW_SEGMENTS; segment++) {
      int segmentIndex = segment;
      int y = BREW_COLUMN_TOP_Y - segment * PROGRESS_SEGMENT_PITCH;
      elements.add(new PreviewElement.Cell(BREW_COLUMN_X, y, Z_WELL, PROGRESS_SEGMENT_SIZE, () -> brewSegmentColor(inventory, flow, segmentIndex)));
    }
    for (int cell = 0; cell < FUEL_GAUGE_CELLS; cell++) {
      int cellIndex = cell;
      int y = FUEL_GAUGE_BOTTOM_Y + cell * PROGRESS_SEGMENT_PITCH;
      elements.add(new PreviewElement.Cell(BREW_FUEL_X, y, Z_WELL, PROGRESS_SEGMENT_SIZE, () -> fuelCellColor(inventory, cellIndex)));
    }
    elements.add(new PreviewElement.Label(0, -(PITCH + LINE), Z_LABEL, () -> brewState(inventory, flow), 0));
    elements.add(new PreviewElement.Label(0, -(PITCH + LINE * 2 + 2), Z_LABEL, () -> brewStats(inventory), 0));
    return styled(elements, theme, blockTitle(theme, stand.getCustomName()));
  }

  private static int brewSegmentColor(Inventory inventory, TimeFlowTracker flow, int segmentIndex) {
    BrewingStand stand = standHolder(inventory);
    if (stand == null) {
      return WELL_COLOR;
    }
    int brewTime = stand.getBrewingTime();
    flow.sample(stand.getWorld().getGameTime(), brewTime);
    if (brewTime <= 0) {
      return WELL_COLOR;
    }
    double progress = Math.max(0.0, Math.min(1.0, 1.0 - brewTime / (double) BREW_TOTAL_TICKS));
    int filled = (int) Math.floor(progress * BREW_SEGMENTS);
    if (segmentIndex < filled) {
      if (flow.surging()) {
        return ((brewTime >> 1) + segmentIndex) % 2 == 0 ? BREW_PULSE_BRIGHT : BREW_FILL_COLOR;
      }
      return BREW_FILL_COLOR;
    }
    if (segmentIndex == filled) {
      return ((brewTime >> 2) & 1) == 0 ? BREW_PULSE_BRIGHT : BREW_PULSE_DIM;
    }
    if (((brewTime >> 2) + segmentIndex * 2) % 7 == 0) {
      return BREW_BUBBLE_COLOR;
    }
    return WELL_COLOR;
  }

  private static int fuelCellColor(Inventory inventory, int cellIndex) {
    BrewingStand stand = standHolder(inventory);
    if (stand == null) {
      return WELL_COLOR;
    }
    int fuel = Math.max(0, Math.min(MAX_FUEL_LEVEL, stand.getFuelLevel()));
    int filled = (int) Math.ceil((fuel / (double) MAX_FUEL_LEVEL) * FUEL_GAUGE_CELLS);
    return cellIndex < filled ? BREW_FUEL_COLOR : WELL_COLOR;
  }

  private static Component brewState(Inventory inventory, TimeFlowTracker flow) {
    BrewingStand stand = standHolder(inventory);
    if (stand == null) {
      return Component.text(text(HoloMessages.PREVIEW_IDLE)).color(NamedTextColor.GRAY);
    }
    int brewTime = stand.getBrewingTime();
    flow.sample(stand.getWorld().getGameTime(), brewTime);
    Component state;
    if (brewTime > 0) {
      double progress = Math.max(0.0, Math.min(1.0, 1.0 - brewTime / (double) BREW_TOTAL_TICKS));
      int percent = (int) Math.round(progress * 100.0);
      state = Component.text(text(
          HoloMessages.PREVIEW_BREWING,
          MessageArgs.builder().untrusted("percent", percent).build()
      )).color(TextColor.color(BREW_FILL_COLOR & 0xFFFFFF));
    } else {
      boolean hasIngredient = !empty(inventory.getItem(3));
      boolean hasBottles = !empty(inventory.getItem(0)) || !empty(inventory.getItem(1)) || !empty(inventory.getItem(2));
      if (hasIngredient && hasBottles && stand.getFuelLevel() <= 0) {
        state = Component.text(text(HoloMessages.PREVIEW_NEEDS_BLAZE_POWDER)).color(NamedTextColor.RED);
      } else if (hasIngredient && hasBottles) {
        state = Component.text(text(HoloMessages.PREVIEW_WAITING)).color(NamedTextColor.GRAY);
      } else if (hasBottles) {
        state = Component.text(text(HoloMessages.PREVIEW_NO_INGREDIENT)).color(NamedTextColor.GRAY);
      } else {
        state = Component.text(text(HoloMessages.PREVIEW_EMPTY)).color(NamedTextColor.DARK_GRAY);
      }
    }
    return withSurgeSuffix(state, flow, BREW_PULSE_BRIGHT);
  }

  private static Component brewStats(Inventory inventory) {
    BrewingStand stand = standHolder(inventory);
    if (stand == null) {
      return Component.empty();
    }
    int fuel = stand.getFuelLevel();
    Component line = fuel > 0
        ? Component.text(text(
            HoloMessages.PREVIEW_FUEL_LEVEL,
            MessageArgs.builder()
                .untrusted("fuel", fuel)
                .untrusted("maximum", MAX_FUEL_LEVEL)
                .build()
        )).color(NamedTextColor.YELLOW)
        : Component.text(text(HoloMessages.PREVIEW_NO_FUEL)).color(NamedTextColor.DARK_GRAY);
    int bottles = 0;
    for (int slot = 0; slot < 3; slot++) {
      if (!empty(inventory.getItem(slot))) {
        bottles++;
      }
    }
    return line.append(Component.text("  •  ").color(NamedTextColor.DARK_GRAY))
        .append(Component.text(text(
            HoloMessages.PREVIEW_BOTTLES,
            MessageArgs.builder()
                .untrusted("bottles", bottles)
                .untrusted("maximum", 3)
                .build()
        )).color(bottles > 0 ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.DARK_GRAY));
  }

  private static BrewingStand standHolder(Inventory inventory) {
    return inventory.getHolder() instanceof BrewingStand holder ? holder : null;
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

  private static Component furnaceState(FurnaceInventory inventory, CookStyle style, TimeFlowTracker flow) {
    Furnace furnace = furnaceHolder(inventory);
    if (furnace == null) {
      return Component.text(text(HoloMessages.PREVIEW_IDLE)).color(NamedTextColor.GRAY);
    }
    flow.sample(furnace.getWorld().getGameTime(), furnace.getCookTime());
    ItemStack input = inventory.getSmelting();
    boolean hasInput = !empty(input);
    int cookTime = furnace.getCookTime();
    int cookTimeTotal = furnace.getCookTimeTotal();
    int burnTime = furnace.getBurnTime();
    Component state;
    if (cookTime > 0 && cookTimeTotal > 0) {
      int percent = (int) Math.round((cookTime * 100.0) / cookTimeTotal);
      String stateText = hasInput
          ? text(
              style.activeItemText(),
              MessageArgs.builder()
                  .untrusted("item", ContainerPreviewTheme.toReadableName(input.getType().name()))
                  .untrusted("percent", percent)
                  .build()
          )
          : text(
              style.activeText(),
              MessageArgs.builder().untrusted("percent", percent).build()
          );
      state = Component.text(stateText).color(TextColor.color(style.fill() & 0xFFFFFF));
    } else if (burnTime > 0 && hasInput) {
      state = Component.text(text(HoloMessages.PREVIEW_HEATING)).color(NamedTextColor.YELLOW);
    } else if (hasInput && empty(inventory.getFuel())) {
      state = Component.text(text(HoloMessages.PREVIEW_NEEDS_FUEL)).color(NamedTextColor.RED);
    } else if (!hasInput) {
      state = Component.text(text(HoloMessages.PREVIEW_NO_INPUT)).color(NamedTextColor.GRAY);
    } else {
      state = Component.text(text(HoloMessages.PREVIEW_WAITING)).color(NamedTextColor.GRAY);
    }
    return withSurgeSuffix(state, flow, style.pulseBright());
  }

  private static Component withSurgeSuffix(Component state, TimeFlowTracker flow, int brightColor) {
    if (!flow.surging()) {
      return state;
    }
    return state.append(Component.text(text(
        HoloMessages.PREVIEW_SURGE_SUFFIX,
        MessageArgs.builder().untrusted("seconds", formatCompact(flow.surgeSeconds())).build()
    )).color(TextColor.color(brightColor & 0xFFFFFF)));
  }

  private static Component furnaceStats(FurnaceInventory inventory) {
    Furnace furnace = furnaceHolder(inventory);
    if (furnace == null) {
      return Component.empty();
    }
    int burnTime = furnace.getBurnTime();
    Component line;
    if (burnTime > 0) {
      line = Component.text(text(
          HoloMessages.PREVIEW_FUEL_SECONDS,
          MessageArgs.builder().untrusted("seconds", burnTime / 20).build()
      )).color(NamedTextColor.YELLOW);
    } else if (!empty(inventory.getFuel())) {
      line = Component.text(text(HoloMessages.PREVIEW_FUEL_READY)).color(NamedTextColor.GRAY);
    } else {
      line = Component.text(text(HoloMessages.PREVIEW_NO_FUEL)).color(NamedTextColor.DARK_GRAY);
    }
    double xp = bankedXp(furnace);
    if (xp >= 0) {
      Component xpText = xp > 0
          ? Component.text(text(
              HoloMessages.PREVIEW_XP_GAIN,
              MessageArgs.builder().untrusted("experience", formatCompact(xp)).build()
          )).color(NamedTextColor.GREEN)
          : Component.text(text(HoloMessages.PREVIEW_XP_ZERO)).color(NamedTextColor.DARK_GRAY);
      line = line.append(Component.text("  •  ").color(NamedTextColor.DARK_GRAY)).append(xpText);
    }
    return line;
  }

  private static int progressSegmentColor(FurnaceInventory inventory, CookStyle style, TimeFlowTracker flow, int segmentIndex) {
    Furnace furnace = furnaceHolder(inventory);
    if (furnace == null) {
      return WELL_COLOR;
    }
    int cookTime = furnace.getCookTime();
    flow.sample(furnace.getWorld().getGameTime(), cookTime);
    int cookTimeTotal = furnace.getCookTimeTotal();
    if (cookTime > 0 && cookTimeTotal > 0) {
      int filled = (int) Math.floor((cookTime / (double) cookTimeTotal) * PROGRESS_SEGMENTS);
      if (segmentIndex < filled) {
        if (flow.surging()) {
          return ((cookTime >> 1) + segmentIndex) % 2 == 0 ? style.pulseBright() : style.fill();
        }
        return style.fill();
      }
      if (segmentIndex == filled) {
        return ((cookTime >> 2) & 1) == 0 ? style.pulseBright() : style.pulseDim();
      }
      return WELL_COLOR;
    }
    int burnTime = furnace.getBurnTime();
    if (burnTime > 0 && segmentIndex == (burnTime >> 2) % PROGRESS_SEGMENTS) {
      return style.chase();
    }
    return WELL_COLOR;
  }

  private static int burnCycleColor(FurnaceInventory inventory, CookStyle style, TimeFlowTracker flow, int[] palette, int phase) {
    Furnace furnace = furnaceHolder(inventory);
    if (furnace == null || furnace.getBurnTime() <= 0) {
      return style.idle();
    }
    flow.sample(furnace.getWorld().getGameTime(), furnace.getCookTime());
    int shift = flow.surging() ? 1 : 2;
    return palette[((furnace.getBurnTime() >> shift) + phase) % palette.length];
  }

  private static double bankedXp(Furnace furnace) {
    if (!RECIPES_USED_AVAILABLE) {
      return -1;
    }
    double xp = 0;
    for (Map.Entry<CookingRecipe<?>, Integer> entry : furnace.getRecipesUsed().entrySet()) {
      xp += entry.getKey().getExperience() * entry.getValue();
    }
    return xp;
  }

  private static String formatCompact(double value) {
    if (value == Math.floor(value)) {
      return String.valueOf((long) value);
    }
    return String.format(Locale.ROOT, "%.1f", value);
  }

  private static CookStyle cookStyle(Material type) {
    return switch (type) {
      case BLAST_FURNACE -> BLAST_STYLE;
      case SMOKER -> SMOKER_STYLE;
      default -> FURNACE_STYLE;
    };
  }

  private static Furnace furnaceHolder(FurnaceInventory inventory) {
    return inventory.getHolder() instanceof Furnace holder ? holder : null;
  }

  private static boolean hasRecipesUsed() {
    try {
      Furnace.class.getMethod("getRecipesUsed");
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  private static final class TimeFlowTracker {

    private final boolean countsDown;
    private long lastGameTime = Long.MIN_VALUE;
    private int lastValue;
    private long surgeUntil = Long.MIN_VALUE;
    private double surgeSeconds;

    private TimeFlowTracker(boolean countsDown) {
      this.countsDown = countsDown;
    }

    private void sample(long gameTime, int value) {
      if (gameTime == lastGameTime) {
        return;
      }
      if (lastGameTime != Long.MIN_VALUE && value > 0) {
        long elapsed = gameTime - lastGameTime;
        long gained = countsDown ? (long) lastValue - value : (long) value - lastValue;
        if (elapsed > 0 && elapsed <= 100 && gained > elapsed + 1) {
          surgeSeconds = (gained - elapsed) / 20.0;
          surgeUntil = gameTime + SURGE_HOLD_TICKS;
        }
      }
      lastGameTime = gameTime;
      lastValue = value;
    }

    private boolean surging() {
      return lastGameTime <= surgeUntil;
    }

    private double surgeSeconds() {
      return surgeSeconds;
    }
  }

  private static Component jukeboxStatus(Block block) {
    if (block.getState() instanceof Jukebox jukebox && jukebox.hasRecord()) {
      String name = ContainerPreviewTheme.toReadableName(jukebox.getRecord().getType().name());
      if (jukebox.isPlaying()) {
        return Component.text(text(
            HoloMessages.PREVIEW_DISC_PLAYING,
            MessageArgs.builder().untrusted("disc", name).build()
        )).color(NamedTextColor.GREEN);
      }
      return Component.text(text(
          HoloMessages.PREVIEW_DISC_LOADED,
          MessageArgs.builder().untrusted("disc", name).build()
      )).color(NamedTextColor.GRAY);
    }
    return Component.text(text(HoloMessages.PREVIEW_NO_DISC)).color(NamedTextColor.DARK_GRAY);
  }

  private static Component beeText(Block block) {
    int[] hive = hiveState(block);
    return Component.text(text(
        HoloMessages.PREVIEW_BEES_AND_HONEY,
        MessageArgs.builder()
            .untrusted("bees", hive[0])
            .untrusted("maximumBees", hive[1])
            .untrusted("honey", hive[2])
            .untrusted("maximumHoney", hive[3])
            .build()
    )).color(NamedTextColor.GOLD);
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
      return Component.text(text(
          HoloMessages.PREVIEW_CAULDRON_EMPTY,
          MessageArgs.builder()
              .untrusted("level", level[0])
              .untrusted("maximum", level[1])
              .build()
      )).color(NamedTextColor.GRAY);
    }
    return Component.text(text(
        HoloMessages.PREVIEW_CAULDRON_LEVEL,
        MessageArgs.builder()
            .untrusted("level", level[0])
            .untrusted("maximum", level[1])
            .build()
    )).color(NamedTextColor.AQUA);
  }

  private static String text(TextKey key) {
    return HoloLocalization.globalText(key);
  }

  private static String text(TextKey key, MessageArgs arguments) {
    return HoloLocalization.globalText(key, arguments);
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
