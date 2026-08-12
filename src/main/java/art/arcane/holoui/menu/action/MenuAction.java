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
import art.arcane.holoui.config.action.CommandActionData;
import art.arcane.holoui.config.action.ConnectActionData;
import art.arcane.holoui.config.action.MessageActionData;
import art.arcane.holoui.config.action.MenuActionData;
import art.arcane.holoui.config.action.NavigationActionData;
import art.arcane.holoui.config.action.SoundActionData;
import art.arcane.holoui.config.action.TeleportActionData;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public abstract class MenuAction<E extends MenuActionData> {

  private static final String UNKNOWN_MENU = "unknown";
  private static final Set<CommandWarning> COMMAND_WARNINGS = ConcurrentHashMap.newKeySet();
  private static final Set<SoundWarning> SOUND_WARNINGS = ConcurrentHashMap.newKeySet();
  private static final Set<ComponentWarning> MESSAGE_WARNINGS = ConcurrentHashMap.newKeySet();
  private static final Set<ComponentWarning> TELEPORT_WARNINGS = ConcurrentHashMap.newKeySet();
  private static final Set<ComponentWarning> CONNECT_WARNINGS = ConcurrentHashMap.newKeySet();
  private static final Set<ComponentWarning> NAVIGATION_WARNINGS = ConcurrentHashMap.newKeySet();

  protected final E data;

  public MenuAction(E data) {
    this.data = data;
  }

  public static MenuAction<?> get(MenuActionData data) {
    return switch (data) {
      case CommandActionData command -> new CommandMenuAction(command);
      case SoundActionData sound -> new SoundMenuAction(sound);
      case MessageActionData message -> new MessageMenuAction(message);
      case TeleportActionData teleport -> new TeleportMenuAction(teleport);
      case ConnectActionData connect -> new ConnectMenuAction(connect);
      case NavigationActionData navigation -> new NavigateMenuAction(navigation);
      case null, default -> null;
    };
  }

  public static List<MenuAction<?>> resolve(List<MenuActionData> data, String menuId, String componentId) {
    List<MenuAction<?>> actions = new ArrayList<>(data == null ? 0 : data.size());
    if (data == null) {
      return actions;
    }

    for (MenuActionData entry : data) {
      MenuAction<?> action = entry == null ? null : get(entry);
      if (action == null) {
        HoloUI.log(Level.WARNING, "Component \"%s\" declares an unsupported action \"%s\"; skipping it.",
            componentId, entry == null ? "null" : entry.getClass().getSimpleName());
        continue;
      }

      if (action instanceof CommandMenuAction command && !command.hasCommand()) {
        String owner = menuId == null ? UNKNOWN_MENU : menuId;
        if (COMMAND_WARNINGS.add(new CommandWarning(owner, componentId))) {
          HoloUI.log(Level.WARNING, "Menu \"%s\" component \"%s\" declares an empty command; that action does nothing.",
              owner, componentId);
        }
        continue;
      }

      if (action instanceof SoundMenuAction sound && !sound.hasSound()) {
        String owner = menuId == null ? UNKNOWN_MENU : menuId;
        String soundKey = ((SoundActionData) entry).sound();
        if (SOUND_WARNINGS.add(new SoundWarning(owner, componentId, soundKey))) {
          HoloUI.log(Level.WARNING, "Menu \"%s\" component \"%s\" declares an unknown sound \"%s\"; that action does nothing.",
              owner, componentId, soundKey);
        }
        continue;
      }

      if (action instanceof MessageMenuAction message && !message.hasMessage()) {
        String owner = menuId == null ? UNKNOWN_MENU : menuId;
        if (MESSAGE_WARNINGS.add(new ComponentWarning(owner, componentId))) {
          HoloUI.log(Level.WARNING,
              "Menu \"%s\" component \"%s\" declares an empty message; that action does nothing.",
              owner, componentId);
        }
        continue;
      }

      if (action instanceof TeleportMenuAction teleport && !teleport.hasValidDestination()) {
        String owner = menuId == null ? UNKNOWN_MENU : menuId;
        if (TELEPORT_WARNINGS.add(new ComponentWarning(owner, componentId))) {
          HoloUI.log(Level.WARNING,
              "Menu \"%s\" component \"%s\" declares an invalid teleport destination; that action does nothing.",
              owner, componentId);
        }
        continue;
      }

      if (action instanceof ConnectMenuAction connect && !connect.hasValidServer()) {
        String owner = menuId == null ? UNKNOWN_MENU : menuId;
        if (CONNECT_WARNINGS.add(new ComponentWarning(owner, componentId))) {
          HoloUI.log(Level.WARNING,
              "Menu \"%s\" component \"%s\" declares an invalid proxy server name; that action does nothing.",
              owner, componentId);
        }
        continue;
      }

      if (action instanceof NavigateMenuAction navigation && !navigation.isValid()) {
        String owner = menuId == null ? UNKNOWN_MENU : menuId;
        if (NAVIGATION_WARNINGS.add(new ComponentWarning(owner, componentId))) {
          HoloUI.log(Level.WARNING, "Menu \"%s\" component \"%s\" declares navigation without a target; that action does nothing.",
              owner, componentId);
        }
        continue;
      }

      actions.add(action);
    }

    return actions;
  }

  public static ActionOutcome execute(List<MenuAction<?>> actions, ActionContext context) {
    for (MenuAction<?> action : actions) {
      if (!action.data.triggerOrDefault().matches(context.trigger())) {
        continue;
      }
      if (action.execute(context) == ActionOutcome.STOP) {
        return ActionOutcome.STOP;
      }
    }
    return ActionOutcome.CONTINUE;
  }

  public abstract ActionOutcome execute(ActionContext context);

  private record CommandWarning(String menuId, String componentId) {
  }

  private record SoundWarning(String menuId, String componentId, String sound) {
  }

  private record ComponentWarning(String menuId, String componentId) {
  }
}
