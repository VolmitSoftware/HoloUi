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

import art.arcane.holoui.api.HoloClickTrigger;
import art.arcane.holoui.config.action.CommandActionData;
import art.arcane.holoui.enums.MenuActionCommandSource;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CommandMenuActionSourceTest {

  @Test
  public void anOmittedSourceRunsTheCommandAsTheClickingPlayer() {
    List<String> dispatched = new ArrayList<>();
    new CommandMenuAction(new CommandActionData(null, "/spawn", null)).execute(context(dispatched));

    assertEquals(List.of("spawn"), dispatched);
  }

  @Test
  public void anExplicitPlayerSourceRunsTheCommandAsTheClickingPlayer() {
    List<String> dispatched = new ArrayList<>();
    new CommandMenuAction(new CommandActionData(MenuActionCommandSource.PLAYER, "heal", null)).execute(context(dispatched));

    assertEquals(List.of("heal"), dispatched);
  }

  @Test
  public void anExplicitServerSourceKeepsConsoleDispatchAndNeverUsesThePlayer() {
    List<String> dispatched = new ArrayList<>();
    new CommandMenuAction(new CommandActionData(MenuActionCommandSource.GLOBAL, "/say hi", null)).execute(context(dispatched));

    assertTrue("a server source must never be routed through the player", dispatched.isEmpty());
  }

  private static ActionContext context(List<String> dispatched) {
    Player player = player(dispatched);
    return new ActionContext() {
      @Override
      public Player player() {
        return player;
      }

      @Override
      public String menuId() {
        return "shop";
      }

      @Override
      public String componentId() {
        return "buy";
      }

      @Override
      public HoloClickTrigger trigger() {
        return HoloClickTrigger.LEFT_CLICK;
      }

      @Override
      public NavigationResult navigate(NavigationRequest request) {
        return NavigationResult.DENIED;
      }
    };
  }

  private static Player player(List<String> dispatched) {
    return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
        (proxy, method, args) -> switch (method.getName()) {
          case "performCommand" -> dispatched.add((String) args[0]);
          case "getUniqueId" -> UUID.fromString("00000000-0000-0000-0000-0000000000d1");
          case "getName" -> "tester";
          case "getLocation" -> new Location(null, 0, 64, 0);
          case "getEyeLocation" -> new Location(null, 0, 65.62D, 0);
          case "hashCode" -> System.identityHashCode(proxy);
          case "equals" -> proxy == args[0];
          case "toString" -> "Player[tester]";
          default -> throw new UnsupportedOperationException("the command action touched " + method.getName());
        });
  }
}
