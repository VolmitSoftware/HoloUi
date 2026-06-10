package art.arcane.holoui.menu.special.inventories;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.config.HuiSettings;
import art.arcane.holoui.menu.DisplayEntityManager;
import art.arcane.holoui.util.common.DisplayEntity;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ContainerPreview {

  private static final double VANILLA_TEXT_BLOCKS_PER_PIXEL = 1.0 / 40.0;
  private static final int LAYOUT_PIXELS_PER_BLOCK = 160;
  private static final double SPACE_BACKGROUND_WIDTH_PX = 5.0;
  private static final double SPACE_BACKGROUND_HEIGHT_PX = 9.0;
  private static final double LABEL_LINE_PX = 9.0;
  private static final double Z_DEPTH_PER_UNIT = 0.03;

  private static final int ITEM_PX = 14;
  private static final byte BILLBOARD_CENTER = 3;
  private static final byte ITEM_DISPLAY_CONTEXT = 8;
  private static final int ITEM_BRIGHTNESS = 0xF000F0;
  private static final byte TEXT_FLAGS = 0;
  private static final byte TEXT_OPACITY_VISIBLE = (byte) 0xFF;
  private static final byte TEXT_OPACITY_HIDDEN = 0;

  private static final double COMFORT_DISTANCE = 1.5;
  private static final double HALF_BLOCK = 0.5;
  private static final double EDGE_MARGIN = 0.12;
  private static final double MIN_DISTANCE = 0.12;
  private static final double MIN_SCALE_FACTOR = 0.08;
  private static final double SCALE_EPSILON = 0.02;
  private static final int REFRESH_INTERVAL = 4;

  private final Player player;
  private final Block block;
  private final Entity entity;
  private final String permissionKey;
  private final Vector targetCenter;
  private final List<PreviewElement> elements;
  private final List<Rendered> rendered = new ArrayList<>();

  private Location anchor;
  private Vector right = new Vector(1, 0, 0);
  private Vector up = new Vector(0, 1, 0);
  private Vector forward = new Vector(0, 0, 1);
  private double scaleTarget = 1.0;
  private double appliedScale = 1.0;
  private int ticks;
  private volatile boolean refreshScheduled;
  private boolean open;

  private ContainerPreview(Player player, Block block, Entity entity, String permissionKey, Vector targetCenter, List<PreviewElement> elements) {
    this.player = player;
    this.block = block;
    this.entity = entity;
    this.permissionKey = permissionKey;
    this.targetCenter = targetCenter;
    this.elements = elements;
    for (PreviewElement element : elements) {
      Rendered r = new Rendered(element);
      seed(r);
      this.rendered.add(r);
    }
  }

  public static ContainerPreview forBlock(Block block, Player player) {
    List<PreviewElement> elements = PreviewLayouts.forBlock(block);
    if (elements == null || elements.isEmpty()) {
      return null;
    }
    Vector center = block.getLocation().toVector().add(new Vector(0.5, 0.5, 0.5));
    return new ContainerPreview(player, block, null, block.getType().getKey().getKey(), center, elements);
  }

  public static ContainerPreview forEntity(Entity entity, Player player) {
    List<PreviewElement> elements = PreviewLayouts.forEntity(entity);
    if (elements == null || elements.isEmpty()) {
      return null;
    }
    Vector center = entity.getLocation().toVector().add(new Vector(0, Math.max(0.35, entity.getHeight() * 0.5), 0));
    return new ContainerPreview(player, null, entity, entity.getType().getKey().getKey(), center, elements);
  }

  public boolean hasPermission() {
    return !HuiSettings.PREVIEW_BY_PERMISSION.value() || player.hasPermission("holoui.preview." + permissionKey);
  }

  public boolean matchesBlock(Block lookingAt) {
    return block != null && lookingAt != null && lookingAt.equals(block);
  }

  public boolean matchesEntity(Entity lookingAt) {
    return entity != null && lookingAt != null && entity.isValid() && lookingAt.getUniqueId().equals(entity.getUniqueId());
  }

  public void open() {
    recomputeAnchor();
    appliedScale = scaleTarget;
    for (Rendered r : rendered) {
      spawn(r);
    }
    open = true;
  }

  public void tick() {
    if (!open) {
      return;
    }
    recomputeAnchor();
    boolean scaleDirty = Math.abs(scaleTarget - appliedScale) > appliedScale * SCALE_EPSILON;
    if (scaleDirty) {
      appliedScale = scaleTarget;
    }
    for (Rendered r : rendered) {
      reposition(r);
      if (scaleDirty) {
        rescale(r);
      }
      applyDynamic(r);
    }
    if (ticks++ % REFRESH_INTERVAL == 0) {
      scheduleRefresh();
    }
  }

  public void refreshVisuals() {
    if (!open) {
      return;
    }
    close();
    open();
  }

  public void close() {
    open = false;
    for (Rendered r : rendered) {
      despawn(r.background);
      despawn(r.item);
      despawn(r.count);
    }
  }

  private void seed(Rendered r) {
    switch (r.element) {
      case PreviewElement.Slot slot -> {
        ItemStack stack = slot.inventory().getItem(slot.slot());
        ItemStack initial = isEmpty(stack) ? null : stack.clone();
        r.appliedItem = initial;
        r.pendingItem = initial;
      }
      case PreviewElement.Cell cell -> {
        int color = cell.color().getAsInt();
        r.appliedColor = color;
        r.pendingColor = color;
      }
      case PreviewElement.Label label -> {
        Component text = safe(label.text().get());
        r.appliedText = text;
        r.pendingText = text;
      }
      case PreviewElement.Panel ignored -> {
      }
    }
  }

  private void spawn(Rendered r) {
    switch (r.element) {
      case PreviewElement.Panel panel -> {
        r.backgroundPx = new double[]{panel.x(), panel.y() - panel.height() / 2.0, panel.z()};
        r.background = spawnBackground(r.backgroundPx, panel.width(), panel.height(), panel.color());
      }
      case PreviewElement.Cell cell -> {
        r.backgroundPx = new double[]{cell.x(), cell.y() - cell.size() / 2.0, cell.z()};
        r.background = spawnBackground(r.backgroundPx, cell.size(), cell.size(), r.appliedColor);
      }
      case PreviewElement.Slot slot -> {
        r.backgroundPx = new double[]{slot.x(), slot.y() - slot.size() / 2.0, slot.z()};
        r.background = spawnBackground(r.backgroundPx, slot.size(), slot.size(), slot.wellColor());
        if (r.appliedItem != null) {
          r.itemPx = new double[]{slot.x(), slot.y(), slot.z() + 1};
          r.item = spawnItem(r.itemPx, r.appliedItem);
          spawnCountIfNeeded(r, slot);
        }
      }
      case PreviewElement.Label label -> {
        r.backgroundPx = new double[]{label.x(), label.y() - LABEL_LINE_PX / 2.0, label.z()};
        r.background = spawnText(r.backgroundPx, r.appliedText, label.backgroundColor());
      }
    }
  }

  private void reposition(Rendered r) {
    if (r.background != null && r.backgroundPx != null) {
      DisplayEntityManager.goTo(r.background, at(r.backgroundPx));
    }
    if (r.item != null && r.itemPx != null) {
      DisplayEntityManager.goTo(r.item, at(r.itemPx));
    }
    if (r.count != null && r.countPx != null) {
      DisplayEntityManager.goTo(r.count, at(r.countPx));
    }
  }

  private void applyDynamic(Rendered r) {
    switch (r.element) {
      case PreviewElement.Cell ignored -> {
        if (r.background != null && r.pendingColor != r.appliedColor) {
          r.appliedColor = r.pendingColor;
          DisplayEntityManager.changeTextBackground(r.background, r.appliedColor);
        }
      }
      case PreviewElement.Label ignored -> {
        Component pending = r.pendingText;
        if (r.background != null && pending != null && !pending.equals(r.appliedText)) {
          r.appliedText = pending;
          DisplayEntityManager.changeName(r.background, pending);
        }
      }
      case PreviewElement.Slot slot -> applySlot(r, slot);
      case PreviewElement.Panel ignored -> {
      }
    }
  }

  private void applySlot(Rendered r, PreviewElement.Slot slot) {
    ItemStack pending = r.pendingItem;
    boolean pendingEmpty = isEmpty(pending);
    boolean appliedEmpty = r.appliedItem == null;
    if (pendingEmpty && appliedEmpty) {
      return;
    }
    if (pendingEmpty) {
      despawn(r.item);
      despawn(r.count);
      r.item = null;
      r.count = null;
      r.appliedItem = null;
      return;
    }
    if (!pending.equals(r.appliedItem)) {
      r.appliedItem = pending.clone();
      if (r.item == null) {
        r.itemPx = new double[]{slot.x(), slot.y(), slot.z() + 1};
        r.item = spawnItem(r.itemPx, r.appliedItem);
      } else {
        DisplayEntityManager.changeItem(r.item, r.appliedItem);
      }
      updateCount(r, slot, r.appliedItem.getAmount());
    }
  }

  private void scheduleRefresh() {
    if (refreshScheduled) {
      return;
    }
    refreshScheduled = true;
    Runnable read = () -> {
      try {
        for (Rendered r : rendered) {
          readDynamic(r);
        }
      } finally {
        refreshScheduled = false;
      }
    };
    if (block != null) {
      if (!FoliaScheduler.runRegion(HoloUI.INSTANCE, block.getLocation(), read)) {
        if (FoliaScheduler.isFolia(HoloUI.INSTANCE.getServer())) {
          refreshScheduled = false;
        } else {
          read.run();
        }
      }
      return;
    }
    if (entity != null) {
      if (!SchedulerUtils.runEntity(HoloUI.INSTANCE, entity, read)) {
        refreshScheduled = false;
      }
      return;
    }
    read.run();
  }

  private void readDynamic(Rendered r) {
    switch (r.element) {
      case PreviewElement.Slot slot -> {
        ItemStack stack = slot.inventory().getItem(slot.slot());
        r.pendingItem = isEmpty(stack) ? null : stack.clone();
      }
      case PreviewElement.Cell cell -> r.pendingColor = cell.color().getAsInt();
      case PreviewElement.Label label -> r.pendingText = safe(label.text().get());
      case PreviewElement.Panel ignored -> {
      }
    }
  }

  private UUID spawnBackground(double[] px, int width, int height, int color) {
    DisplayEntity displayEntity = DisplayEntity.Builder.textDisplay(
        Component.text(" "), at(px), bgScaleX(width), bgScaleY(height), 1F, BILLBOARD_CENTER, TEXT_FLAGS, color, TEXT_OPACITY_HIDDEN);
    UUID uuid = DisplayEntityManager.add(displayEntity);
    DisplayEntityManager.spawn(uuid, player);
    return uuid;
  }

  private void rescale(Rendered r) {
    switch (r.element) {
      case PreviewElement.Panel panel -> DisplayEntityManager.changeScale(r.background, bgScaleX(panel.width()), bgScaleY(panel.height()), 1F);
      case PreviewElement.Cell cell -> DisplayEntityManager.changeScale(r.background, bgScaleX(cell.size()), bgScaleY(cell.size()), 1F);
      case PreviewElement.Slot slot -> {
        DisplayEntityManager.changeScale(r.background, bgScaleX(slot.size()), bgScaleY(slot.size()), 1F);
        if (r.item != null) {
          float scale = itemScale();
          DisplayEntityManager.changeScale(r.item, scale, scale, scale);
        }
        if (r.count != null) {
          DisplayEntityManager.changeScale(r.count, baseTextScale(), baseTextScale(), 1F);
        }
      }
      case PreviewElement.Label ignored -> DisplayEntityManager.changeScale(r.background, baseTextScale(), baseTextScale(), 1F);
    }
  }

  private float bgScaleX(int widthPx) {
    return (float) (baseTextScale() * (widthPx / SPACE_BACKGROUND_WIDTH_PX));
  }

  private float bgScaleY(int heightPx) {
    return (float) (baseTextScale() * (heightPx / SPACE_BACKGROUND_HEIGHT_PX));
  }

  private float itemScale() {
    return (float) (ITEM_PX * pixel());
  }

  private UUID spawnText(double[] px, Component text, int backgroundColor) {
    float scale = baseTextScale();
    DisplayEntity displayEntity = DisplayEntity.Builder.textDisplay(
        text, at(px), scale, scale, 1F, BILLBOARD_CENTER, TEXT_FLAGS, backgroundColor, TEXT_OPACITY_VISIBLE);
    UUID uuid = DisplayEntityManager.add(displayEntity);
    DisplayEntityManager.spawn(uuid, player);
    return uuid;
  }

  private UUID spawnItem(double[] px, ItemStack stack) {
    float scale = itemScale();
    DisplayEntity displayEntity = DisplayEntity.Builder.itemDisplay(stack, at(px), scale, BILLBOARD_CENTER, ITEM_DISPLAY_CONTEXT);
    displayEntity.brightness(ITEM_BRIGHTNESS);
    UUID uuid = DisplayEntityManager.add(displayEntity);
    DisplayEntityManager.spawn(uuid, player);
    return uuid;
  }

  private void spawnCountIfNeeded(Rendered r, PreviewElement.Slot slot) {
    if (r.appliedItem == null || r.appliedItem.getAmount() <= 1) {
      return;
    }
    updateCount(r, slot, r.appliedItem.getAmount());
  }

  private void updateCount(Rendered r, PreviewElement.Slot slot, int amount) {
    if (amount <= 1) {
      despawn(r.count);
      r.count = null;
      return;
    }
    Component text = Component.text(amount).color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD);
    if (r.count == null) {
      r.countPx = new double[]{slot.x() + slot.size() / 2.0 - 3.0, slot.y() - slot.size() / 2.0 + LABEL_LINE_PX - 1.0, slot.z() + 2};
      DisplayEntity displayEntity = DisplayEntity.Builder.textDisplay(
          text, at(r.countPx), baseTextScale(), baseTextScale(), 1F, BILLBOARD_CENTER, TEXT_FLAGS, 0, TEXT_OPACITY_VISIBLE);
      r.count = DisplayEntityManager.add(displayEntity);
      DisplayEntityManager.spawn(r.count, player);
    } else {
      DisplayEntityManager.changeName(r.count, text);
    }
  }

  private void despawn(UUID uuid) {
    if (uuid != null) {
      DisplayEntityManager.delete(uuid, player);
    }
  }

  private double pixel() {
    return appliedScale / LAYOUT_PIXELS_PER_BLOCK;
  }

  private float baseTextScale() {
    return (float) (pixel() / VANILLA_TEXT_BLOCKS_PER_PIXEL);
  }

  private Location at(double[] px) {
    return at(px[0], px[1], px[2]);
  }

  private Location at(double pxX, double pxY, double pxZ) {
    double unit = pixel();
    double depth = appliedScale * Z_DEPTH_PER_UNIT;
    Location loc = anchor.clone();
    loc.add(right.clone().multiply(pxX * unit));
    loc.add(up.clone().multiply(pxY * unit));
    loc.add(forward.clone().multiply(-pxZ * depth));
    loc.setYaw(0F);
    loc.setPitch(0F);
    return loc;
  }

  private void recomputeAnchor() {
    Location eye = player.getEyeLocation();
    Vector look = eye.getDirection();
    if (look.lengthSquared() <= 1.0E-6) {
      look = new Vector(0, 0, 1);
    }
    look.normalize();
    Vector worldUp = new Vector(0, 1, 0);
    Vector computedRight = look.clone().crossProduct(worldUp);
    if (computedRight.lengthSquared() <= 1.0E-6) {
      double yaw = Math.toRadians(eye.getYaw());
      computedRight = new Vector(Math.cos(yaw), 0, Math.sin(yaw));
    }
    computedRight.normalize();
    Vector computedUp = computedRight.clone().crossProduct(look);
    if (computedUp.lengthSquared() <= 1.0E-6) {
      computedUp = worldUp;
    }
    computedUp.normalize();

    double distanceToTarget = eye.toVector().distance(targetCenter);
    double surfaceDistance = Math.max(0.0, distanceToTarget - HALF_BLOCK);
    double anchorDistance = Math.max(MIN_DISTANCE, Math.min(COMFORT_DISTANCE, surfaceDistance - EDGE_MARGIN));
    double distanceFactor = Math.max(MIN_SCALE_FACTOR, Math.min(1.0, anchorDistance / COMFORT_DISTANCE));

    this.scaleTarget = HuiSettings.previewScale() * distanceFactor;
    this.forward = look;
    this.right = computedRight;
    this.up = computedUp;
    this.anchor = eye.clone().add(look.clone().multiply(anchorDistance));
    this.anchor.setYaw(0F);
    this.anchor.setPitch(0F);
  }

  private static Component safe(Component component) {
    return component == null ? Component.empty() : component;
  }

  private static boolean isEmpty(ItemStack stack) {
    return stack == null || stack.getType() == Material.AIR || stack.getAmount() < 1;
  }

  private static final class Rendered {
    private final PreviewElement element;
    private UUID background;
    private UUID item;
    private UUID count;
    private double[] backgroundPx;
    private double[] itemPx;
    private double[] countPx;
    private ItemStack appliedItem;
    private int appliedColor;
    private Component appliedText;
    private volatile ItemStack pendingItem;
    private volatile int pendingColor;
    private volatile Component pendingText;

    private Rendered(PreviewElement element) {
      this.element = element;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ContainerPreview other)) {
      return false;
    }
    return Objects.equals(block, other.block) && Objects.equals(entity, other.entity) && Objects.equals(player, other.player);
  }

  @Override
  public int hashCode() {
    return Objects.hash(player, block, entity);
  }
}
