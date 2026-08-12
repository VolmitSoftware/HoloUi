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

import art.arcane.holoui.config.action.MessageActionData;
import art.arcane.holoui.util.common.TextUtils;
import art.arcane.volmlib.util.bukkit.Placeholders;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class MessageMenuAction extends MenuAction<MessageActionData> {
  public MessageMenuAction(MessageActionData data) {
    super(data);
  }

  public boolean hasMessage() {
    return data.message() != null && !data.message().isBlank();
  }

  @Override
  public ActionOutcome execute(ActionContext context) {
    Player player = context.player();
    String personalized = data.message().replace("%player%", player.getName());
    String resolved = Placeholders.setPlaceholders(player, personalized);
    player.sendMessage(sanitizeInteractions(TextUtils.parse(resolved)));
    return ActionOutcome.CONTINUE;
  }

  private static Component sanitizeInteractions(Component component) {
    List<Component> children = new ArrayList<>(component.children().size());
    for (Component child : component.children()) {
      children.add(sanitizeInteractions(child));
    }
    return component.clickEvent(null).insertion(null).children(children);
  }
}
