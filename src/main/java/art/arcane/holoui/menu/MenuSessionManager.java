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
package art.arcane.holoui.menu;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.api.HoloClick;
import art.arcane.holoui.api.HoloClickHandler;
import art.arcane.holoui.api.HoloCloseReason;
import art.arcane.holoui.api.internal.ApiClickGuard;
import art.arcane.holoui.api.internal.ApiEvents;
import art.arcane.holoui.api.internal.ApiMenuHandle;
import art.arcane.holoui.config.HuiSettings;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.menu.components.ClickableComponent;
import art.arcane.holoui.menu.special.inventories.ContainerPreview;
import art.arcane.holoui.menu.special.inventories.ContainerPreviewAccess;
import art.arcane.holoui.menu.special.inventories.doc.CompiledPreviewDocument;
import art.arcane.holoui.menu.special.inventories.doc.PreviewDocumentRegistry;
import art.arcane.holoui.service.HoloUiTelemetry;
import art.arcane.holoui.util.common.ParticleUtils;
import art.arcane.volmlib.util.bukkit.Events;
import art.arcane.volmlib.util.bukkit.papi.PlayerSnapshotStore;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class MenuSessionManager {

  private final Map<Player, SessionHolder> holders = new ConcurrentHashMap<>();

  @Getter
  private final PlayerSnapshotStore<String> openMenus = new PlayerSnapshotStore<>();

  @Getter
  private final ApiClickGuard clickGuard = new ApiClickGuard(HoloUI.INSTANCE.getLogger(), System::currentTimeMillis,
      ApiClickGuard.DEFAULT_FAULT_LIMIT, ApiClickGuard.DEFAULT_SLOW_MILLIS);

  private SchedulerUtils.TaskHandle debugHitbox, debugPos;

  public MenuSessionManager() {
    applyDebugSettings();
    SchedulerUtils.scheduleSyncTask(HoloUI.INSTANCE, 1L, () -> {
      long tickStart = System.nanoTime();
      holders.forEach((player, holder) -> {
        Runnable tickTask = () -> {
          if (!player.isOnline()) {
            holder.close(HoloCloseReason.QUIT);
            holders.remove(player, holder);
            return;
          }

          if (holder.tick()) {
            holders.remove(player, holder);
          }
        };

        if (!SchedulerUtils.runEntity(HoloUI.INSTANCE, player, tickTask)) {
          return;
        }
      });
      HoloUiTelemetry.addTickNanos(System.nanoTime() - tickStart);
    }, false);
    Events.listen(HoloUI.INSTANCE, PlayerMoveEvent.class, EventPriority.HIGHEST, e -> {
      if (e.isCancelled() || e.getTo() == null) return;
      SessionHolder holder = holders.get(e.getPlayer());
      if (holder == null) return;
      holder.inspectSession(s -> s == null ? null : handleMovement(s, e.getFrom(), e.getTo()));
    });
    Events.listen(HoloUI.INSTANCE, PlayerDeathEvent.class, EventPriority.MONITOR, e -> {
      SessionHolder holder = holders.get(e.getEntity());
      if (holder == null) return;
      holder.inspectSession(s -> s != null && s.isCloseOnDeath() ? HoloCloseReason.DEATH : null);
    });
    Events.listen(HoloUI.INSTANCE, PlayerRespawnEvent.class, EventPriority.MONITOR, e -> {
      SessionHolder holder = holders.get(e.getPlayer());
      if (holder == null) return;
      holder.inspectSession(s -> {
        if (s == null) return null;
        if (!s.isValid(e.getRespawnLocation())) return HoloCloseReason.RESPAWN;
        s.move(e.getRespawnLocation().clone());
        return null;
      });
    });
    Events.listen(HoloUI.INSTANCE, PlayerTeleportEvent.class, EventPriority.MONITOR, e -> {
      SessionHolder holder = holders.get(e.getPlayer());
      if (holder == null || e.getTo() == null) return;
      holder.inspectSession(s -> {
        if (s == null) return null;
        if (!s.isValid(e.getTo()) || s.isCloseOnTeleport()) return HoloCloseReason.TELEPORT;
        s.move(e.getTo().clone());
        return null;
      });
    });
    Events.listen(HoloUI.INSTANCE, PlayerQuitEvent.class, e -> {
      SessionHolder holder = holders.remove(e.getPlayer());
      if (holder == null) return;
      holder.close(HoloCloseReason.QUIT);
    });
    Events.listen(HoloUI.INSTANCE, PlayerInteractEvent.class, EventPriority.MONITOR, this::dispatchClick);
    listenToInventoryPreview();
  }

  private void dispatchClick(PlayerInteractEvent event) {
    Action action = event.getAction();
    if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;

    SessionHolder holder = holders.get(event.getPlayer());
    if (holder == null) return;

    SessionHolder.ClickSnapshot snapshot = holder.snapshotClick();
    if (snapshot == null) return;

    event.setCancelled(true);
    Player player = event.getPlayer();
    ApiMenuHandle handle = snapshot.handle();
    String ownerName = handle == null ? null : handle.owner().name();

    for (ClickableComponent<?> component : snapshot.components()) {
      if (!ApiEvents.fireClick(player, snapshot.menuId(), component.getId(), ownerName)) continue;

      try {
        component.onClick();
      } catch (Exception ex) {
        HoloUI.logExceptionStack(false, ex, "Menu component %s of menu %s threw while handling a click from %s.",
            component.getId(), snapshot.menuId(), player.getName());
      }

      if (handle == null || !handle.live()) continue;

      HoloClickHandler apiHandler = handle.handler(component.getId());
      if (apiHandler == null) continue;

      clickGuard.dispatch(handle.owner(), apiHandler,
          new HoloClick(player, snapshot.menuId(), component.getId(), handle));
    }
  }

  public int holderCount() {
    return holders.size();
  }

  public boolean openLastSession(Player p) {
    SessionHolder holder = holders.get(p);
    if (holder == null) return false;

    String lastId = holder.lastSessionId();
    if (lastId == null) return false;

    MenuDefinitionData menu = HoloUI.INSTANCE.getConfigManager()
        .get(lastId)
        .orElse(null);
    if (menu == null) return false;

    return createNewSession(p, menu, null);
  }

  public void createNewSession(Player p, MenuDefinitionData menu) {
    createNewSession(p, menu, null);
  }

  public boolean createNewSession(Player p, MenuDefinitionData menu, ApiMenuHandle handle) {
    if (!ApiEvents.fireOpen(p, menu.getId(), handle == null ? null : handle.owner().name())) {
      return false;
    }

    holders.computeIfAbsent(p, this::newHolder).openSession(menu, handle);
    return true;
  }

  public boolean hasMenuSession(Player p) {
    SessionHolder holder = holders.get(p);
    return holder != null && holder.hasSession();
  }

  public boolean destroySessionFor(Player p, ApiMenuHandle handle, HoloCloseReason reason) {
    SessionHolder holder = holders.get(p);
    return holder != null && holder.closeSessionOf(handle, reason);
  }

  public void addPreviewSession(Player p, ContainerPreview session) {
    holders.computeIfAbsent(p, this::newHolder).openPreview(session);
  }

  public boolean hasPreviewSession(Player p) {
    SessionHolder holder = holders.get(p);
    return holder != null && holder.hasPreview();
  }

  public boolean destroySession(Player p, boolean history) {
    return destroySession(p, history, HoloCloseReason.CLOSED_BY_COMMAND);
  }

  public boolean destroySession(Player p, boolean history, HoloCloseReason reason) {
    SessionHolder holder = holders.get(p);
    if (holder == null) return false;
    return holder.closeSession(history, reason);
  }

  public void destroyAll() {
    holders.values().forEach(holder -> holder.close(HoloCloseReason.HOLOUI_SHUTDOWN));
    holders.clear();
    openMenus.clear();
  }

  private SessionHolder newHolder(Player player) {
    return new SessionHolder(player, openMenus);
  }

  public void destroyAllType(String id, Consumer<Player> consumer) {
    holders.forEach((player, holder) -> {
      Runnable destroyTask = () -> {
        boolean closed = holder.inspectSession(session ->
            session != null && session.getId().equalsIgnoreCase(id) ? HoloCloseReason.DEFINITION_RELOADED : null);
        if (closed) {
          consumer.accept(player);
        }
      };

      SchedulerUtils.runEntity(HoloUI.INSTANCE, player, destroyTask);
    });
  }

  public void refreshVisuals() {
    holders.forEach((player, holder) -> {
      Runnable refreshTask = holder::refreshVisuals;
      SchedulerUtils.runEntity(HoloUI.INSTANCE, player, refreshTask);
    });
  }

  public void closeAllPreviews() {
    holders.forEach((player, holder) -> {
      Runnable closeTask = holder::closePreview;
      SchedulerUtils.runEntity(HoloUI.INSTANCE, player, closeTask);
    });
  }

  public void applyDebugSettings() {
    controlHitboxDebug(Boolean.TRUE.equals(HuiSettings.DEBUG_HITBOX.value()));
    controlPositionDebug(Boolean.TRUE.equals(HuiSettings.DEBUG_SPACING.value()));
  }

  static HoloCloseReason handleMovement(MenuSession session, Location from, Location to) {
    if (session.isFreezePlayer()) {
      to.setX(from.getX());
      to.setY(from.getY());
      to.setZ(from.getZ());
      Player player = session.getPlayer();
      Vector velocity = player.getVelocity();
      if (velocity.getX() != 0 || velocity.getY() != 0 || velocity.getZ() != 0) {
        player.setVelocity(new Vector());
      }
      return null;
    }

    if (!session.isValid(to)) {
      return HoloCloseReason.MOVED_OUT_OF_RANGE;
    }

    if (session.isFollowPlayer()) {
      session.move(to.clone());
    }

    return null;
  }

  public void controlHitboxDebug(boolean hitbox) {
    if (hitbox && (debugHitbox == null || debugHitbox.isCancelled())) {
      debugHitbox = SchedulerUtils.scheduleSyncTask(HoloUI.INSTANCE, 2L, () -> holders.forEach((player, holder) -> {
        Runnable debugTask = () -> holder.onSession(session -> {
          if (session == null) return;
          session.getComponents().forEach(c -> {
            if (c instanceof ClickableComponent<?> o)
              o.highlightHitbox(player.getWorld());
          });
        });

        SchedulerUtils.runEntity(HoloUI.INSTANCE, player, debugTask);
      }), false);
    } else if (!hitbox && (debugHitbox != null && !debugHitbox.isCancelled()))
      debugHitbox.cancel();
  }

  //TODO Fix anchor particle
  public void controlPositionDebug(boolean positionDebug) {
    if (positionDebug && (debugPos == null || debugPos.isCancelled())) {
      debugPos = SchedulerUtils.scheduleSyncTask(HoloUI.INSTANCE, 2L, () -> holders.forEach((player, holder) -> {
        Runnable debugTask = () -> {
          World world = player.getWorld();
          holder.onSession(s -> {
            if (s == null) return;
            ParticleUtils.playParticle(world, s.getCenterInitialYAdjusted().toVector(), Color.YELLOW);
            s.getComponents().forEach(c -> ParticleUtils.playParticle(world, c.getLocation().toVector(), Color.ORANGE));
          });
        };

        SchedulerUtils.runEntity(HoloUI.INSTANCE, player, debugTask);
      }), false);
    } else if (!positionDebug && (debugPos != null && !debugPos.isCancelled()))
      debugPos.cancel();
  }

  private void listenToInventoryPreview() {
    SchedulerUtils.scheduleSyncTask(HoloUI.INSTANCE, 1L, () -> Bukkit.getOnlinePlayers().forEach(player -> {
      Runnable previewTask = () -> managePreviewEvents(player);
      if (!SchedulerUtils.runEntity(HoloUI.INSTANCE, player, previewTask)) {
        SessionHolder holder = holders.get(player);
        if (holder != null) {
          holder.closePreview();
        }
      }
    }), false);
  }

  private void managePreviewEvents(Player p) {
    try {
      PreviewTarget target = getLookedAtPreviewTarget(p);
      SessionHolder holder = holders.get(p);
      if (target == null) {
        if (holder != null) {
          holder.closePreview();
        }
        return;
      }

      if (holder == null) {
        holder = holders.computeIfAbsent(p, this::newHolder);
      }

      SessionHolder finalHolder = holder;
      holder.onPreview(preview -> {
        if (preview == null) {
          createNewPreviewSession(target, p);
          return;
        }

        if (!target.matches(preview)) {
          finalHolder.closePreview();
          createNewPreviewSession(target, p);
        }
      });
    } catch (Exception ex) {
      HoloUI.logExceptionStack(false, ex, "Failed to manage inventory preview for %s.", p.getName());
    }
  }

  /**
   * The block a player's eye ray hits within the preview look distance, ignoring passable blocks,
   * or null when the ray hits nothing (or the player's world is unloaded). Mirrors only the block
   * half of {@link #getLookedAtPreviewTarget}, without its per-document {@code isPreviewBlockType}
   * gate, so a caller can test one specific document's own matcher against whatever block the
   * player is actually looking at — which is what {@code /holoui previews dump} needs.
   */
  public Block lookedAtBlock(Player player) {
    Location eyeLocation = player.getEyeLocation();
    World world = eyeLocation.getWorld();
    if (world == null) {
      return null;
    }
    RayTraceResult blockResult = world.rayTraceBlocks(
        eyeLocation,
        eyeLocation.getDirection(),
        HuiSettings.previewLookDistance(),
        FluidCollisionMode.NEVER,
        true
    );
    Block targetBlock = blockResult == null ? null : blockResult.getHitBlock();
    return targetBlock != null && targetBlock.getType() != Material.AIR ? targetBlock : null;
  }

  private PreviewTarget getLookedAtPreviewTarget(Player player) {
    if (!ContainerPreviewAccess.isEnabled()) {
      return null;
    }
    Location eyeLocation = player.getEyeLocation();
    World world = eyeLocation.getWorld();
    if (world == null) {
      return null;
    }
    Vector direction = eyeLocation.getDirection();
    RayTraceResult blockResult = world.rayTraceBlocks(
        eyeLocation,
        direction,
        HuiSettings.previewLookDistance(),
        FluidCollisionMode.NEVER,
        true
    );
    PreviewTarget blockTarget = null;
    Block targetBlock = blockResult == null ? null : blockResult.getHitBlock();
    if (targetBlock != null && targetBlock.getType() != Material.AIR && isPreviewBlockType(targetBlock.getType())) {
      blockTarget = PreviewTarget.block(targetBlock);
    }
    RayTraceResult entityResult = world.rayTraceEntities(
        eyeLocation,
        direction,
        HuiSettings.previewLookDistance(),
        0.35D,
        this::isPreviewEntity
    );
    Entity targetEntity = entityResult == null ? null : entityResult.getHitEntity();
    if (targetEntity == null) {
      return blockTarget;
    }
    double blockDistance = hitDistanceSquared(eyeLocation, blockResult);
    double entityDistance = hitDistanceSquared(eyeLocation, entityResult);
    if (targetBlock != null && targetBlock.getType() != Material.AIR && blockDistance + 0.01D < entityDistance) {
      return blockTarget;
    }
    return PreviewTarget.entity(targetEntity);
  }

  private void createNewPreviewSession(PreviewTarget target, Player p) {
    ContainerPreviewAccess.ViewerAccess access = ContainerPreviewAccess.capture(p);
    if (target.block() != null) {
      createNewBlockPreviewSession(target.block(), p, access);
      return;
    }
    if (target.entity() != null) {
      createNewEntityPreviewSession(target.entity(), p, access);
    }
  }

  private void createNewBlockPreviewSession(Block b, Player p, ContainerPreviewAccess.ViewerAccess access) {
    Runnable createTask = () -> {
      if (b.getType() == Material.AIR) {
        return;
      }
      boolean canOpen = ContainerPreviewAccess.canOpen(p, b, access);
      if (!canOpen) {
        ContainerPreview lockedSession = ContainerPreview.locked(b, p);
        openPreviewIfCurrent(PreviewTarget.block(b), p, lockedSession);
        return;
      }
      if (isEnderChestDocument(b)) {
        Vector center = b.getLocation().toVector().add(new Vector(0.5D, 0.5D, 0.5D));
        Runnable buildTask = () -> {
          ContainerPreview newSession = ContainerPreview.forEnderChest(b, p, center);
          openPreviewIfCurrent(PreviewTarget.block(b), p, newSession);
        };
        if (!SchedulerUtils.runEntity(HoloUI.INSTANCE, p, buildTask)) {
          SessionHolder holder = holders.get(p);
          if (holder != null) {
            holder.closePreview();
          }
        }
        return;
      }
      Runnable buildTask = () -> {
        ContainerPreview newSession = ContainerPreview.forBlock(b, p);
        openPreviewIfCurrent(PreviewTarget.block(b), p, newSession);
      };
      buildTask.run();
    };

    if (!FoliaScheduler.runRegion(HoloUI.INSTANCE, b.getLocation(), createTask)
        && !FoliaScheduler.isFolia(HoloUI.INSTANCE.getServer())) {
      createTask.run();
    }
  }

  private void createNewEntityPreviewSession(Entity entity, Player p, ContainerPreviewAccess.ViewerAccess access) {
    Runnable createTask = () -> {
      if (!entity.isValid() || !isPreviewEntity(entity)) {
        return;
      }
      ContainerPreview newSession = ContainerPreviewAccess.canOpen(p, entity, access)
          ? ContainerPreview.forEntity(entity, p)
          : ContainerPreview.locked(entity, p);
      openPreviewIfCurrent(PreviewTarget.entity(entity), p, newSession);
    };

    if (!SchedulerUtils.runEntity(HoloUI.INSTANCE, entity, createTask)) {
      SessionHolder holder = holders.get(p);
      if (holder != null) {
        holder.closePreview();
      }
    }
  }

  private void openPreviewIfCurrent(PreviewTarget target, Player p, ContainerPreview newSession) {
    if (newSession == null) {
      return;
    }
    Runnable openTask = () -> {
      if (!newSession.canView() || !isStillLookingAt(p, target)) {
        newSession.close();
        return;
      }
      addPreviewSession(p, newSession);
    };
    if (!SchedulerUtils.runEntity(HoloUI.INSTANCE, p, openTask)) {
      openTask.run();
    }
  }

  private boolean isStillLookingAt(Player player, PreviewTarget target) {
    PreviewTarget current = getLookedAtPreviewTarget(player);
    return target.matches(current);
  }

  private double hitDistanceSquared(Location eyeLocation, RayTraceResult rayTraceResult) {
    if (rayTraceResult == null || rayTraceResult.getHitPosition() == null) {
      return Double.MAX_VALUE;
    }
    return eyeLocation.toVector().distanceSquared(rayTraceResult.getHitPosition());
  }

  private boolean isPreviewBlockType(Material type) {
    PreviewDocumentRegistry registry = previewRegistry();
    return registry != null && registry.isPreviewBlockType(type);
  }

  /**
   * Whether this block's winning document is the ender-chest one, i.e. whether the preview must be
   * built from the viewer's own ender chest rather than from a tile entity. Driven off the resolved
   * document instead of {@code Material.ENDER_CHEST} so that a higher-priority user document naming
   * the block still wins, and so that dropping {@code special} from {@code ender_chest.json} makes
   * the block take the ordinary block path rather than stranding it on a null preview.
   */
  private boolean isEnderChestDocument(Block block) {
    PreviewDocumentRegistry registry = previewRegistry();
    CompiledPreviewDocument.Resolved resolved = registry == null ? null : registry.forBlock(block.getType());
    return resolved != null && PreviewDocumentRegistry.SPECIAL_ENDER_CHEST.equals(resolved.doc().special());
  }

  private boolean isPreviewEntity(Entity entity) {
    PreviewDocumentRegistry registry = previewRegistry();
    return registry != null && registry.isPreviewEntity(entity);
  }

  private static PreviewDocumentRegistry previewRegistry() {
    HoloUI plugin = HoloUI.INSTANCE;
    return plugin == null ? null : plugin.getPreviewRegistry();
  }

  private record PreviewTarget(Block block, Entity entity) {

    private static PreviewTarget block(Block block) {
      return new PreviewTarget(block, null);
    }

    private static PreviewTarget entity(Entity entity) {
      return new PreviewTarget(null, entity);
    }

    private boolean matches(ContainerPreview preview) {
      if (block != null) {
        return preview.matchesBlock(block);
      }
      return preview.matchesEntity(entity);
    }

    private boolean matches(PreviewTarget other) {
      if (other == null) {
        return false;
      }
      if (block != null || other.block != null) {
        return block != null && other.block != null && block.equals(other.block);
      }
      return entity != null
          && other.entity != null
          && entity.getUniqueId().equals(other.entity.getUniqueId());
    }
  }
}
