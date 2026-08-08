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
package art.arcane.holoui.service;

import art.arcane.holoui.HoloCommand;
import art.arcane.volmlib.util.director.compat.DirectorEngineFactory;
import art.arcane.volmlib.util.director.help.DirectorMiniMenu;
import art.arcane.volmlib.util.director.runtime.DirectorRuntimeEngine;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A terminal cannot click or hover, so the help page a non-player sender receives must be the
 * plain console rendering rather than the MiniMessage page built for players.
 */
public class HoloUiCommandHelpDeliveryTest {

  @Test
  public void aConsoleSenderReceivesThePlainConsoleHelpPage() {
    List<String> received = new ArrayList<>();
    service().deliverHelp(console(received), rootHelpPage(), null);

    assertFalse("console received nothing", received.isEmpty());
    assertTrue(received.get(0), received.get(0).startsWith("---"));
    for (String line : received) {
      assertFalse(line, line.contains("<gradient:"));
      assertFalse(line, line.contains("<click:"));
      assertFalse(line, line.contains("<hover:"));
      assertFalse(line, line.contains("<font:"));
    }
  }

  @Test
  public void aPlayerSenderKeepsTheRichHelpPage() {
    List<String> received = new ArrayList<>();
    service().deliverHelp(player(received), rootHelpPage(), null);

    assertFalse("player received nothing", received.isEmpty());
    for (String line : received) {
      assertFalse(line, line.startsWith("---"));
    }
  }

  private static HoloUiCommandService service() {
    return new HoloUiCommandService(null);
  }

  private static DirectorMiniMenu.DirectorHelpPage rootHelpPage() {
    DirectorRuntimeEngine engine = DirectorEngineFactory.create(new HoloCommand(null));
    Optional<DirectorMiniMenu.DirectorHelpPage> page = DirectorMiniMenu.resolveHelp(engine, List.of(), 9);
    return page.orElseThrow(() -> new IllegalStateException("no root help page"));
  }

  private static CommandSender console(List<String> received) {
    return (CommandSender) Proxy.newProxyInstance(HoloUiCommandHelpDeliveryTest.class.getClassLoader(),
        new Class<?>[]{CommandSender.class}, (proxy, method, args) -> capture(proxy, method.getName(), args, received));
  }

  private static Player player(List<String> received) {
    return (Player) Proxy.newProxyInstance(HoloUiCommandHelpDeliveryTest.class.getClassLoader(),
        new Class<?>[]{Player.class}, (proxy, method, args) -> capture(proxy, method.getName(), args, received));
  }

  private static Object capture(Object proxy, String name, Object[] args, List<String> received) {
    return switch (name) {
      case "sendMessage", "sendRichMessage", "sendPlainMessage" -> {
        String message = String.valueOf(args[0]);
        if (!message.trim().isEmpty()) {
          received.add(message);
        }
        yield null;
      }
      case "hashCode" -> System.identityHashCode(proxy);
      case "equals" -> proxy == args[0];
      case "toString" -> "sender";
      default -> throw new UnsupportedOperationException(name);
    };
  }
}
