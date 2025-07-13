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
package com.volmit.holoui.menu;

import com.volmit.holoui.HoloUI;
import com.volmit.holoui.config.HuiSettings;
import com.volmit.holoui.config.MenuDefinitionData;
import com.volmit.holoui.menu.components.ClickableComponent;
import com.volmit.holoui.menu.special.BlockMenuSession;
import com.volmit.holoui.menu.special.inventories.InventoryPreviewMenu;
import com.volmit.holoui.utils.Events;
import com.volmit.holoui.utils.ParticleUtils;
import com.volmit.holoui.utils.SchedulerUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class MenuSessionManager {

    private final Map<Player, SessionHolder> holders = new ConcurrentHashMap<>();

    private BukkitTask debugHitbox, debugPos;

    public MenuSessionManager() {
        controlHitboxDebug(HuiSettings.DEBUG_HITBOX.value());
        controlPositionDebug(HuiSettings.DEBUG_SPACING.value());
        SchedulerUtils.scheduleSyncTask(HoloUI.INSTANCE, 1L, () -> holders.values().removeIf(SessionHolder::tick), false);
        Events.listen(PlayerMoveEvent.class, EventPriority.HIGHEST, e -> {
            if (e.isCancelled() || e.getTo() == null) return;
            SessionHolder holder = holders.get(e.getPlayer());
            if (holder == null) return;
            synchronized (holder) {
                MenuSession s = holder.session;
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
            }
        });
        Events.listen(PlayerDeathEvent.class, EventPriority.MONITOR, e -> {
            SessionHolder holder = holders.get(e.getEntity());
            if (holder == null) return;
            synchronized (holder) {
                MenuSession s = holder.session;
                if (s == null || !s.isCloseOnDeath()) return;
                holder.closeSession(false);
            }
        });
        Events.listen(PlayerRespawnEvent.class, EventPriority.MONITOR, e -> {
            SessionHolder holder = holders.get(e.getPlayer());
            if (holder == null) return;
            synchronized (holder) {
                MenuSession s = holder.session;
                if (s == null || !s.isValid(e.getRespawnLocation())) holder.closeSession(false);
                else s.move(e.getRespawnLocation().clone(), true);
            }
        });
        Events.listen(PlayerTeleportEvent.class, EventPriority.MONITOR, e -> {
            SessionHolder holder = holders.get(e.getPlayer());
            if (holder == null || e.getTo() == null) return;
            synchronized (holder) {
                MenuSession s = holder.session;
                if (s == null || !s.isValid(e.getTo()) || s.isCloseOnTeleport()) holder.closeSession(false);
                else s.move(e.getTo().clone(), true);
            }
        });
        Events.listen(PlayerQuitEvent.class, e -> {
            SessionHolder holder = holders.get(e.getPlayer());
            if (holder == null) return;
            holder.close();
        });
        listenToInventoryPreview();
    }

    public boolean openLastSession(Player p) {
        SessionHolder holder = holders.get(p);
        if (holder == null) return false;
        synchronized (holder) {
            String lastId = holder.lastSession;
            if (lastId == null) return false;
            MenuDefinitionData menu = HoloUI.INSTANCE.getConfigManager()
                    .get(lastId)
                    .orElse(null);
            if (menu == null) return false;
            createNewSession(p, menu);
            return true;
        }
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
        holders.forEach((k, v) -> v.close());
        holders.clear();
    }

    public void destroyAllType(String id, Consumer<Player> consumer) {
        holders.forEach((player, holder) -> {
            synchronized (holder) {
                if (!holder.session.getId().equalsIgnoreCase(id))
                    return;
                holder.closeSession(false);
                consumer.accept(player);
            }
        });
    }

    public void controlHitboxDebug(boolean hitbox) {
        if (hitbox && (debugHitbox == null || debugHitbox.isCancelled())) {
            debugHitbox = SchedulerUtils.scheduleSyncTask(HoloUI.INSTANCE, 2L, () -> holders.forEach((player, holder) -> {
                synchronized (holder) {
                    MenuSession session = holder.session;
                    if (session == null) return;
                    session.getComponents().forEach(c -> {
                        if (c instanceof ClickableComponent<?> o)
                            o.highlightHitbox(player.getWorld());
                    });
                }
            }), false);
        } else if (!hitbox && (debugHitbox != null && !debugHitbox.isCancelled()))
            debugHitbox.cancel();
    }

    //TODO Fix anchor particle
    public void controlPositionDebug(boolean positionDebug) {
        if (positionDebug && (debugPos == null || debugPos.isCancelled())) {
            debugPos = SchedulerUtils.scheduleSyncTask(HoloUI.INSTANCE, 2L, () -> {
                holders.forEach((player, holder) -> {
                    synchronized (holder) {
                        World world = player.getWorld();
                        MenuSession s = holder.session;
                        if (s != null) {
                            ParticleUtils.playParticle(world, s.getCenterInitialYAdjusted().toVector(), Color.YELLOW);
                            s.getComponents().forEach(c -> ParticleUtils.playParticle(world, c.getLocation().toVector(), Color.ORANGE));
                        }

                        BlockMenuSession p = holder.preview;
                        if (p != null) {
                            ParticleUtils.playParticle(world, p.getCenterPoint().toVector(), Color.YELLOW);
                            p.getComponents().forEach(c -> ParticleUtils.playParticle(world, c.getLocation().toVector(), Color.ORANGE));
                        }
                    }
                });
            }, false);
        } else if (!positionDebug && (debugPos != null && !debugPos.isCancelled()))
            debugPos.cancel();
    }

    private void listenToInventoryPreview() {
        Events.listen(PlayerToggleSneakEvent.class, EventPriority.MONITOR, e -> managePreviewEvents(e.getPlayer()));
        Events.listen(PlayerMoveEvent.class, EventPriority.MONITOR, e -> managePreviewEvents(e.getPlayer()));
    }

    private void managePreviewEvents(Player p) {
        if (!p.isSneaking()) return;
        try {
            Block b = p.getTargetBlock(null, 10);
            SessionHolder holder = holders.computeIfAbsent(p, SessionHolder::new);
            if (holder.preview == null) {
                createNewPreviewSession(b, p);
            }
        } catch (IllegalStateException ignored) {}
    }

    private void createNewPreviewSession(Block b, Player p) {
        if (b.getType() != Material.AIR && b.getState() instanceof Container) {
            BlockMenuSession newSession = InventoryPreviewMenu.create(b, p);
            if (newSession != null && newSession.shouldRender(b) && newSession.hasPermission())
                addPreviewSession(p, newSession);
        }
    }
}
