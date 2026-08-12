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
import art.arcane.holoui.config.action.ConnectActionData;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class ConnectMenuAction extends MenuAction<ConnectActionData> {
  static final String CHANNEL = "BungeeCord";

  public ConnectMenuAction(ConnectActionData data) {
    super(data);
  }

  public boolean hasValidServer() {
    return data.hasValidServer();
  }

  @Override
  public ActionOutcome execute(ActionContext context) {
    context.player().sendPluginMessage(HoloUI.INSTANCE, CHANNEL, payload(data.server()));
    return ActionOutcome.CONTINUE;
  }

  static byte[] payload(String server) {
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
         DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeUTF("Connect");
      output.writeUTF(server);
      return bytes.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException("Could not encode the BungeeCord connect payload", exception);
    }
  }
}
