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
package art.arcane.holoui.menu.action;

import art.arcane.holoui.HoloUI;
import art.arcane.holoui.config.action.TeleportActionData;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class TeleportMenuAction extends MenuAction<TeleportActionData> {
  private static final Set<DestinationWarning> DESTINATION_WARNINGS = ConcurrentHashMap.newKeySet();

  public TeleportMenuAction(TeleportActionData data) {
    super(data);
  }

  public boolean hasValidDestination() {
    return data.hasValidDestination();
  }

  @Override
  public ActionOutcome execute(ActionContext context) {
    Player player = context.player();
    boolean accepted = SchedulerUtils.runEntity(HoloUI.INSTANCE, player, () -> teleport(context));
    if (!accepted) {
      HoloUI.log(Level.WARNING,
          "Menu \"%s\" component \"%s\" could not schedule its teleport for player %s.",
          context.menuId(), context.componentId(), player.getName());
    }
    return ActionOutcome.CONTINUE;
  }

  private void teleport(ActionContext context) {
    NamespacedKey worldKey = data.resolveWorldKey();
    World world = worldKey == null ? null : Bukkit.getWorld(worldKey);
    if (world == null) {
      DestinationWarning warning = new DestinationWarning(context.menuId(), context.componentId(), data.world());
      if (DESTINATION_WARNINGS.add(warning)) {
        HoloUI.log(Level.WARNING,
            "Menu \"%s\" component \"%s\" cannot teleport to unloaded world \"%s\"; that action does nothing.",
            context.menuId(), context.componentId(), data.world());
      }
      return;
    }

    Player player = context.player();
    Location destination = new Location(world, data.x(), data.y(), data.z(), data.yaw(), data.pitch());
    CompletableFuture<Boolean> result = player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
    result.whenComplete((success, failure) -> reportResult(context, player, success, failure));
  }

  private void reportResult(ActionContext context, Player player, Boolean success, Throwable failure) {
    if (failure != null) {
      HoloUI.logExceptionStack(false, failure,
          "Menu \"%s\" component \"%s\" failed to teleport player %s to world %s.",
          context.menuId(), context.componentId(), player.getName(), data.world());
      return;
    }
    if (!Boolean.TRUE.equals(success)) {
      HoloUI.log(Level.WARNING,
          "Menu \"%s\" component \"%s\" could not teleport player %s to world %s.",
          context.menuId(), context.componentId(), player.getName(), data.world());
    }
  }

  private record DestinationWarning(String menuId, String componentId, String worldKey) {
  }
}
