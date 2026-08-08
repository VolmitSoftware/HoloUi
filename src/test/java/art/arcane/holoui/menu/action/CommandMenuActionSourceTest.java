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

import art.arcane.holoui.config.MenuComponentData;
import art.arcane.holoui.config.MenuDefinitionData;
import art.arcane.holoui.config.action.CommandActionData;
import art.arcane.holoui.enums.MenuActionCommandSource;
import art.arcane.holoui.menu.MenuSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
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
    new CommandMenuAction(new CommandActionData(null, "/spawn")).execute(session(dispatched));

    assertEquals(List.of("spawn"), dispatched);
  }

  @Test
  public void anExplicitPlayerSourceRunsTheCommandAsTheClickingPlayer() {
    List<String> dispatched = new ArrayList<>();
    new CommandMenuAction(new CommandActionData(MenuActionCommandSource.PLAYER, "heal")).execute(session(dispatched));

    assertEquals(List.of("heal"), dispatched);
  }

  @Test
  public void anExplicitServerSourceKeepsConsoleDispatchAndNeverUsesThePlayer() {
    List<String> dispatched = new ArrayList<>();
    new CommandMenuAction(new CommandActionData(MenuActionCommandSource.GLOBAL, "/say hi")).execute(session(dispatched));

    assertTrue("a server source must never be routed through the player", dispatched.isEmpty());
  }

  private static MenuSession session(List<String> dispatched) {
    MenuDefinitionData data = new MenuDefinitionData(new Vector(0, 0, 0), false, false, 8.0D, false, false,
        List.<MenuComponentData>of());
    data.setId("shop");
    return new MenuSession(data, player(dispatched));
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
