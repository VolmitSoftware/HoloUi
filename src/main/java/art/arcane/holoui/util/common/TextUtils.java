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
package art.arcane.holoui.util.common;

import art.arcane.holoui.HoloUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class TextUtils {
    private static final Map<String, String> REPLACEMENTS = replacements();

    public static Component parse(String text) {
        text = ChatColor.translateAlternateColorCodes('&', text);
        for (var entry : REPLACEMENTS.entrySet()) {
            text = text.replace(entry.getKey(), entry.getValue());
        }
        return MiniMessage.miniMessage().deserialize(text);
    }

    public static Component textColor(String text, String hexColor) {
        return Component.text(text).color(TextColor.fromHexString(hexColor));
    }

    public static Component textColor(String text, int hexColor) {
        return Component.text(text).color(TextColor.color(hexColor));
    }

    public static String content(Component component) {
        StringBuilder builder = new StringBuilder();
        if (component instanceof TextComponent text) {
            builder.append(text.content());
        }

        for (Component child : component.children()) {
            builder.append(content(child));
        }
        return builder.toString();
    }

    public static void splash(HoloUI plugin) {
        ChatColor dark = ChatColor.DARK_GRAY;
        ChatColor accent = ChatColor.AQUA;
        ChatColor meta = ChatColor.GRAY;
        String version = plugin.getDescription().getVersion();
        int javaVersion = getJavaVersion();

        String splash =
                "\n"
                        + dark + "██" + accent + "╗  " + dark + "██" + accent + "╗ " + dark + "██████" + accent + "╗ " + dark + "██" + accent + "╗      " + dark + "██████" + accent + "╗ " + dark + "██" + accent + "╗   " + dark + "██" + accent + "╗" + dark + "██" + accent + "╗\n"
                        + dark + "██" + accent + "║  " + dark + "██" + accent + "║" + dark + "██" + accent + "╔═══" + dark + "██" + accent + "╗" + dark + "██" + accent + "║     " + dark + "██" + accent + "╔═══" + dark + "██" + accent + "╗" + dark + "██" + accent + "║   " + dark + "██" + accent + "║" + dark + "██" + accent + "║" + meta + "   HoloUI, Holographic Interface Runtime\n"
                        + dark + "███████" + accent + "║" + dark + "██" + accent + "║   " + dark + "██" + accent + "║" + dark + "██" + accent + "║     " + dark + "██" + accent + "║   " + dark + "██" + accent + "║" + dark + "██" + accent + "║   " + dark + "██" + accent + "║" + dark + "██" + accent + "║" + meta + "   Version: " + accent + version + "\n"
                        + dark + "██" + accent + "╔══" + dark + "██" + accent + "║" + dark + "██" + accent + "║   " + dark + "██" + accent + "║" + dark + "██" + accent + "║     " + dark + "██" + accent + "║   " + dark + "██" + accent + "║" + dark + "██" + accent + "║   " + dark + "██" + accent + "║" + dark + "██" + accent + "║" + meta + "   By: " + rainbowStudioName() + "\n"
                        + dark + "██" + accent + "║  " + dark + "██" + accent + "║╚" + dark + "██████" + accent + "╔╝" + dark + "███████" + accent + "╗╚" + dark + "██████" + accent + "╔╝╚" + dark + "██████" + accent + "╔╝" + dark + "██" + accent + "║" + meta + "   Java Version: " + accent + javaVersion + "\n"
                        + accent + "╚═╝  ╚═╝ ╚═════╝ ╚══════╝ ╚═════╝  ╚═════╝ ╚═╝\n";

        Bukkit.getConsoleSender().sendMessage(splash);
    }

    private static int getJavaVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            version = version.substring(2, 3);
        } else {
            int dot = version.indexOf('.');
            if (dot != -1) {
                version = version.substring(0, dot);
            }
        }
        return Integer.parseInt(version);
    }

    private static String rainbowStudioName() {
        return ChatColor.RED + "A"
                + ChatColor.GOLD + "r"
                + ChatColor.YELLOW + "c"
                + ChatColor.GREEN + "a"
                + ChatColor.DARK_GRAY + "n"
                + ChatColor.AQUA + "e "
                + ChatColor.AQUA + "A"
                + ChatColor.BLUE + "r"
                + ChatColor.DARK_BLUE + "t"
                + ChatColor.DARK_PURPLE + "s"
                + ChatColor.DARK_AQUA + " (Volmit Software)";
    }

    private static Map<String, String> replacements() {
        Map<String, String> replacements = new HashMap<>();
        for (ChatColor color : ChatColor.values()) {
            replacements.put(color.toString(), "<" + color.asBungee().getName() + ">");
        }
        return Collections.unmodifiableMap(replacements);
    }
}
