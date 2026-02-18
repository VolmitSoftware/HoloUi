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
import art.arcane.holoui.config.HuiSettings;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.menu.components.ClickableComponent;
import art.arcane.holoui.menu.special.BlockMenuSession;
import art.arcane.holoui.menu.special.inventories.InventoryPreviewMenu;
import art.arcane.holoui.util.common.ParticleUtils;
import art.arcane.volmlib.util.bukkit.Events;
import art.arcane.volmlib.util.scheduling.FoliaScheduler;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
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

  private SchedulerUtils.TaskHandle debugHitbox, debugPos;

  public MenuSessionManager() {
    controlHitboxDebug(HuiSettings.DEBUG_HITBOX.value());
    controlPositionDebug(HuiSettings.DEBUG_SPACING.value());
    SchedulerUtils.scheduleSyncTask(HoloUI.INSTANCE, 1L, () -> holders.forEach((player, holder) -> {
      Runnable tickTask = () -> {
        if (!player.isOnline()) {
          holder.close();
          holders.remove(player, holder);
          return;
        }

        if (holder.tick()) {
          holders.remove(player, holder);
        }
      };

      if (!SchedulerUtils.runEntity(HoloUI.INSTANCE, player, tickTask)) {
        holders.remove(player, holder);
        holder.close();
      }
    }), false);
    Events.listen(HoloUI.INSTANCE, PlayerMoveEvent.class, EventPriority.HIGHEST, e -> {
      if (e.isCancelled() || e.getTo() == null) return;
      SessionHolder holder = holders.get(e.getPlayer());
      if (holder == null) return;
      holder.onSession(s -> {
        if (s == null) return;

        if (!s.isValid(e.getTo())) {
          holder.closeSession(false);
          return;
        }

        if (s.isFreezePlayer()) {
          Location from = e.getFrom();
          Location to = e.getTo();
          to.setX(from.getX());
          to.setY(from.getY());
          to.setZ(from.getZ());
          Player player = e.getPlayer();
          Vector velocity = player.getVelocity();
          if (velocity.getX() != 0 || velocity.getY() != 0 || velocity.getZ() != 0) {
            player.setVelocity(new Vector());
          }
          return;
        }

        if (s.isFollowPlayer()) {
          s.move(e.getTo().clone(), true);
        }
      });
    });
    Events.listen(HoloUI.INSTANCE, PlayerDeathEvent.class, EventPriority.MONITOR, e -> {
      SessionHolder holder = holders.get(e.getEntity());
      if (holder == null) return;
      holder.onSession(s -> {
        if (s == null || !s.isCloseOnDeath()) return;
        holder.closeSession(false);
      });
    });
    Events.listen(HoloUI.INSTANCE, PlayerRespawnEvent.class, EventPriority.MONITOR, e -> {
      SessionHolder holder = holders.get(e.getPlayer());
      if (holder == null) return;
      holder.onSession(s -> {
        if (s == null || !s.isValid(e.getRespawnLocation()))
          holder.closeSession(false);
        else s.move(e.getRespawnLocation().clone(), true);
      });
    });
    Events.listen(HoloUI.INSTANCE, PlayerTeleportEvent.class, EventPriority.MONITOR, e -> {
      SessionHolder holder = holders.get(e.getPlayer());
      if (holder == null || e.getTo() == null) return;
      holder.onSession(s -> {
        if (s == null || !s.isValid(e.getTo()) || s.isCloseOnTeleport())
          holder.closeSession(false);
        else s.move(e.getTo().clone(), true);
      });
    });
    Events.listen(HoloUI.INSTANCE, PlayerQuitEvent.class, e -> {
      SessionHolder holder = holders.remove(e.getPlayer());
      if (holder == null) return;
      holder.close();
    });
    listenToInventoryPreview();
  }

  public boolean openLastSession(Player p) {
    SessionHolder holder = holders.get(p);
    if (holder == null) return false;
    return holder.onLastSession(lastId -> {
      if (lastId == null) return false;
      MenuDefinitionData menu = HoloUI.INSTANCE.getConfigManager()
          .get(lastId)
          .orElse(null);
      if (menu == null) return false;
      createNewSession(p, menu);
      return true;
    });
  }

  public void createNewSession(Player p, MenuDefinitionData menu) {
    holders.computeIfAbsent(p, SessionHolder::new).openSession(menu);
  }

  public void addPreviewSession(Player p, BlockMenuSession session) {
    holders.computeIfAbsent(p, SessionHolder::new).openPreview(session);
  }

  public boolean destroySession(Player p, boolean history) {
    SessionHolder holder = holders.get(p);
    if (holder == null) return false;
    return holder.closeSession(history);
  }

  public void destroyAll() {
    holders.forEach((player, holder) -> {
      Runnable closeTask = holder::close;
      if (!SchedulerUtils.runEntity(HoloUI.INSTANCE, player, closeTask)) {
        closeTask.run();
      }
    });
    holders.clear();
  }

  public void destroyAllType(String id, Consumer<Player> consumer) {
    holders.forEach((player, holder) -> {
      Runnable destroyTask = () -> holder.onSession(session -> {
        if (session == null || !session.getId().equalsIgnoreCase(id)) return;
        holder.closeSession(false);
        consumer.accept(player);
      });

      if (!SchedulerUtils.runEntity(HoloUI.INSTANCE, player, destroyTask)) {
        destroyTask.run();
      }
    });
  }

  public void refreshVisuals() {
    holders.forEach((player, holder) -> {
      Runnable refreshTask = holder::refreshVisuals;
      if (!SchedulerUtils.runEntity(HoloUI.INSTANCE, player, refreshTask)) {
        refreshTask.run();
      }
    });
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
          holder.onPreview(p -> {
            if (p == null) return;
            ParticleUtils.playParticle(world, p.getCenterPoint().toVector(), Color.YELLOW);
            p.getComponents().forEach(c -> ParticleUtils.playParticle(world, c.getLocation().toVector(), Color.ORANGE));
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
      Block b = getLookedAtContainer(p);
      SessionHolder holder = holders.get(p);
      if (b == null) {
        if (holder != null) {
          holder.closePreview();
        }
        return;
      }

      if (holder == null) {
        holder = holders.computeIfAbsent(p, SessionHolder::new);
      }

      SessionHolder finalHolder = holder;
      holder.onPreview(preview -> {
        if (preview == null) {
          createNewPreviewSession(b, p);
          return;
        }

        if (!preview.shouldRender(b)) {
          finalHolder.closePreview();
          createNewPreviewSession(b, p);
        }
      });
    } catch (Exception ex) {
      HoloUI.logExceptionStack(false, ex, "Failed to manage inventory preview for %s.", p.getName());
    }
  }

  private Block getLookedAtContainer(Player player) {
    Location eyeLocation = player.getEyeLocation();
    if (eyeLocation.getWorld() == null) {
      return null;
    }
    RayTraceResult rayTraceResult = eyeLocation.getWorld().rayTraceBlocks(
        eyeLocation,
        eyeLocation.getDirection(),
        HuiSettings.previewLookDistance(),
        FluidCollisionMode.NEVER,
        true
    );
    if (rayTraceResult == null) {
      return null;
    }
    Block target = rayTraceResult.getHitBlock();
    if (target == null || target.getType() == Material.AIR) {
      return null;
    }
    if (!isPreviewBlockType(target.getType())) {
      return null;
    }
    return target;
  }

  private void createNewPreviewSession(Block b, Player p) {
    Runnable createTask = () -> {
      if (b.getType() == Material.AIR || !(b.getState() instanceof Container)) {
        return;
      }
      BlockMenuSession newSession = InventoryPreviewMenu.create(b, p);
      if (newSession != null && newSession.hasPermission()) {
        Runnable openTask = () -> addPreviewSession(p, newSession);
        if (!SchedulerUtils.runEntity(HoloUI.INSTANCE, p, openTask)) {
          openTask.run();
        }
      }
    };

    if (!FoliaScheduler.runRegion(HoloUI.INSTANCE, b.getLocation(), createTask)
        && !FoliaScheduler.isFolia(HoloUI.INSTANCE.getServer())) {
      createTask.run();
    }
  }

  private boolean isPreviewBlockType(Material type) {
    if (type == null || type == Material.AIR) {
      return false;
    }
    if (type.name().endsWith("SHULKER_BOX")) {
      return true;
    }
    return switch (type) {
      case CHEST, TRAPPED_CHEST, BARREL, DISPENSER, DROPPER, HOPPER, FURNACE,
           BLAST_FURNACE, SMOKER -> true;
      default -> false;
    };
  }
}
